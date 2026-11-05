/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Simulator, type SimulatorOptions } from '../src/simulator.js';
import { StateStore, INITIAL_HEX } from '../src/store.js';
import { buildServer } from '../src/server.js';

const pump = { uuid: '11111111-1111-4111-8111-111111111111', name: 'Demo Toshiba', type: 'HEAT_PUMP', relayCount: 4 };
const relay = { uuid: '22222222-2222-4222-8222-222222222222', name: 'Demo relay', type: 'STANDARD', relayCount: 2 };
const options: SimulatorOptions = {
  backendBaseUrl: 'http://backend:8080', token: 'test-secret', commandDelayMs: 2000,
  discoveryTtlMs: 180_000, fallbackOn: false, outdoorTemperature: 5, requestTimeoutMs: 1000,
};
const logger = { warn() {} };
const command = '30431A41313200101505FE0B00001002010000';

test('commands are accepted before application, applied after delay, and persist across restart', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'device-simulator-'));
  const path = join(directory, 'state.db');
  let store = new StateStore(path);
  let now = 1_000_000;
  const request = (async () => Response.json([pump])) as typeof fetch;
  try {
    let simulator = new Simulator(store, options, logger, request, () => now);
    await simulator.discover();
    simulator.acceptCommand(pump.uuid, command);
    assert.equal(simulator.pumpState(pump.uuid).stateHex.slice(0, 6), INITIAL_HEX.slice(0, 6));
    now += 1000;
    simulator.acceptCommand(pump.uuid, command); // duplicate leaves original application time intact
    store.close();
    store = new StateStore(path);
    simulator = new Simulator(store, options, logger, request, () => now);
    await simulator.discover();
    now += 1000;
    assert.equal(simulator.pumpState(pump.uuid).stateHex.slice(0, 6), command.slice(0, 6));
    now += 3_600_000;
    const bytes = Buffer.from(simulator.pumpState(pump.uuid).stateHex, 'hex');
    assert.ok(bytes[8] > 21 && bytes[8] < 26, 'room warms gradually towards target');
  } finally { store.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('relay polling uses real control endpoint and falls back on errors and empty maps', async () => {
  const store = new StateStore(':memory:');
  let mode = 'success';
  const seen: string[] = [];
  const request = (async (url: string | URL | Request, init?: RequestInit) => {
    const path = String(url);
    seen.push(path);
    if (path.endsWith('/devices')) {
      assert.equal((init?.headers as Record<string, string>).Authorization, 'Bearer test-secret');
      return Response.json([relay]);
    }
    assert.equal(init?.headers, undefined, 'service token is only sent to discovery');
    return mode === 'failure' ? new Response('', { status: 503 })
      : Response.json(mode === 'empty' ? {} : { 0: 1, 1: 0 });
  }) as typeof fetch;
  const simulator = new Simulator(store, options, logger, request);
  try {
    await simulator.discover();
    await simulator.pollRelays();
    assert.deepEqual(store.relay(relay.uuid).channels, { 0: 1, 1: 0 });
    assert.ok(seen.includes(`http://backend:8080/control/${relay.uuid}`));
    mode = 'failure';
    await simulator.pollRelays();
    assert.deepEqual(store.relay(relay.uuid).channels, { 0: 0, 1: 0 });
    assert.equal(store.relay(relay.uuid).successful, false);
    mode = 'empty';
    await simulator.pollRelays();
    assert.equal(store.relay(relay.uuid).successful, false);
  } finally { store.close(); }
});

test('private API authenticates, validates commands, and refuses unknown or expired devices', async () => {
  const store = new StateStore(':memory:');
  let now = 1_000_000;
  let discoveryWorks = true;
  const request = (async () => discoveryWorks ? Response.json([pump]) : new Response('', { status: 503 })) as typeof fetch;
  const simulator = new Simulator(store, options, logger, request, () => now);
  const app = buildServer(simulator, options.token, false);
  const headers = { authorization: 'Bearer test-secret' };
  try {
    assert.equal((await app.inject('/health')).statusCode, 200);
    assert.equal((await app.inject('/ready')).statusCode, 503);
    assert.equal((await app.inject('/devices')).statusCode, 401);
    await simulator.discover();
    assert.equal((await app.inject('/ready')).statusCode, 200);
    assert.equal((await app.inject({ url: `/heat-pumps/${relay.uuid}/state`, headers })).statusCode, 404);
    assert.equal((await app.inject({ method: 'POST', url: `/heat-pumps/${pump.uuid}/commands`, headers,
      payload: { stateHex: 'bad' } })).statusCode, 400);
    assert.equal((await app.inject({ method: 'POST', url: `/heat-pumps/${pump.uuid}/commands`, headers,
      payload: { stateHex: command } })).statusCode, 202);
    const initial = await app.inject({ url: `/heat-pumps/${pump.uuid}/state`, headers });
    assert.equal(initial.json().stateHex.slice(0, 6), INITIAL_HEX.slice(0, 6));
    now += 2000;
    const applied = await app.inject({ url: `/heat-pumps/${pump.uuid}/state`, headers });
    assert.equal(applied.json().stateHex.slice(0, 6), command.slice(0, 6));
    discoveryWorks = false;
    await simulator.discover();
    now += options.discoveryTtlMs;
    assert.equal((await app.inject({ url: `/heat-pumps/${pump.uuid}/state`, headers })).statusCode, 503);
    assert.deepEqual(simulator.list(), []);
  } finally { await app.close(); store.close(); }
});

test('successful inventory removal revokes access immediately', async () => {
  const store = new StateStore(':memory:');
  let inventory = [pump];
  const simulator = new Simulator(store, options, logger, (async () => Response.json(inventory)) as typeof fetch);
  try {
    await simulator.discover();
    assert.equal(simulator.hasPump(pump.uuid), true);
    inventory = [];
    await simulator.discover();
    assert.equal(simulator.hasPump(pump.uuid), false);
    assert.throws(() => store.pump(pump.uuid));
  } finally { store.close(); }
});

test('negative outdoor temperatures follow the existing Toshiba decoder encoding', async () => {
  for (const temperature of [-12, -1]) {
    const store = new StateStore(':memory:');
    const simulator = new Simulator(store, { ...options, outdoorTemperature: temperature }, logger,
      (async () => Response.json([pump])) as typeof fetch);
    try {
      await simulator.discover();
      const bytes = Buffer.from(simulator.pumpState(pump.uuid).stateHex, 'hex');
      assert.equal(bytes[9], temperature === -1 ? 0x7e : 0xf4);
    } finally { store.close(); }
  }
});

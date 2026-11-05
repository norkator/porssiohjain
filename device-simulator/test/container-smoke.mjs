/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';

const token = 'container-smoke-test-private-service-token-123456789';
const pump = { uuid: '11111111-1111-4111-8111-111111111111', name: 'Pump', type: 'HEAT_PUMP', relayCount: 4 };
const relay = { uuid: '22222222-2222-4222-8222-222222222222', name: 'Relay', type: 'STANDARD', relayCount: 4 };
let polls = 0;
const backend = createServer((req, res) => {
  res.setHeader('content-type', 'application/json');
  if (req.url === '/internal/device-simulator/devices') {
    assert.equal(req.headers.authorization, `Bearer ${token}`);
    res.end(JSON.stringify([pump, relay]));
  } else if (req.url === `/control/${relay.uuid}`) {
    assert.equal(req.headers.authorization, undefined);
    polls++;
    res.end(JSON.stringify({ 0: 1, 1: 0 }));
  } else { res.writeHead(404); res.end('{}'); }
});
backend.listen(8099, '127.0.0.1');
await once(backend, 'listening');
let child;
let logs = '';
function start() {
  child = spawn(process.execPath, ['/app/dist/src/main.js'], { env: {
    ...process.env, BACKEND_BASE_URL: 'http://127.0.0.1:8099', DEVICE_SIMULATOR_TOKEN: token,
    COMMAND_DELAY_MS: '500', PORT: '8080', STATE_DB_PATH: '/data/smoke.db',
  }, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', chunk => { logs += chunk; });
  child.stderr.on('data', chunk => { logs += chunk; });
}
async function waitReady() {
  for (let attempt = 0; attempt < 100; attempt++) {
    try { if ((await fetch('http://127.0.0.1:8080/ready')).ok) return; } catch {}
    if (child.exitCode !== null) throw new Error(`Simulator exited: ${logs}`);
    await delay(100);
  }
  throw new Error(`Simulator readiness timeout: ${logs}`);
}
async function stop() {
  if (child && child.exitCode === null) {
    const exited = once(child, 'exit');
    child.kill('SIGTERM');
    const [code] = await exited;
    assert.equal(code, 0);
  }
}
const headers = { authorization: `Bearer ${token}`, 'content-type': 'application/json' };
try {
  start();
  await waitReady();
  assert.equal((await fetch('http://127.0.0.1:8080/health')).status, 200);
  assert.equal((await fetch('http://127.0.0.1:8080/devices')).status, 401);
  const commandUrl = `http://127.0.0.1:8080/heat-pumps/${pump.uuid}/commands`;
  const stateUrl = `http://127.0.0.1:8080/heat-pumps/${pump.uuid}/state`;
  const command = await fetch(commandUrl, { method: 'POST', headers,
    body: JSON.stringify({ stateHex: '30431A41313200101505FE0B00001002010000' }) });
  assert.equal(command.status, 202);
  const initial = await (await fetch(stateUrl, { headers })).json();
  assert.equal(initial.stateHex.slice(0, 2), '31');
  await delay(650);
  const applied = await (await fetch(stateUrl, { headers })).json();
  assert.equal(applied.stateHex.slice(0, 6), '30431A');
  assert.ok(polls >= 1);
  const devices = await (await fetch('http://127.0.0.1:8080/devices', { headers })).json();
  assert.equal(devices.find(device => device.uuid === relay.uuid).relayState.channels['0'], 1);
  await stop();
  start();
  await waitReady();
  const restored = await (await fetch(stateUrl, { headers })).json();
  assert.equal(restored.stateHex.slice(0, 6), '30431A');
  console.log('Container smoke passed: health, discovery, authentication, relay polling, command/read-back delay, restart persistence, graceful shutdown.');
} finally {
  await stop();
  backend.close();
}

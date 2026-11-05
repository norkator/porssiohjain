/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
import Fastify from 'fastify';
import { timingSafeEqual } from 'node:crypto';
import type { Simulator } from './simulator.js';

export function buildServer(simulator: Simulator, token: string, logging = true) {
  const app = Fastify({ logger: logging ? { redact: ['req.headers.authorization'] } : false, bodyLimit: 4096 });
  app.get('/health', async () => ({ status: 'ok' }));
  app.get('/ready', async (_request, reply) => {
    if (!simulator.ready()) return reply.code(503).send({ status: 'waiting-for-backend' });
    return { status: 'ready' };
  });

  app.register(async privateApi => {
    privateApi.addHook('onRequest', async (request, reply) => {
      const actual = Buffer.from(request.headers.authorization ?? '');
      const expected = Buffer.from(`Bearer ${token}`);
      if (!token || actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
        return reply.code(401).send({ error: 'Unauthorized' });
      }
    });
    privateApi.get('/devices', async () => simulator.list().map(device => ({ ...device, simulated: true,
      ...(device.type === 'STANDARD' ? { relayState: simulator.store.relay(device.uuid) } : {}) })));

    const params = { type: 'object', required: ['uuid'], properties: {
      uuid: { type: 'string', pattern: '^[0-9a-fA-F-]{36}$' },
    } };
    privateApi.get<{ Params: { uuid: string } }>('/heat-pumps/:uuid/state', { schema: { params } },
      async (request, reply) => {
        if (!simulator.ready()) return reply.code(503).send({ error: 'Inventory unavailable' });
        if (!simulator.hasPump(request.params.uuid)) return reply.code(404).send({ error: 'Heat pump not found' });
        return simulator.pumpState(request.params.uuid);
      });
    privateApi.post<{ Params: { uuid: string }; Body: { stateHex: string } }>('/heat-pumps/:uuid/commands', {
      schema: { params, body: { type: 'object', additionalProperties: false, required: ['stateHex'],
        properties: { stateHex: { type: 'string', pattern: '^[0-9a-fA-F]{38}$' } } } },
    }, async (request, reply) => {
      if (!simulator.ready()) return reply.code(503).send({ error: 'Inventory unavailable' });
      if (!simulator.hasPump(request.params.uuid)) return reply.code(404).send({ error: 'Heat pump not found' });
      simulator.acceptCommand(request.params.uuid, request.body.stateHex.toUpperCase());
      return reply.code(202).send({ accepted: true, simulated: true });
    });
  });
  return app;
}

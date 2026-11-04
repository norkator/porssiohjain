/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
import { StateStore } from './store.js';
import { Simulator } from './simulator.js';
import { buildServer } from './server.js';

function numeric(name: string, fallback: number, min: number, max: number): number {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isFinite(value) || value < min || value > max) throw new Error(`Invalid ${name}`);
  return value;
}

const token = process.env.DEVICE_SIMULATOR_TOKEN;
const backend = process.env.BACKEND_BASE_URL;
if (!token || token.trim().length < 32) throw new Error('DEVICE_SIMULATOR_TOKEN must contain at least 32 characters');
if (!backend || !['http:', 'https:'].includes(new URL(backend).protocol)) throw new Error('BACKEND_BASE_URL must be HTTP(S)');
const discoveryInterval = numeric('DISCOVERY_INTERVAL_MS', 60_000, 1000, 3_600_000);
const relayInterval = numeric('RELAY_POLL_INTERVAL_MS', 300_000, 1000, 3_600_000);
const discoveryTtl = numeric('DISCOVERY_TTL_MS', 180_000, discoveryInterval * 2, 86_400_000);
if (!['ON', 'OFF'].includes(process.env.RELAY_DEFAULT_STATE ?? 'OFF')) throw new Error('Invalid RELAY_DEFAULT_STATE');
const store = new StateStore(process.env.STATE_DB_PATH ?? './data/simulator.db');
const simulator = new Simulator(store, {
  token, backendBaseUrl: backend.replace(/\/+$/, ''), discoveryTtlMs: discoveryTtl,
  commandDelayMs: numeric('COMMAND_DELAY_MS', 2000, 0, 60_000),
  requestTimeoutMs: numeric('REQUEST_TIMEOUT_MS', 10_000, 100, 60_000),
  fallbackOn: process.env.RELAY_DEFAULT_STATE === 'ON',
  outdoorTemperature: numeric('OUTDOOR_TEMPERATURE', 5, -40, 45),
}, { warn: (object, message) => app.log.warn(object, message) });
const app = buildServer(simulator, token);

await app.listen({ host: '0.0.0.0', port: numeric('PORT', 8080, 1, 65535) });
let stopped = false;
// Completion-based loops prevent overlapping requests during backend outages.
const timers = new Set<ReturnType<typeof setTimeout>>();
async function repeat(work: () => Promise<void>, interval: number): Promise<void> {
  if (stopped) return;
  try { await work(); } catch { app.log.error('Simulator background operation failed'); }
  if (stopped) return;
  const timer = setTimeout(() => { timers.delete(timer); void repeat(work, interval); }, interval);
  timers.add(timer);
}
await simulator.discover();
void repeat(() => simulator.discover(), discoveryInterval);
void repeat(() => simulator.pollRelays(), relayInterval);
const tick = setInterval(() => simulator.tick(), 1000);

for (const signal of ['SIGINT', 'SIGTERM'] as const) process.once(signal, async () => {
  stopped = true;
  for (const timer of timers) clearTimeout(timer);
  clearInterval(tick);
  await app.close();
  // Outstanding HTTP calls have a bounded timeout; process termination closes SQLite safely.
  process.exit(0);
});

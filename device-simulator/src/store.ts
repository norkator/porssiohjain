/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import type { PumpState, RelayState, SimulatedDevice } from './types.js';

export const INITIAL_HEX = '31431641313200101505FE0B00001002010000';

export class StateStore {
  private readonly db: DatabaseSync;

  constructor(path: string) {
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec(`PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS device_state (
        uuid TEXT PRIMARY KEY, type TEXT NOT NULL, state TEXT NOT NULL
      );`);
  }

  register(device: SimulatedDevice, now: number): void {
    const state: PumpState | RelayState = device.type === 'HEAT_PUMP'
      ? { stateHex: INITIAL_HEX, pendingHex: null, applyAt: null, roomTemperature: 21, updatedAt: now }
      : { channels: Object.fromEntries(Array.from({ length: device.relayCount }, (_, i) => [i, 0])),
        observedAt: new Date(now).toISOString(), successful: false };
    this.db.prepare('INSERT INTO device_state(uuid, type, state) VALUES (?, ?, ?) ON CONFLICT(uuid) DO NOTHING')
      .run(device.uuid, device.type, JSON.stringify(state));
    // A deliberately reconfigured device starts with the new device type's defaults.
    this.db.prepare('UPDATE device_state SET type = ?, state = ? WHERE uuid = ? AND type <> ?')
      .run(device.type, JSON.stringify(state), device.uuid, device.type);
  }

  pump(uuid: string): PumpState {
    return this.read<PumpState>(uuid, 'HEAT_PUMP');
  }

  relay(uuid: string): RelayState {
    return this.read<RelayState>(uuid, 'STANDARD');
  }

  private read<T>(uuid: string, type: string): T {
    const row = this.db.prepare('SELECT state FROM device_state WHERE uuid = ? AND type = ?').get(uuid, type);
    if (!row) throw new Error('Device state not found');
    return JSON.parse(String(row.state)) as T;
  }

  save(uuid: string, state: PumpState | RelayState): void {
    this.db.prepare('UPDATE device_state SET state = ? WHERE uuid = ?').run(JSON.stringify(state), uuid);
  }

  removeInactive(activeUuids: string[]): void {
    const active = new Set(activeUuids);
    for (const row of this.db.prepare('SELECT uuid FROM device_state').all()) {
      if (!active.has(String(row.uuid))) this.db.prepare('DELETE FROM device_state WHERE uuid = ?').run(row.uuid);
    }
  }

  close(): void { this.db.close(); }
}

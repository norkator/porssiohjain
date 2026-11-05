/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
import { StateStore } from './store.js';
import { isDeviceList, type PumpState, type SimulatedDevice } from './types.js';

interface Logger { warn(object: object, message: string): void }
export interface SimulatorOptions {
  backendBaseUrl: string;
  token: string;
  commandDelayMs: number;
  discoveryTtlMs: number;
  fallbackOn: boolean;
  outdoorTemperature: number;
  requestTimeoutMs: number;
}

/** Owns virtual hardware state. Rules and scheduling decisions always come from the backend. */
export class Simulator {
  private devices = new Map<string, SimulatedDevice>();
  private lastDiscovery: number | null = null;

  constructor(readonly store: StateStore, readonly options: SimulatorOptions,
              private readonly logger: Logger, private readonly request: typeof fetch = fetch,
              private readonly now: () => number = Date.now) {}

  async discover(): Promise<void> {
    try {
      const response = await this.request(this.options.backendBaseUrl + '/internal/device-simulator/devices', {
        headers: { Authorization: `Bearer ${this.options.token}` },
        signal: AbortSignal.timeout(this.options.requestTimeoutMs), redirect: 'error',
      });
      if (!response.ok) throw new Error(`Discovery HTTP ${response.status}`);
      const devices: unknown = await response.json();
      if (!isDeviceList(devices)) throw new Error('Invalid device discovery response');
      this.devices = new Map(devices.map(device => [device.uuid, device]));
      const now = this.now();
      for (const device of devices) this.store.register(device, now);
      this.store.removeInactive(devices.map(device => device.uuid));
      this.lastDiscovery = now;
    } catch {
      this.logger.warn({}, 'Device discovery failed; inventory expires after the discovery timeout');
    }
  }

  ready(): boolean {
    return this.lastDiscovery !== null && this.now() - this.lastDiscovery < this.options.discoveryTtlMs;
  }

  list(): SimulatedDevice[] { return this.ready() ? [...this.devices.values()] : []; }

  hasPump(uuid: string): boolean { return this.ready() && this.devices.get(uuid)?.type === 'HEAT_PUMP'; }

  acceptCommand(uuid: string, stateHex: string): void {
    const state = this.store.pump(uuid);
    // Repeating the same pending request must not postpone its application indefinitely.
    if (state.pendingHex === stateHex || (state.pendingHex === null && state.stateHex === stateHex)) return;
    state.pendingHex = stateHex;
    state.applyAt = this.now() + this.options.commandDelayMs;
    this.store.save(uuid, state);
  }

  pumpState(uuid: string): { stateHex: string; observedAt: string; simulated: true } {
    this.advancePump(uuid);
    return { stateHex: this.store.pump(uuid).stateHex, observedAt: new Date(this.now()).toISOString(), simulated: true };
  }

  tick(): void {
    for (const device of this.list()) {
      if (device.type === 'HEAT_PUMP') this.advancePump(device.uuid);
    }
  }

  private advancePump(uuid: string): void {
    const state = this.store.pump(uuid);
    const now = this.now();
    // Evolve the previous state up to the command's application time, then evolve the new state.
    if (state.pendingHex && state.applyAt !== null && state.applyAt <= now) {
      this.evolve(state, Math.max(state.updatedAt, state.applyAt));
      state.stateHex = state.pendingHex;
      state.pendingHex = null;
      state.applyAt = null;
    }
    this.evolve(state, now);
    this.store.save(uuid, state);
  }

  private evolve(state: PumpState, now: number): void {
    const bytes = Buffer.from(state.stateHex, 'hex');
    const on = bytes[0] === 0x30;
    const target = bytes[2];
    const mode = bytes[1];
    // Advisory demo physics: relax towards a target over hours, with passive outdoor heat loss.
    const active = on && (mode === 0x41 || mode === 0x42 || mode === 0x43);
    const equilibrium = active ? Math.max(16, Math.min(30, target)) : this.options.outdoorTemperature;
    const elapsedHours = Math.max(0, now - state.updatedAt) / 3_600_000;
    const factor = 1 - Math.exp(-elapsedHours / (active ? 1.5 : 12));
    state.roomTemperature += (equilibrium - state.roomTemperature) * factor;
    bytes[8] = encodeTemperature(state.roomTemperature);
    bytes[9] = encodeTemperature(this.options.outdoorTemperature);
    state.stateHex = bytes.toString('hex').toUpperCase();
    state.updatedAt = now;
  }

  async pollRelays(): Promise<void> {
    for (const device of this.list()) {
      if (device.type !== 'STANDARD') continue;
      await this.pollRelay(device);
    }
  }

  private async pollRelay(device: SimulatedDevice): Promise<void> {
    let channels: Record<string, 0 | 1> = {};
    let successful = false;
    try {
      const response = await this.request(`${this.options.backendBaseUrl}/control/${device.uuid}`, {
        signal: AbortSignal.timeout(this.options.requestTimeoutMs), redirect: 'error',
      });
      if (!response.ok) throw new Error(`Control HTTP ${response.status}`);
      const body: unknown = await response.json();
      if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).length === 0)
        throw new Error('Empty or invalid control map');
      const entries = Object.entries(body);
      if (entries.some(([channel, value]) => !/^\d{1,2}$/.test(channel) || Number(channel) > 63
          || (value !== 0 && value !== 1))) throw new Error('Invalid relay state');
      channels = { ...this.store.relay(device.uuid).channels, ...Object.fromEntries(entries) };
      successful = true;
    } catch {
      channels = Object.fromEntries(Array.from({ length: device.relayCount }, (_, i) => [i, this.options.fallbackOn ? 1 : 0]));
      this.logger.warn({ deviceUuid: device.uuid }, 'Relay polling failed; applied configured fallback state');
    }
    this.store.save(device.uuid, { channels, observedAt: new Date(this.now()).toISOString(), successful });
  }
}

// Toshiba's existing decoder uses signed temperatures, with 0x7E representing -1 °C.
function encodeTemperature(temperature: number): number {
  const rounded = Math.max(-40, Math.min(60, Math.round(temperature)));
  return rounded === -1 ? 0x7e : rounded & 0xff;
}

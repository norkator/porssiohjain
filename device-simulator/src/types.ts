/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE for details.
 */
export interface SimulatedDevice {
  uuid: string;
  name: string;
  type: 'STANDARD' | 'HEAT_PUMP';
  relayCount: number;
}

export interface PumpState {
  stateHex: string;
  pendingHex: string | null;
  applyAt: number | null;
  roomTemperature: number;
  updatedAt: number;
}

export interface RelayState {
  channels: Record<string, 0 | 1>;
  observedAt: string;
  successful: boolean;
}

export function isDeviceList(value: unknown): value is SimulatedDevice[] {
  return Array.isArray(value) && value.length <= 1000 && value.every(device =>
    device && typeof device.name === 'string' && typeof device.uuid === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(device.uuid)
    && ['STANDARD', 'HEAT_PUMP'].includes(device.type)
    && Number.isInteger(device.relayCount) && device.relayCount >= 1 && device.relayCount <= 64);
}

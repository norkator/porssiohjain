/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

import { apiFetch, readApiErrorMessage } from "@/lib/api";
import { getCurrentLocale } from "@/lib/i18n";

export type TuyaConnection = { id: number; label: string; connected: boolean };
export type TuyaQrLogin = { id: string; payload: string; expiresAt: string };
export type TuyaHome = { id: string; name: string };
export type TuyaCloudDevice = { id: string; name: string; category: string };
export type TuyaReading = { code: string; value: string; unit: string };
export type TuyaSnapshot = {
  online: boolean;
  fetchedAt: string;
  readings: TuyaReading[];
  writableRelays: string[];
};

export class TuyaRequestError extends Error {}

async function tuyaRequest<T>(path: string, { method = "GET", body, signal }: {
  method?: string;
  body?: object;
  signal?: AbortSignal;
} = {}) {
  const headers = new Headers({ "Accept-Language": getCurrentLocale() });
  if (body) headers.set("Content-Type", "application/json");
  const response = await apiFetch(`/api/tuya${path}`, {
    body: body ? JSON.stringify(body) : undefined,
    cache: "no-store",
    headers,
    method,
    signal
  });
  if (!response.ok) {
    const message = await readApiErrorMessage(response);
    if (message) throw new TuyaRequestError(message);
    throw new Error(`Tuya request failed with status ${response.status}`);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export function fetchTuyaConnections(signal?: AbortSignal) {
  return tuyaRequest<TuyaConnection[]>("/connections", { signal });
}

export function renameTuyaConnection(connectionId: number, label: string) {
  return tuyaRequest<TuyaConnection>(`/connections/${connectionId}`, { method: "PATCH", body: { label } });
}

export function disconnectTuyaConnection(connectionId: number) {
  return tuyaRequest<void>(`/connections/${connectionId}/session`, { method: "DELETE" });
}

export function beginTuyaLogin(connectionId: number | null, label: string, userCode: string) {
  return tuyaRequest<TuyaQrLogin>("/login", { method: "POST", body: { connectionId, label, userCode } });
}

export function finishTuyaLogin(attemptId: string) {
  return tuyaRequest<TuyaConnection>(`/login/${attemptId}/complete`, { method: "POST" });
}

export function cancelTuyaLogin(attemptId: string) {
  return tuyaRequest<void>(`/login/${attemptId}`, { method: "DELETE" });
}

export function fetchTuyaHomes(connectionId: number, signal: AbortSignal) {
  return tuyaRequest<TuyaHome[]>(`/connections/${connectionId}/homes`, { signal });
}

export function fetchTuyaDevices(connectionId: number, homeId: string, signal: AbortSignal) {
  return tuyaRequest<TuyaCloudDevice[]>(`/connections/${connectionId}/homes/${encodeURIComponent(homeId)}/devices`, { signal });
}

export function importTuyaDevice(body: { connectionId: number; externalId: string; name: string; timezone: string; enabled: boolean }) {
  return tuyaRequest<{ deviceId: number }>("/devices", { method: "POST", body });
}

export function fetchTuyaDeviceConnection(deviceId: number, signal: AbortSignal) {
  return tuyaRequest<TuyaConnection>(`/devices/${deviceId}/connection`, { signal });
}

export function pollTuyaDevice(deviceId: number) {
  return tuyaRequest<TuyaSnapshot>(`/devices/${deviceId}/poll`, { method: "POST" });
}

export function switchTuyaRelay(deviceId: number, code: string, on: boolean) {
  return tuyaRequest<TuyaSnapshot>(`/devices/${deviceId}/relays/${encodeURIComponent(code)}`, { method: "POST", body: { on } });
}

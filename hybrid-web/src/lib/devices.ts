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

import { apiFetch, apiGetJson } from "@/lib/api";

export type ApiDevice = {
  id: number;
  uuid: string;
  deviceType: string;
  enabled: boolean;
  deviceName: string;
  timezone: string;
  lastCommunication: string | null;
  createdAt: string;
  updatedAt: string;
  accountId: number;
  shared: boolean;
  apiOnline: boolean;
  mqttOnline: boolean;
  mqttUsername: string | null;
  mqttPassword: string | null;
  hpName: string | null;
  acType: string | null;
  acUsername: string | null;
  acPassword: string | null;
  acDeviceId: string | null;
  buildingId: number | null;
  acDeviceUniqueId: string | null;
};

export type HeatPumpStateResponse = {
  acType: AcType;
  currentState: string | null;
  lastPolledState: string | null;
};

export type DeviceType = "STANDARD" | "HEAT_PUMP";
export type AcType = "NONE" | "TOSHIBA" | "MITSUBISHI";

export type DevicePayload = {
  deviceName: string;
  timezone: string;
  deviceType: DeviceType;
  enabled: boolean;
  hpName?: string;
  acType?: AcType;
  acUsername?: string;
  acPassword?: string;
  acDeviceId?: string;
  buildingId?: string;
};

export async function fetchDevices() {
  return apiGetJson<ApiDevice[]>("/devices");
}

export async function fetchDevice(deviceId: number) {
  return apiGetJson<ApiDevice>(`/devices/${deviceId}`);
}

export async function updateDevice(deviceId: number, payload: DevicePayload) {
  const response = await apiFetch(`/devices/${deviceId}`, {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json"
    },
    method: "PUT"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<ApiDevice>;
}

export async function deleteDevice(deviceId: number) {
  const response = await apiFetch(`/devices/${deviceId}`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function fetchHeatPumpState(deviceId: number) {
  return apiGetJson<HeatPumpStateResponse>(`/devices/${deviceId}/heat-pump/state`);
}

export async function sendHeatPumpCommand(deviceId: number, state: string) {
  const response = await apiFetch(`/devices/${deviceId}/heat-pump/commands`, {
    body: JSON.stringify({ state }),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function sendMqttRelayDebugCommand(deviceId: number, channel: number, on: boolean) {
  const response = await apiFetch(`/devices/${deviceId}/mqtt-relays/${channel}`, {
    body: JSON.stringify({ on }),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export function getDeviceConnectionState(device: ApiDevice) {
  if (!device.enabled) {
    return {
      label: "Disabled",
      tone: "offline" as const
    };
  }

  if (device.apiOnline && device.mqttOnline) {
    return {
      label: "API + MQTT Online",
      tone: "online" as const
    };
  }

  if (device.apiOnline) {
    return {
      label: "API Online",
      tone: "online" as const
    };
  }

  if (device.mqttOnline) {
    return {
      label: "MQTT Online",
      tone: "online" as const
    };
  }

  return {
    label: "Offline",
    tone: "offline" as const
  };
}

export function getDeviceAccent(device: ApiDevice) {
  switch (device.deviceType) {
    case "HEAT_PUMP":
      return "border-primary";
    case "INVERTER":
      return "border-primary-container";
    case "STANDARD":
      return "border-secondary";
    default:
      return "border-primary-fixed-dim";
  }
}

export function formatDeviceType(deviceType: string) {
  return deviceType
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function formatAcType(acType: string | null | undefined) {
  switch (acType) {
    case "TOSHIBA":
      return "Toshiba";
    case "MITSUBISHI":
      return "Mitsubishi";
    case "NONE":
    default:
      return "None";
  }
}

export function formatDeviceLastCommunication(lastCommunication: string | null) {
  if (!lastCommunication) {
    return "No contact yet";
  }

  const date = new Date(lastCommunication);

  if (Number.isNaN(date.getTime())) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short"
  }).format(date);
}

export function getOnlineDeviceCount(devices: ApiDevice[]) {
  return devices.filter((device) => device.enabled && (device.apiOnline || device.mqttOnline)).length;
}

export function getLatestDeviceCommunication(devices: ApiDevice[]) {
  return devices
    .filter((device) => device.lastCommunication)
    .sort((left, right) => Date.parse(right.lastCommunication ?? "") - Date.parse(left.lastCommunication ?? ""))[0]
    ?.lastCommunication ?? null;
}

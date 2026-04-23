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

import { apiGetJson } from "@/lib/api";
import { type ApiDevice, fetchDevices } from "@/lib/devices";
import { getSessionData } from "@/lib/session";

type CreateDeviceRequest = {
  deviceName: string;
  timezone: string;
  deviceType: string;
  enabled: boolean;
  acType?: "TOSHIBA" | "MITSUBISHI";
  hpName?: string;
  acUsername?: string;
  acPassword?: string;
  acDeviceId?: string;
  buildingId?: string;
  acDeviceUniqueId?: string;
};

function getBackendDeviceType(deviceTypeId: string) {
  switch (deviceTypeId) {
    case "shelly-pro-relays":
      return "STANDARD";
    case "toshiba-heat-pump":
    case "mitsubishi-heat-pump":
      return "HEAT_PUMP";
    default:
      throw new Error(`Unsupported device type: ${deviceTypeId}`);
  }
}

function getBackendAcType(deviceTypeId: string) {
  switch (deviceTypeId) {
    case "toshiba-heat-pump":
      return "TOSHIBA" as const;
    case "mitsubishi-heat-pump":
      return "MITSUBISHI" as const;
    default:
      return undefined;
  }
}

async function resolveAccountId() {
  const session = getSessionData();

  if (typeof session.accountId === "number") {
    return session.accountId;
  }

  const devices = await fetchDevices();
  const existingAccountId = devices[0]?.accountId;

  if (typeof existingAccountId === "number") {
    return existingAccountId;
  }

  throw new Error("Account ID unavailable. Add accountId to bootstrap data before creating the first device.");
}

export async function createDevice(input: {
  deviceName: string;
  timezone: string;
  deviceTypeId: string;
  hpName?: string;
  acUsername?: string;
  acPassword?: string;
  acDeviceId?: string;
  buildingId?: string;
  acDeviceUniqueId?: string;
}) {
  const accountId = await resolveAccountId();
  const payload: CreateDeviceRequest = {
    deviceName: input.deviceName,
    timezone: input.timezone,
    deviceType: getBackendDeviceType(input.deviceTypeId),
    enabled: true,
    acType: getBackendAcType(input.deviceTypeId),
    hpName: input.hpName,
    acUsername: input.acUsername,
    acPassword: input.acPassword,
    acDeviceId: input.acDeviceId,
    buildingId: input.buildingId,
    acDeviceUniqueId: input.acDeviceUniqueId
  };

  return apiGetJson<ApiDevice>(`/device/create/${accountId}`, {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });
}

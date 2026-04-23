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

import { apiFetch } from "@/lib/api";

export type HeatPumpAcType = "TOSHIBA" | "MITSUBISHI";

export type HeatPumpAcDevice = {
  acType: HeatPumpAcType;
  id: string;
  name: string;
  buildingId: string | null;
  deviceUniqueId: string | null;
};

type ListSelectableHeatPumpAcDevicesRequest = {
  acType: HeatPumpAcType;
  hpName: string;
  acUsername: string;
  acPassword: string;
};

async function getErrorMessage(response: Response) {
  const fallbackMessage = `Request failed with status ${response.status}`;

  try {
    const text = await response.text();

    if (!text) {
      return fallbackMessage;
    }

    try {
      const parsed = JSON.parse(text) as { message?: string; error?: string };

      return parsed.message ?? parsed.error ?? text;
    } catch {
      return text;
    }
  } catch {
    return fallbackMessage;
  }
}

export async function listSelectableHeatPumpAcDevices(
  request: ListSelectableHeatPumpAcDevicesRequest
) {
  const response = await apiFetch("/devices/heat-pump/ac-devices", {
    body: JSON.stringify(request),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.json() as Promise<HeatPumpAcDevice[]>;
}

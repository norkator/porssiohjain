import { apiFetch, apiGetJson } from "@/lib/api";
import { type ApiDevice } from "@/lib/devices";

export type ControlMode = "BELOW_MAX_PRICE" | "CHEAPEST_HOURS" | "MANUAL" | "SCHEDULED";

export type ApiControl = {
  id: number;
  name: string;
  timezone: string;
  maxPriceSnt: number | null;
  minPriceSnt: number | null;
  dailyOnMinutes: number | null;
  taxPercent: number | null;
  mode: ControlMode;
  manualOn: boolean | null;
  alwaysOnBelowMinPrice: boolean | null;
  energyContractId: number | null;
  energyContractName: string | null;
  transferContractId: number | null;
  transferContractName: string | null;
  siteId: number | null;
  createdAt: string;
  updatedAt: string;
  shared: boolean;
};

export type ControlPayload = {
  name: string;
  timezone: string;
  maxPriceSnt: number;
  minPriceSnt: number;
  dailyOnMinutes: number;
  taxPercent: number;
  mode: ControlMode;
  manualOn: boolean;
  alwaysOnBelowMinPrice: boolean;
};

export type ControlDeviceLink = {
  id: number;
  controlId: number;
  deviceId: number;
  deviceChannel: number;
  estimatedPowerKw: number | null;
  device: Pick<ApiDevice, "createdAt" | "deviceName" | "deviceType" | "id" | "lastCommunication" | "updatedAt" | "uuid">;
};

export type ControlDeviceLinkPayload = {
  deviceId: number;
  deviceChannel: number;
  estimatedPowerKw: number | null;
};

export const CONTROL_MODES: ControlMode[] = ["BELOW_MAX_PRICE", "CHEAPEST_HOURS", "MANUAL", "SCHEDULED"];

export async function fetchControls() {
  return apiGetJson<ApiControl[]>("/api/controls");
}

export async function fetchControl(controlId: number) {
  return apiGetJson<ApiControl>(`/api/controls/${controlId}`);
}

export async function createControl(payload: ControlPayload) {
  const response = await apiFetch("/api/controls", {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<ApiControl>;
}

export async function updateControl(controlId: number, payload: ControlPayload) {
  const response = await apiFetch(`/api/controls/${controlId}`, {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json"
    },
    method: "PUT"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<ApiControl>;
}

export async function deleteControl(controlId: number) {
  const response = await apiFetch(`/api/controls/${controlId}`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export async function fetchControlDeviceLinks(controlId: number) {
  return apiGetJson<ControlDeviceLink[]>(`/api/controls/${controlId}/links`);
}

export async function addControlDeviceLink(controlId: number, payload: ControlDeviceLinkPayload) {
  const response = await apiFetch(`/api/controls/${controlId}/links/devices`, {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<ControlDeviceLink>;
}

export async function deleteControlDeviceLink(linkId: number) {
  const response = await apiFetch(`/control/delete/device/${linkId}`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
}

export function formatControlMode(mode: ControlMode) {
  switch (mode) {
    case "BELOW_MAX_PRICE":
      return "Below Max Price";
    case "CHEAPEST_HOURS":
      return "Cheapest Hours";
    case "MANUAL":
      return "Manual";
    case "SCHEDULED":
      return "Scheduled";
  }
}

export function formatControlDate(value: string | null | undefined, timezone?: string | null) {
  if (!value) {
    return "Not available";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    timeZone: timezone || undefined
  }).format(date);
}

export function getControlAccent(control: ApiControl) {
  if (control.shared) {
    return "border-secondary";
  }

  switch (control.mode) {
    case "MANUAL":
      return control.manualOn ? "border-primary" : "border-outline";
    case "CHEAPEST_HOURS":
      return "border-primary-container";
    case "SCHEDULED":
      return "border-secondary";
    case "BELOW_MAX_PRICE":
    default:
      return "border-primary-fixed-dim";
  }
}

export function getControlStatus(control: ApiControl) {
  if (control.mode === "MANUAL") {
    return {
      label: control.manualOn ? "Manual On" : "Manual Off",
      tone: control.manualOn ? ("online" as const) : ("offline" as const)
    };
  }

  return {
    label: control.shared ? "Shared" : "Active",
    tone: "online" as const
  };
}

export function getLatestControlUpdate(controls: ApiControl[]) {
  return controls
    .filter((control) => control.updatedAt)
    .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))[0]
    ?.updatedAt ?? null;
}

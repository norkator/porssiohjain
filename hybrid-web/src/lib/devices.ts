import { apiGetJson } from "@/lib/api";

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

export async function fetchDevices() {
  return apiGetJson<ApiDevice[]>("/devices");
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

export type DeviceTypeOption = {
  id: string;
  title: string;
  description: string;
  icon: string;
  setupNotice: string;
};

export type AddDeviceDraft = {
  deviceTypeId?: string;
  deviceName: string;
  timezone: string;
  hpName: string;
  acUsername: string;
  acPassword: string;
  acDeviceId: string;
  acDeviceLabel: string;
  acBuildingId: string;
  acDeviceUniqueId: string;
  uuid: string;
};

export type ProvisionedDeviceDraft = {
  id: number;
  uuid: string;
  mqttPassword: string | null;
  mqttUsername: string | null;
};

const STORAGE_KEY = "energy-controller.add-device-draft";
const PROVISIONED_DEVICE_STORAGE_KEY = "energy-controller.add-device-provisioned";

export const DEVICE_TYPE_OPTIONS: DeviceTypeOption[] = [
  {
    id: "shelly-pro-relays",
    title: "Shelly Pro Relays",
    description: "Devices switching on loads like Pro 1/1PM, Pro 2PM, Pro 3, and Pro 4PM.",
    icon: "S",
    setupNotice:
      "Before adding Shelly device make sure you have Shelly device added and configured in Shelly Smart Control application."
  },
  {
    id: "toshiba-heat-pump",
    title: "Toshiba Heat Pump",
    description: "Devices using the Toshiba Home AC Control app.",
    icon: "T",
    setupNotice:
      "Before continuing, make sure your Toshiba heat pump is already available in Toshiba Home AC Control application."
  },
  {
    id: "mitsubishi-heat-pump",
    title: "Mitsubishi Heat Pump",
    description: "Devices using the MELCloud app.",
    icon: "M",
    setupNotice:
      "Before continuing, make sure your Mitsubishi heat pump is already available in MELCloud application."
  }
];

export function getCurrentTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
}

export function getAvailableTimezones() {
  const intlWithSupportedValues = Intl as unknown as {
    supportedValuesOf?: (key: string) => string[];
  };

  const timezones = intlWithSupportedValues.supportedValuesOf?.("timeZone") ?? [getCurrentTimezone(), "UTC"];

  return Array.from(new Set([getCurrentTimezone(), ...timezones])).sort((left, right) => left.localeCompare(right));
}

export function getDefaultAddDeviceDraft(): AddDeviceDraft {
  return {
    deviceName: "",
    timezone: getCurrentTimezone(),
    hpName: "",
    acUsername: "",
    acPassword: "",
    acDeviceId: "",
    acDeviceLabel: "",
    acBuildingId: "",
    acDeviceUniqueId: "",
    uuid: ""
  };
}

export function getDeviceTypeOption(deviceTypeId?: string) {
  return DEVICE_TYPE_OPTIONS.find((option) => option.id === deviceTypeId);
}

export function readAddDeviceDraft(): AddDeviceDraft {
  if (typeof window === "undefined") {
    return getDefaultAddDeviceDraft();
  }

  const fallback = getDefaultAddDeviceDraft();
  const rawValue = window.sessionStorage.getItem(STORAGE_KEY);

  if (!rawValue) {
    return fallback;
  }

  try {
    const parsed = JSON.parse(rawValue) as Partial<AddDeviceDraft>;

    return {
      deviceTypeId: parsed.deviceTypeId,
      deviceName: parsed.deviceName ?? fallback.deviceName,
      timezone: parsed.timezone ?? fallback.timezone,
      hpName: parsed.hpName ?? fallback.hpName,
      acUsername: parsed.acUsername ?? fallback.acUsername,
      acPassword: parsed.acPassword ?? fallback.acPassword,
      acDeviceId: parsed.acDeviceId ?? fallback.acDeviceId,
      acDeviceLabel: parsed.acDeviceLabel ?? fallback.acDeviceLabel,
      acBuildingId: parsed.acBuildingId ?? fallback.acBuildingId,
      acDeviceUniqueId: parsed.acDeviceUniqueId ?? fallback.acDeviceUniqueId,
      uuid: parsed.uuid ?? fallback.uuid
    };
  } catch {
    return fallback;
  }
}

export function writeAddDeviceDraft(draft: AddDeviceDraft) {
  if (typeof window === "undefined") {
    return;
  }

  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
}

export function updateAddDeviceDraft(patch: Partial<AddDeviceDraft>) {
  const nextDraft = {
    ...readAddDeviceDraft(),
    ...patch
  };

  writeAddDeviceDraft(nextDraft);

  return nextDraft;
}

export function clearAddDeviceDraft() {
  if (typeof window === "undefined") {
    return;
  }

  window.sessionStorage.removeItem(STORAGE_KEY);
}

export function readProvisionedDeviceDraft() {
  if (typeof window === "undefined") {
    return null;
  }

  const rawValue = window.sessionStorage.getItem(PROVISIONED_DEVICE_STORAGE_KEY);

  if (!rawValue) {
    return null;
  }

  try {
    return JSON.parse(rawValue) as ProvisionedDeviceDraft;
  } catch {
    return null;
  }
}

export function writeProvisionedDeviceDraft(device: ProvisionedDeviceDraft) {
  if (typeof window === "undefined") {
    return;
  }

  window.sessionStorage.setItem(PROVISIONED_DEVICE_STORAGE_KEY, JSON.stringify(device));
}

export function clearProvisionedDeviceDraft() {
  if (typeof window === "undefined") {
    return;
  }

  window.sessionStorage.removeItem(PROVISIONED_DEVICE_STORAGE_KEY);
}

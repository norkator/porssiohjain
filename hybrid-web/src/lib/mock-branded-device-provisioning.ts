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

import { getAndroidBridge } from "@/lib/android-bridge";

export type MockDiscoveredBrandedDevice = {
  discoveryId: string;
  serialNumber: string;
  claimCode: string;
  productModel: string;
  firmwareVersion: string;
  rssi: number;
  relayChannels: number;
};

export type MockWifiAccessPoint = {
  ssid: string;
  rssi: number;
  security: "open" | "wep" | "wpa" | "wpa2" | "wpa3" | string;
  mock?: boolean;
};

export const MOCK_WIFI_ACCESS_POINT: MockWifiAccessPoint = {
  ssid: "Web UI mock network",
  rssi: -55,
  security: "wpa2",
  mock: true
};

const MOCK_DISCOVERED_DEVICES: MockDiscoveredBrandedDevice[] = [
  {
    discoveryId: "mock:PO-DIN-DEMO-001",
    serialNumber: "PO-DIN-DEMO-001",
    claimCode: "QR-DEMO-001",
    productModel: "Pörssiohjain DIN Relay 4CH",
    firmwareVersion: "demo-1.0.0",
    rssi: -41,
    relayChannels: 4
  },
  {
    discoveryId: "mock:PO-DIN-DEMO-002",
    serialNumber: "PO-DIN-DEMO-002",
    claimCode: "QR-DEMO-002",
    productModel: "Pörssiohjain DIN Relay 2CH",
    firmwareVersion: "demo-1.0.0",
    rssi: -56,
    relayChannels: 2
  },
  {
    discoveryId: "mock:PO-DIN-DEMO-003",
    serialNumber: "PO-DIN-DEMO-003",
    claimCode: "QR-DEMO-003",
    productModel: "Pörssiohjain DIN Relay 1CH",
    firmwareVersion: "demo-1.0.0",
    rssi: -68,
    relayChannels: 1
  }
];

export function getMockDiscoveredBrandedDevices() {
  return MOCK_DISCOVERED_DEVICES;
}

function normalizeWifiAccessPoints(networks: Partial<MockWifiAccessPoint>[]) {
  const seenSsids = new Set<string>();
  const normalizedNetworks: MockWifiAccessPoint[] = [];

  networks.forEach((network) => {
    const ssid = network.ssid?.trim();

    if (!ssid || seenSsids.has(ssid)) {
      return;
    }

    seenSsids.add(ssid);
    normalizedNetworks.push({
      ssid,
      rssi: typeof network.rssi === "number" ? network.rssi : -100,
      security: network.security ?? "wpa2"
    });
  });

  return normalizedNetworks;
}

export async function scanWifiAccessPoints(discoveryId: string) {
  const bridge = getAndroidBridge();
  let networks: MockWifiAccessPoint[] = [];

  if (bridge?.scanWifiAccessPoints) {
    const rawResult = bridge.scanWifiAccessPoints(discoveryId);
    const parsed = JSON.parse(rawResult) as { networks?: Partial<MockWifiAccessPoint>[] };
    networks = normalizeWifiAccessPoints(parsed.networks ?? []);
  }

  return [...networks, MOCK_WIFI_ACCESS_POINT];
}

export async function mockClaimBrandedDevice(input: {
  device: MockDiscoveredBrandedDevice;
  deviceName: string;
  ssid: string;
  wifiPassword: string;
}) {
  await new Promise((resolve) => window.setTimeout(resolve, 500));

  return {
    claimCode: input.device.claimCode,
    deviceName: input.deviceName,
    serialNumber: input.device.serialNumber,
    ssid: input.ssid
  };
}

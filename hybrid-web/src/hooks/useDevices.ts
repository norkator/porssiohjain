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

import { useEffect, useState } from "react";
import { fetchDevices, getLatestDeviceCommunication, getOnlineDeviceCount, type ApiDevice } from "@/lib/devices";

type UseDevicesState = {
  devices: ApiDevice[];
  isLoading: boolean;
  error: string | null;
};

export function useDevices() {
  const [state, setState] = useState<UseDevicesState>({
    devices: [],
    isLoading: true,
    error: null
  });

  useEffect(() => {
    let isActive = true;

    async function loadDevices() {
      try {
        const devices = await fetchDevices();

        if (!isActive) {
          return;
        }

        setState({
          devices,
          isLoading: false,
          error: null
        });
      } catch (error) {
        if (!isActive) {
          return;
        }

        setState({
          devices: [],
          isLoading: false,
          error: error instanceof Error ? error.message : "Failed to load devices"
        });
      }
    }

    loadDevices();

    return () => {
      isActive = false;
    };
  }, []);

  return {
    ...state,
    onlineCount: getOnlineDeviceCount(state.devices),
    totalCount: state.devices.length,
    latestCommunication: getLatestDeviceCommunication(state.devices)
  };
}

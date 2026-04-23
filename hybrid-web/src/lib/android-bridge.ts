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

export type BootstrapData = {
  appName?: string;
  baseUrl?: string;
  token?: string;
  accountId?: number;
  hasToken?: boolean;
  environment?: "local" | "production" | string;
  error?: string;
};

type AndroidBridgeApi = {
  getBootstrapData?: () => string;
  showToast?: (message: string) => void;
  openNativeScreen?: (screen: string) => void;
  logout?: () => void;
};

declare global {
  interface Window {
    AndroidBridge?: AndroidBridgeApi;
  }
}

export function getAndroidBridge(): AndroidBridgeApi | undefined {
  return window.AndroidBridge;
}

export function getBootstrapData(): BootstrapData {
  const bridge = getAndroidBridge();

  if (!bridge?.getBootstrapData) {
    return {
      appName: "Energy Controller",
      baseUrl: "http://localhost",
      token: "",
      accountId: undefined,
      hasToken: false,
      environment: "local",
      error: "Android bridge unavailable"
    };
  }

  try {
    return JSON.parse(bridge.getBootstrapData());
  } catch (error) {
    return {
      error: String(error)
    };
  }
}

export function showNativeToast(message: string) {
  getAndroidBridge()?.showToast?.(message);
}

export function openNativeScreen(screen: string) {
  getAndroidBridge()?.openNativeScreen?.(screen);
}

export function logoutNative() {
  getAndroidBridge()?.logout?.();
}

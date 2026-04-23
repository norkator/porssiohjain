import { getAndroidBridge, getBootstrapData, type BootstrapData } from "@/lib/android-bridge";

const DEV_SESSION_STORAGE_KEY = "energy-controller.dev-session";

type DevSessionOverride = {
  token?: string;
  baseUrl?: string;
};

export type SessionData = BootstrapData & {
  token: string;
  baseUrl: string;
  hasToken: boolean;
  source: "android" | "local-storage" | "env";
};

function getDevSessionOverride(): DevSessionOverride {
  if (typeof window === "undefined") {
    return {};
  }

  const rawValue = window.localStorage.getItem(DEV_SESSION_STORAGE_KEY);

  if (!rawValue) {
    return {};
  }

  try {
    return JSON.parse(rawValue) as DevSessionOverride;
  } catch {
    return {};
  }
}

function getEnvSessionFallback(): SessionData {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "";
  const token = import.meta.env.VITE_DEV_TOKEN ?? "";

  return {
    appName: "Energy Controller",
    baseUrl,
    token,
    hasToken: Boolean(token),
    environment: "local",
    source: "env",
    error: token ? undefined : "No development token configured"
  };
}

export function getSessionData(): SessionData {
  if (getAndroidBridge()?.getBootstrapData) {
    const bootstrap = getBootstrapData();

    return {
      appName: bootstrap.appName ?? "Energy Controller",
      baseUrl: bootstrap.baseUrl ?? "",
      token: bootstrap.token ?? "",
      accountId: bootstrap.accountId,
      hasToken: Boolean(bootstrap.token),
      environment: bootstrap.environment ?? "production",
      error: bootstrap.error,
      source: "android"
    };
  }

  const envSession = getEnvSessionFallback();
  const override = getDevSessionOverride();
  const baseUrl = override.baseUrl ?? envSession.baseUrl;
  const token = override.token ?? envSession.token;
  const hasOverride = override.baseUrl !== undefined || override.token !== undefined;

  return {
    appName: "Energy Controller",
    baseUrl,
    token,
    accountId: undefined,
    hasToken: Boolean(token),
    environment: "local",
    source: hasOverride ? "local-storage" : "env",
    error: token ? undefined : "Set a token in window.devSession or .env.local"
  };
}

export function setDevSessionOverride(nextOverride: DevSessionOverride) {
  if (typeof window === "undefined") {
    return;
  }

  const currentOverride = getDevSessionOverride();
  const mergedOverride = {
    ...currentOverride,
    ...nextOverride
  };

  window.localStorage.setItem(DEV_SESSION_STORAGE_KEY, JSON.stringify(mergedOverride));
}

export function clearDevSessionOverride() {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(DEV_SESSION_STORAGE_KEY);
}

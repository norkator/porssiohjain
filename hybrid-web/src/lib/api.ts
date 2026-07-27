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

import { getSessionData, handleUnauthorizedSession, setBrowserSession } from "@/lib/session";

type RefreshResponse = {
  token: string;
  refreshToken: string;
  accountId?: number;
  locale?: string;
};

let refreshInFlight: Promise<boolean> | undefined;

export function getApiUrl(path: string) {
  const { baseUrl } = getSessionData();

  return new URL(path, baseUrl).toString();
}

export function getAuthHeaders(headers?: HeadersInit) {
  const { token } = getSessionData();
  const nextHeaders = new Headers(headers);

  if (token) {
    nextHeaders.set("Authorization", token);
  }

  return nextHeaders;
}

export async function apiFetch(path: string, init?: RequestInit) {
  let response = await fetch(getApiUrl(path), {
    ...init,
    headers: getAuthHeaders(init?.headers)
  });

  if (response.status === 401) {
    const refreshed = await refreshSession();

    if (refreshed) {
      response = await fetch(getApiUrl(path), {
        ...init,
        headers: getAuthHeaders(init?.headers)
      });
    }

    if (response.status === 401) {
      handleUnauthorizedSession();
    }
  }

  return response;
}

async function refreshSession() {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = undefined;
    });
  }

  return refreshInFlight;
}

async function performRefresh() {
  const session = getSessionData();

  if (!session.refreshToken) {
    return false;
  }

  try {
    const response = await fetch(getApiUrl("/account/token/refresh"), {
      body: JSON.stringify({ refreshToken: session.refreshToken }),
      headers: {
        "Content-Type": "application/json"
      },
      method: "POST"
    });

    if (!response.ok) {
      return false;
    }

    const refreshed = await response.json() as RefreshResponse;
    setBrowserSession({
      token: refreshed.token,
      refreshToken: refreshed.refreshToken,
      accountId: refreshed.accountId ?? session.accountId,
      locale: refreshed.locale ?? session.locale
    });
    return true;
  } catch {
    return false;
  }
}

export async function apiGetJson<T>(path: string, init?: RequestInit) {
  const response = await apiFetch(path, init);

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

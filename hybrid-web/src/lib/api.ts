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

import { getSessionData, handleUnauthorizedSession } from "@/lib/session";

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
  const response = await fetch(getApiUrl(path), {
    ...init,
    headers: getAuthHeaders(init?.headers)
  });

  if (response.status === 401) {
    handleUnauthorizedSession();
  }

  return response;
}

export async function apiGetJson<T>(path: string, init?: RequestInit) {
  const response = await apiFetch(path, init);

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

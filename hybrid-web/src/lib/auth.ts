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

import { getSessionData, setBrowserSessionToken } from "@/lib/session";

export type LoginResponse = {
  token: string;
  expiresAt: string;
};

export type CreatedAccount = {
  id?: number;
  uuid: string;
  secret: string;
};

function getPublicApiUrl(path: string) {
  const { baseUrl } = getSessionData();

  if (!baseUrl) {
    throw new Error("API base URL is not configured.");
  }

  return new URL(path, baseUrl).toString();
}

async function readError(response: Response) {
  const text = await response.text();

  if (!text) {
    return `Request failed with status ${response.status}`;
  }

  try {
    const parsed = JSON.parse(text) as { message?: string; error?: string };
    return parsed.message ?? parsed.error ?? text;
  } catch {
    return text;
  }
}

export async function loginWithCredentials(input: { uuid: string; secret: string }) {
  const response = await fetch(getPublicApiUrl("/account/login"), {
    body: JSON.stringify(input),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  const loginResponse = await response.json() as LoginResponse;
  setBrowserSessionToken(loginResponse.token);

  return loginResponse;
}

export async function createAccount() {
  const response = await fetch(getPublicApiUrl("/account/create"), {
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<CreatedAccount>;
}

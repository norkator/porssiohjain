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

import { getPublicApiUrl, readError, type LoginResponse } from "@/lib/auth";

export type QrLoginChallenge = {
  challengeId: string;
  browserSecret: string;
  qrPayload: string;
  expiresAt: string;
  pollIntervalMs: number;
};

export type QrLoginStatus = {
  status: "PENDING" | "APPROVED" | "CONSUMED" | "CANCELLED" | "EXPIRED";
  expiresAt: string;
};

export async function createQrLoginChallenge() {
  const response = await fetch(getPublicApiUrl("/account/qr-login/challenges"), {
    body: JSON.stringify({
      browserName: typeof navigator === "undefined" ? undefined : navigator.userAgent,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
    }),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<QrLoginChallenge>;
}

export async function completeQrLoginChallenge(challengeId: string, browserSecret: string) {
  const response = await fetch(getPublicApiUrl(`/account/qr-login/challenges/${challengeId}/complete`), {
    body: JSON.stringify({ browserSecret }),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<QrLoginStatus | LoginResponse>;
}

export async function cancelQrLoginChallenge(challengeId: string, browserSecret: string) {
  const response = await fetch(getPublicApiUrl(`/account/qr-login/challenges/${challengeId}/cancel`), {
    body: JSON.stringify({ browserSecret }),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<QrLoginStatus>;
}

export function isQrLoginComplete(response: QrLoginStatus | LoginResponse): response is LoginResponse {
  return "token" in response && typeof response.token === "string";
}

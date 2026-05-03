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

import { apiFetch, apiGetJson } from "@/lib/api";

export type AccountTier = "FREE" | "PRO" | "BUSINESS";

export type MeResponse = {
  accountId: number;
  uuid: string;
  tier: AccountTier;
  email: string | null;
  locale: string;
  notifyPowerLimitExceeded: boolean;
  emailNotificationsEnabled: boolean;
  pushNotificationsEnabled: boolean;
  createdAt: string | null;
  deviceLimit: number;
  controlLimit: number | null;
  productionSourceLimit: number | null;
  weatherControlLimit: number | null;
  weeklyEmailNotificationLimit: number;
  weeklyPushNotificationLimit: number | null;
};

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

export async function fetchMe() {
  return apiGetJson<MeResponse>("/me");
}

export async function updateMe(input: {
  email: string;
  locale: string;
  notifyPowerLimitExceeded: boolean;
  emailNotificationsEnabled: boolean;
  pushNotificationsEnabled: boolean;
}) {
  const response = await apiFetch("/me", {
    body: JSON.stringify(input),
    headers: {
      "Content-Type": "application/json"
    },
    method: "PUT"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  return response.json() as Promise<MeResponse>;
}

export async function changePassword(input: { currentPassword: string; newPassword: string }) {
  const response = await apiFetch("/me/password", {
    body: JSON.stringify(input),
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }
}

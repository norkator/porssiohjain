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

import { apiGetJson } from "@/lib/api";

const CONTROL_SAVINGS_CACHE_PREFIX = "porssiohjain.controlSavings.";
const CONTROL_SAVINGS_CACHE_TTL_MS = 15 * 60 * 1000;

export type ControlSavings = {
  from: string;
  to: string;
  timezone: string;
  baselineMethod: string;
  estimatedPowerKw: number;
  estimatedUsageKwh: number;
  baselineCostEur: number;
  controlledCostEur: number;
  estimatedSavingsEur: number;
  controlCount: number;
  controlsWithEstimatedPowerCount: number;
  scheduleEntryCount: number;
};

export function fetchControlSavings(input?: { from?: string; to?: string; timezone?: string }) {
  const params = new URLSearchParams();

  if (input?.from) {
    params.set("from", input.from);
  }
  if (input?.to) {
    params.set("to", input.to);
  }
  if (input?.timezone) {
    params.set("timezone", input.timezone);
  }

  const query = params.toString();
  return apiGetJson<ControlSavings>(`/dashboard/control-savings${query ? `?${query}` : ""}`);
}

export async function fetchCachedControlSavings(input?: { from?: string; to?: string; timezone?: string }) {
  const cacheKey = `${CONTROL_SAVINGS_CACHE_PREFIX}${input?.from ?? ""}|${input?.to ?? ""}|${input?.timezone ?? ""}`;
  const now = Date.now();

  try {
    const cached = window.sessionStorage.getItem(cacheKey);
    if (cached) {
      const parsed = JSON.parse(cached) as { expiresAt: number; value: ControlSavings };
      if (parsed.expiresAt > now) {
        return parsed.value;
      }
      window.sessionStorage.removeItem(cacheKey);
    }
  } catch {
    // Ignore storage failures; the network request remains the source of truth.
  }

  const value = await fetchControlSavings(input);
  try {
    window.sessionStorage.setItem(cacheKey, JSON.stringify({
      expiresAt: now + CONTROL_SAVINGS_CACHE_TTL_MS,
      value
    }));
  } catch {
    // Ignore quota and private-mode storage failures.
  }
  return value;
}

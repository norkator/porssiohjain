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

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

export type NordpoolTodayChartPoint = {
  timestamp: string;
  price: number;
};

export type NordpoolTodayChart = {
  date: string;
  timezone: string;
  resolutionMinutes: number;
  min: number;
  avg: number;
  max: number;
  current: number;
  points: NordpoolTodayChartPoint[];
};

export async function fetchNordpoolTodayChart(timezone: string) {
  const params = new URLSearchParams();

  if (timezone) {
    params.set("timezone", timezone);
  }

  const suffix = params.toString();

  return apiGetJson<NordpoolTodayChart>(`/nordpool/today-chart${suffix ? `?${suffix}` : ""}`);
}

export function formatNordpoolPrice(value: number) {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

export function formatNordpoolTime(value: string, timezone?: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: timezone || undefined
  }).format(new Date(value));
}

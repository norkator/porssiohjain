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

export type MarketNotificationMetric = "CURRENT_PRICE" | "DAILY_AVERAGE";
export type MarketNotificationComparison = "GREATER_THAN" | "LESS_THAN";

export type MarketNotification = {
  id: number;
  name: string;
  description: string | null;
  metric: MarketNotificationMetric;
  comparisonType: MarketNotificationComparison;
  thresholdPrice: number;
  activeFrom: string;
  activeTo: string;
  timezone: string;
  enabled: boolean;
  lastSentAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type MarketNotificationPayload = {
  name: string;
  description: string | null;
  metric: MarketNotificationMetric;
  comparisonType: MarketNotificationComparison;
  thresholdPrice: number;
  activeFrom: string;
  activeTo: string;
  timezone: string;
  enabled: boolean;
};

export async function fetchNordpoolTodayChart(timezone: string) {
  const params = new URLSearchParams();

  if (timezone) {
    params.set("timezone", timezone);
  }

  const suffix = params.toString();

  return apiGetJson<NordpoolTodayChart>(`/nordpool/today-chart${suffix ? `?${suffix}` : ""}`);
}

async function sendMarketNotification<T>(path: string, payload: unknown, method = "POST") {
  const response = await apiFetch(path, {
    body: payload === undefined ? undefined : JSON.stringify(payload),
    headers: { "Content-Type": "application/json" },
    method
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return text ? JSON.parse(text) as T : undefined as T;
}

export const fetchMarketNotifications = () => apiGetJson<MarketNotification[]>("/api/market-notifications");
export const createMarketNotification = (payload: MarketNotificationPayload) => sendMarketNotification<MarketNotification>("/api/market-notifications", payload);
export const updateMarketNotification = (id: number, payload: MarketNotificationPayload) => sendMarketNotification<MarketNotification>(`/api/market-notifications/${id}`, payload, "PUT");
export const deleteMarketNotification = (id: number) => sendMarketNotification<void>(`/api/market-notifications/${id}`, undefined, "DELETE");

export function formatNordpoolPrice(value: number) {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

export function formatNordpoolTime(value: string, timezone?: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    hour12: false,
    hourCycle: "h23",
    minute: "2-digit",
    timeZone: timezone || undefined
  }).format(new Date(value));
}

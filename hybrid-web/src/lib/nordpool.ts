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
import { getCurrentIntlLocales } from "@/lib/i18n";

const NORDPOOL_CHART_CACHE_PREFIX = "porssiohjain.nordpoolChart.v2.";
const NORDPOOL_CHART_CACHE_TTL_MS = 15 * 60 * 1000;

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

export function mergeNordpoolCharts(charts: NordpoolTodayChart[]): NordpoolTodayChart | null {
  const first = charts.find((chart) => chart.points.length > 0) ?? charts[0];
  if (!first) return null;

  const pointsByTime = new Map(charts.flatMap((chart) => chart.points)
    .map((point) => [Date.parse(point.timestamp), point]));
  const points = [...pointsByTime.entries()].sort(([left], [right]) => left - right).map(([, point]) => point);
  const prices = points.map((point) => point.price);

  return {
    ...first,
    points,
    min: prices.length ? Math.min(...prices) : 0,
    max: prices.length ? Math.max(...prices) : 0,
    avg: prices.length ? prices.reduce((sum, price) => sum + price, 0) / prices.length : 0
  };
}

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
  return fetchNordpoolChart("/nordpool/today-chart", timezone);
}

export async function fetchNordpoolTomorrowChart(timezone: string) {
  return fetchNordpoolChart("/nordpool/tomorrow-chart", timezone);
}

export async function fetchCachedNordpoolTodayChart(timezone: string) {
  return fetchCachedNordpoolChart("today", "/nordpool/today-chart", timezone);
}

export async function fetchCachedNordpoolTomorrowChart(timezone: string) {
  return fetchCachedNordpoolChart("tomorrow", "/nordpool/tomorrow-chart", timezone);
}

async function fetchNordpoolChart(path: string, timezone: string) {
  const params = new URLSearchParams();

  if (timezone) {
    params.set("timezone", timezone);
  }

  const suffix = params.toString();

  return apiGetJson<NordpoolTodayChart>(`${path}${suffix ? `?${suffix}` : ""}`);
}

async function fetchCachedNordpoolChart(day: "today" | "tomorrow", path: string, timezone: string) {
  const now = Date.now();
  const localDate = new Intl.DateTimeFormat("sv-SE", {
    year: "numeric", month: "2-digit", day: "2-digit", timeZone: timezone || undefined
  }).format(new Date(now));
  const cacheKey = `${NORDPOOL_CHART_CACHE_PREFIX}${day}.${timezone}.${localDate}`;

  try {
    const cached = window.sessionStorage.getItem(cacheKey);
    if (cached) {
      const parsed = JSON.parse(cached) as { expiresAt: number; value: NordpoolTodayChart };
      if (parsed.expiresAt > now) {
        return parsed.value;
      }
      window.sessionStorage.removeItem(cacheKey);
    }
  } catch {
    // Ignore storage failures; the API remains the source of truth.
  }

  const value = await fetchNordpoolChart(path, timezone);
  try {
    window.sessionStorage.setItem(cacheKey, JSON.stringify({
      expiresAt: now + NORDPOOL_CHART_CACHE_TTL_MS,
      value
    }));
  } catch {
    // Ignore quota and private-mode storage failures.
  }
  return value;
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
  return new Intl.NumberFormat(getCurrentIntlLocales(), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

export function formatNordpoolTime(value: string, timezone?: string) {
  return new Intl.DateTimeFormat(getCurrentIntlLocales(), {
    hour: "2-digit",
    hour12: false,
    hourCycle: "h23",
    minute: "2-digit",
    timeZone: timezone || undefined
  }).format(new Date(value));
}

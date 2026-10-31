/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */

import { apiFetch, apiGetJson } from "@/lib/api";
export type ComfortSchedule = {
  id: number | null;
  name: string;
  schedule: {
    defaultTarget: number;
    periods: { from: string; to: string; target: number }[];
  };
};
export type PlanPoint = {
  time: string;
  price: number | null;
  outdoor: number | null;
  room: number | null;
  floor: number | null;
  floorSetpoint: number | null;
  heatPumpSetpoint: number | null;
  target: number | null;
  wood: number | null;
  mode: string;
  reason: string;
};
export type PlannerRoom = {
  id: number;
  name: string;
  target: number;
  scheduleId: number | null;
  points: PlanPoint[];
};
export type Monitor = {
  start: string;
  tomorrow: string;
  end: string;
  rooms: PlannerRoom[];
  schedules: ComfortSchedule[];
  summary: {
    site: { id: number; name: string };
    timezone: string;
    status: { code: string };
    latestPlan: { generatedAt: string } | null;
    coverage: { tomorrowAvailability: string };
    automation: { lastAutomationError: string | null };
    nextWoodRecommendation: {
      roomId: number;
      notifyAt: string;
      reason: string;
    } | null;
  };
};
export const fetchMonitor = (signal?: AbortSignal) =>
  apiGetJson<Monitor>("/api/heating-planner/monitor", { signal });
export async function savePlanner(path: string, body: unknown) {
  return apiGetJsonOrEmpty(`/api/heating-planner/${path}`, body);
}
async function apiGetJsonOrEmpty(path: string, body: unknown) {
  const response = await apiFetch(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(
      error?.message ??
        "Could not save comfort settings. Check temperatures and overlapping time windows.",
    );
  }
}

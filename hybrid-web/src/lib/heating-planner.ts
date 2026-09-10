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

export type HeatingPlannerHeatSourceType = "FLOOR_HEATING" | "WOOD_STOVE" | "OTHER";

export type HeatingPlannerSiteChoice = {
  id: number;
  name: string;
  timezone: string | null;
  weatherPlace: string | null;
};

export type HeatingPlannerDeviceChoice = {
  id: number;
  name: string;
  type: string;
};

export type HeatingPlannerTransferContractChoice = {
  id: number;
  name: string;
};

export type HeatingPlannerRoom = {
  name: string;
  sourceType: HeatingPlannerHeatSourceType;
  targetRoomTemperature: number;
  normalFloorTemperature: number;
  maximumPreheatFloorTemperature: number;
  absoluteMaximumFloorTemperature: number;
  dischargeFloorSetpoint: number;
  controllingDeviceId: number | null;
  roomSensorDeviceId: number | null;
  floorSensorDeviceId: number | null;
};

export type HeatingPlannerConfiguration = {
  enabled: boolean;
  plannerActiveBelowTemperature: number;
  woodRecommendationBelowTemperature: number;
  taxPercent: number;
  transferContractId: number | null;
  stoveLoaded: boolean;
  stoveAvailableFrom: string;
  stoveAvailableTo: string;
  woodAmount: number;
  woodReleaseDelayMinutes: number;
  woodReleaseDurationMinutes: number;
  cheapPriceThreshold: number;
  expensivePriceThreshold: number;
  cheapPricePercentile: number;
  expensivePricePercentile: number;
  rooms: HeatingPlannerRoom[];
};

export type HeatingPlannerActiveControl = {
  ready: boolean;
  active: boolean;
  issues: string[];
  candidatePlanVersion: string | null;
  lastAutomaticPlanAt: string | null;
  lastAutomaticActivationAt: string | null;
  lastAutomationError: string | null;
};

export type HeatingPlannerPlanPoint = {
  room: string;
  plannedTime: string;
  priceCentsPerKwh: number | null;
  outdoorTemperature: number | null;
  predictedFloorTemperature: number | null;
  predictedRoomTemperature: number | null;
  plannedFloorSetpoint: number | null;
  predictedWoodHeatRate: number | null;
  heating: boolean;
  operatingMode: string;
  reason: string;
};

export type HeatingPlannerPlan = {
  planVersion: string;
  status: string;
  createdAt: string;
  horizonStart: string;
  horizonEnd: string;
  triggerReason: string;
  points: HeatingPlannerPlanPoint[];
};

export type HeatingPlannerResponse = {
  selectedSiteId: number | null;
  sites: HeatingPlannerSiteChoice[];
  devices: HeatingPlannerDeviceChoice[];
  transferContracts: HeatingPlannerTransferContractChoice[];
  configuration: HeatingPlannerConfiguration;
  activeControl: HeatingPlannerActiveControl;
  latestPlan: HeatingPlannerPlan | null;
};

async function sendJson<T>(path: string, method: string, payload?: unknown) {
  const response = await apiFetch(path, {
    body: payload === undefined ? undefined : JSON.stringify(payload),
    headers: payload === undefined ? undefined : { "Content-Type": "application/json" },
    method
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function fetchHeatingPlanner(siteId?: number | null) {
  const suffix = siteId ? `?siteId=${encodeURIComponent(String(siteId))}` : "";
  return apiGetJson<HeatingPlannerResponse>(`/api/heating-planner${suffix}`);
}

export function saveHeatingPlanner(siteId: number, configuration: HeatingPlannerConfiguration) {
  return sendJson<HeatingPlannerResponse>(`/api/heating-planner/${siteId}`, "PUT", { configuration });
}

export function recalculateHeatingPlanner(siteId: number) {
  return sendJson<HeatingPlannerResponse>(`/api/heating-planner/${siteId}/recalculate`, "POST");
}

export function enableHeatingPlannerActiveControl(siteId: number) {
  return sendJson<HeatingPlannerActiveControl>(`/api/heating-planner/${siteId}/active-control`, "POST");
}

export function disableHeatingPlannerActiveControl(siteId: number) {
  return sendJson<HeatingPlannerActiveControl>(`/api/heating-planner/${siteId}/active-control`, "DELETE");
}

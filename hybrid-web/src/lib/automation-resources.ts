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
import { type ApiDevice } from "@/lib/devices";

export type ApiSite = {
  id: number;
  name: string;
  timezone: string | null;
  weatherPlace: string | null;
};

export type ProductionApiType = "SHELLY" | "SOFAR_SOLARMANPV";
export type ComparisonType = "GREATER_THAN" | "LESS_THAN";
export type ControlAction = "TURN_ON" | "TURN_OFF" | "SET_TEMPERATURE" | "SET_MODE";
export type WeatherMetricType = "TEMPERATURE" | "HUMIDITY";

export type ApiSiteWeatherForecastPoint = {
  time: string;
  temperature: number | null;
  windSpeedMs: number | null;
  windGust: number | null;
  humidity: number | null;
  totalCloudCover: number | null;
  precipitationAmount: number | null;
};

export type ApiSiteWeatherForecast = {
  siteId: number | null;
  siteName: string | null;
  weatherPlace: string | null;
  timezone: string | null;
  fetchedAt: string | null;
  forecastStartTime: string | null;
  forecastEndTime: string | null;
  timestepMinutes: number | null;
  points: ApiSiteWeatherForecastPoint[];
};

export type ApiWeatherControl = {
  id: number;
  name: string;
  siteId: number;
  siteName: string | null;
  siteTimezone: string | null;
  createdAt: string;
  updatedAt: string;
  shared: boolean | null;
};

export type WeatherControlPayload = {
  name: string;
  siteId: number;
};

export type WeatherControlDeviceLink = {
  id: number;
  weatherControlId: number;
  deviceId: number;
  deviceChannel: number;
  weatherMetric: WeatherMetricType;
  comparisonType: ComparisonType;
  thresholdValue: number;
  controlAction: ControlAction;
  priorityRule: boolean;
  device: Pick<ApiDevice, "deviceName" | "deviceType" | "id" | "uuid">;
};

export type WeatherControlDevicePayload = {
  deviceId: number;
  deviceChannel: number;
  weatherMetric: WeatherMetricType;
  comparisonType: ComparisonType;
  thresholdValue: number;
  controlAction: ControlAction;
  priorityRule: boolean;
};

export type WeatherControlHeatPumpLink = {
  id: number;
  weatherControlId: number;
  deviceId: number;
  stateHex: string;
  weatherMetric: WeatherMetricType;
  comparisonType: ComparisonType;
  thresholdValue: number;
  device: Pick<ApiDevice, "deviceName" | "deviceType" | "id" | "uuid">;
};

export type WeatherControlHeatPumpPayload = {
  deviceId: number;
  stateHex: string;
  weatherMetric: WeatherMetricType;
  comparisonType: ComparisonType;
  thresholdValue: number;
};

export type ApiProductionSource = {
  id: number;
  uuid: string;
  name: string;
  apiType: ProductionApiType;
  currentKw: number | null;
  peakKw: number | null;
  enabled: boolean;
  timezone: string;
  siteId: number | null;
  siteName: string | null;
  appId: string | null;
  appSecret: string | null;
  email: string | null;
  password: string | null;
  stationId: string | null;
  createdAt: string;
  updatedAt: string;
  shared: boolean | null;
};

export type ProductionSourcePayload = {
  name: string;
  apiType: ProductionApiType;
  appId: string | null;
  appSecret: string | null;
  email: string | null;
  password: string | null;
  stationId: string | null;
  enabled: boolean;
  timezone?: string;
  siteId?: number | null;
};

export type ProductionSourceDeviceLink = {
  id: number;
  sourceId: number;
  deviceId: number;
  deviceChannel: number;
  triggerKw: number;
  comparisonType: ComparisonType;
  action: ControlAction;
  enabled: boolean;
  device: Pick<ApiDevice, "deviceName" | "deviceType" | "id" | "uuid">;
};

export type ProductionSourceDevicePayload = {
  deviceId: number;
  deviceChannel: number;
  triggerKw: number;
  comparisonType: ComparisonType;
  action: ControlAction;
};

export type ApiPowerLimit = {
  id: number;
  uuid: string;
  name: string;
  limitKw: number | null;
  currentKw: number | null;
  peakKw: number | null;
  enabled: boolean;
  notifyEnabled: boolean;
  timezone: string;
  limitIntervalMinutes: number | null;
  siteId: number | null;
  createdAt: string;
  updatedAt: string;
  lastTotalKwh: number | null;
};

export type PowerLimitPayload = {
  name: string;
  limitKw: number;
  enabled: boolean;
  notifyEnabled?: boolean;
  timezone?: string;
  limitIntervalMinutes?: number;
  siteId?: number | null;
};

export type PowerLimitDeviceLink = {
  id: number;
  powerLimitId: number;
  deviceId: number;
  deviceChannel: number;
  device: Pick<ApiDevice, "deviceName" | "deviceType" | "id" | "uuid">;
};

export type PowerLimitHistoryPoint = {
  accountId: number;
  kilowatts: number;
  createdAt: string;
};

export function formatDate(value: string | null | undefined, timezone?: string | null) {
  if (!value) return "Not available";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Unknown";
  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    timeZone: timezone || undefined
  }).format(date);
}

export function formatKw(value: number | null | undefined) {
  return typeof value === "number" && Number.isFinite(value) ? value.toFixed(2) : "-";
}

async function postJson<T>(path: string, payload: unknown, method = "POST") {
  const response = await apiFetch(path, {
    body: payload === undefined ? undefined : JSON.stringify(payload),
    headers: { "Content-Type": "application/json" },
    method
  });

  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
  if (response.status === 204) return undefined as T;

  const text = await response.text();
  if (!text) return undefined as T;

  return JSON.parse(text) as T;
}

export const WEATHER_METRICS: WeatherMetricType[] = ["TEMPERATURE", "HUMIDITY"];
export const COMPARISONS: ComparisonType[] = ["GREATER_THAN", "LESS_THAN"];
export const CONTROL_ACTIONS: ControlAction[] = ["TURN_ON", "TURN_OFF"];
export const PRODUCTION_API_TYPES: ProductionApiType[] = ["SHELLY", "SOFAR_SOLARMANPV"];

export const fetchSites = () => apiGetJson<ApiSite[]>("/api/sites");

export const fetchWeatherControls = () => apiGetJson<ApiWeatherControl[]>("/api/weather-controls");
export const fetchWeatherControl = (id: number) => apiGetJson<ApiWeatherControl>(`/api/weather-controls/${id}`);
export const fetchWeatherControlWeather = (id: number) => apiGetJson<ApiSiteWeatherForecast>(`/api/weather-controls/${id}/weather`);
export const createWeatherControl = (payload: WeatherControlPayload) => postJson<ApiWeatherControl>("/api/weather-controls", payload);
export const updateWeatherControl = (id: number, payload: WeatherControlPayload) => postJson<ApiWeatherControl>(`/api/weather-controls/${id}`, payload, "PUT");
export const fetchWeatherControlDeviceLinks = (id: number) => apiGetJson<WeatherControlDeviceLink[]>(`/api/weather-controls/${id}/devices`);
export const addWeatherControlDeviceLink = (id: number, payload: WeatherControlDevicePayload) => postJson<WeatherControlDeviceLink>(`/api/weather-controls/${id}/devices`, payload);
export const updateWeatherControlDeviceLink = (id: number, payload: WeatherControlDevicePayload) => postJson<WeatherControlDeviceLink>(`/api/weather-controls/devices/${id}`, payload, "PUT");
export const deleteWeatherControlDeviceLink = (id: number) => postJson<void>(`/api/weather-controls/devices/${id}`, undefined, "DELETE");
export const fetchWeatherControlHeatPumpLinks = (id: number) => apiGetJson<WeatherControlHeatPumpLink[]>(`/api/weather-controls/${id}/heat-pumps`);
export const addWeatherControlHeatPumpLink = (id: number, payload: WeatherControlHeatPumpPayload) => postJson<WeatherControlHeatPumpLink>(`/api/weather-controls/${id}/heat-pumps`, payload);
export const updateWeatherControlHeatPumpLink = (id: number, payload: WeatherControlHeatPumpPayload) => postJson<WeatherControlHeatPumpLink>(`/api/weather-controls/heat-pumps/${id}`, payload, "PUT");
export const deleteWeatherControlHeatPumpLink = (id: number) => postJson<void>(`/api/weather-controls/heat-pumps/${id}`, undefined, "DELETE");

export const fetchProductionSources = () => apiGetJson<ApiProductionSource[]>("/api/production-sources");
export const fetchProductionSource = (id: number) => apiGetJson<ApiProductionSource>(`/api/production-sources/${id}`);
export const createProductionSource = (payload: ProductionSourcePayload) => postJson<void>("/api/production-sources", payload);
export const updateProductionSource = (id: number, payload: ProductionSourcePayload) => postJson<ApiProductionSource>(`/api/production-sources/${id}`, payload, "PUT");
export const deleteProductionSource = (id: number) => postJson<void>(`/api/production-sources/${id}`, undefined, "DELETE");
export const fetchProductionSourceDeviceLinks = (id: number) => apiGetJson<ProductionSourceDeviceLink[]>(`/api/production-sources/${id}/devices`);
export const addProductionSourceDeviceLink = (id: number, payload: ProductionSourceDevicePayload) => postJson<void>(`/api/production-sources/${id}/devices`, payload);
export const deleteProductionSourceDeviceLink = (sourceId: number, linkId: number) => postJson<void>(`/api/production-sources/${sourceId}/devices/${linkId}`, undefined, "DELETE");

export const fetchPowerLimits = () => apiGetJson<ApiPowerLimit[]>("/api/power-limits");
export const fetchPowerLimit = (id: number) => apiGetJson<ApiPowerLimit>(`/api/power-limits/${id}`);
export const createPowerLimit = (payload: PowerLimitPayload) => postJson<ApiPowerLimit>("/api/power-limits", payload);
export const updatePowerLimit = (id: number, payload: PowerLimitPayload) => postJson<ApiPowerLimit>(`/api/power-limits/${id}`, payload, "PUT");
export const deletePowerLimit = (id: number) => postJson<void>(`/api/power-limits/${id}`, undefined, "DELETE");
export const fetchPowerLimitDeviceLinks = (id: number) => apiGetJson<PowerLimitDeviceLink[]>(`/api/power-limits/${id}/devices`);
export const fetchPowerLimitHistory = (id: number, hours = 24) => apiGetJson<PowerLimitHistoryPoint[]>(`/api/power-limits/${id}/history?hours=${hours}`);
export const addPowerLimitDeviceLink = (id: number, payload: { deviceId: number; deviceChannel: number }) => postJson<void>(`/api/power-limits/${id}/devices`, payload);
export const deletePowerLimitDeviceLink = (id: number) => postJson<void>(`/api/power-limits/devices/${id}`, undefined, "DELETE");

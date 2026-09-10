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

import PageHeader from "@/components/PageHeader";
import {
  disableHeatingPlannerActiveControl,
  enableHeatingPlannerActiveControl,
  fetchHeatingPlanner,
  recalculateHeatingPlanner,
  saveHeatingPlanner,
  type HeatingPlannerConfiguration,
  type HeatingPlannerDeviceChoice,
  type HeatingPlannerHeatSourceType,
  type HeatingPlannerPlan,
  type HeatingPlannerPlanPoint,
  type HeatingPlannerResponse,
  type HeatingPlannerRoom
} from "@/lib/heating-planner";
import { useI18n } from "@/lib/i18n";
import { type ChangeEvent, type FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

const CHART_WIDTH = 920;
const CHART_HEIGHT = 300;
const CHART_PADDING = { bottom: 42, left: 48, right: 18, top: 24 };

const DEFAULT_ROOM: HeatingPlannerRoom = {
  name: "Living room",
  sourceType: "FLOOR_HEATING",
  targetRoomTemperature: 21,
  normalFloorTemperature: 23,
  maximumPreheatFloorTemperature: 27,
  absoluteMaximumFloorTemperature: 29,
  dischargeFloorSetpoint: 19,
  controllingDeviceId: null,
  roomSensorDeviceId: null,
  floorSensorDeviceId: null
};

function formatDateTime(value: string | null | undefined, timezone?: string | null) {
  if (!value) return "-";

  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    timeZone: timezone || undefined
  }).format(new Date(value));
}

function numericValue(value: FormDataEntryValue | null, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function nullableNumber(value: FormDataEntryValue | null) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function heatSourceLabel(type: HeatingPlannerHeatSourceType, labels: Record<string, string>) {
  return labels[type] ?? type;
}

function deviceOptions(devices: HeatingPlannerDeviceChoice[], type: "THERMOSTAT" | "TEMPERATURE_SENSOR" | "ANY") {
  return devices.filter((device) => type === "ANY" || device.type === type);
}

function linePath<T>(items: T[], getX: (item: T, index: number) => number, getY: (item: T) => number | null) {
  return items
    .map((item, index) => {
      const y = getY(item);
      if (y === null) return null;
      return `${index === 0 ? "M" : "L"} ${getX(item, index).toFixed(2)} ${y.toFixed(2)}`;
    })
    .filter(Boolean)
    .join(" ");
}

function HeatingPlanChart({ plan, timezone }: { plan: HeatingPlannerPlan | null; timezone?: string | null }) {
  const points = useMemo(() => {
    if (!plan?.points.length) return [];
    const firstRoom = plan.points[0].room;
    return plan.points.filter((point) => point.room === firstRoom).slice(0, 192);
  }, [plan]);

  if (!plan || points.length === 0) {
    return (
      <div className="rounded-xl bg-surface-container-low p-6 text-sm text-on-surface-variant">
        No saved plan points yet. Save rooms and recalculate a plan from the planner service to populate this preview.
      </div>
    );
  }

  const values = points.flatMap((point) => [
    point.priceCentsPerKwh,
    point.predictedFloorTemperature,
    point.predictedRoomTemperature,
    point.plannedFloorSetpoint
  ]).filter((value): value is number => typeof value === "number" && Number.isFinite(value));
  const minValue = Math.min(...values, 0);
  const maxValue = Math.max(...values, 1);
  const innerWidth = CHART_WIDTH - CHART_PADDING.left - CHART_PADDING.right;
  const innerHeight = CHART_HEIGHT - CHART_PADDING.top - CHART_PADDING.bottom;
  const xFor = (_point: HeatingPlannerPlanPoint, index: number) =>
    CHART_PADDING.left + (innerWidth * index) / Math.max(points.length - 1, 1);
  const yFor = (value: number | null) => {
    if (typeof value !== "number" || !Number.isFinite(value)) return null;
    return CHART_PADDING.top + innerHeight - ((value - minValue) / Math.max(maxValue - minValue, 1)) * innerHeight;
  };
  const pricePath = linePath(points, xFor, (point) => yFor(point.priceCentsPerKwh));
  const floorPath = linePath(points, xFor, (point) => yFor(point.predictedFloorTemperature));
  const roomPath = linePath(points, xFor, (point) => yFor(point.predictedRoomTemperature));
  const setpointPath = linePath(points, xFor, (point) => yFor(point.plannedFloorSetpoint));

  return (
    <div className="overflow-x-auto rounded-xl bg-surface-container-low p-3">
      <svg className="h-auto min-w-[44rem] w-full" role="img" viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}>
        <rect
          fill="rgb(var(--chart-plot-background))"
          height={innerHeight}
          rx="16"
          width={innerWidth}
          x={CHART_PADDING.left}
          y={CHART_PADDING.top}
        />
        {[0, 0.25, 0.5, 0.75, 1].map((step) => {
          const y = CHART_PADDING.top + innerHeight * step;
          const labelValue = maxValue - (maxValue - minValue) * step;
          return (
            <g key={step}>
              <line stroke="rgb(var(--color-outline-variant) / 0.58)" strokeDasharray="5 8" x1={CHART_PADDING.left} x2={CHART_WIDTH - CHART_PADDING.right} y1={y} y2={y} />
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="10" textAnchor="end" x={CHART_PADDING.left - 8} y={y + 3}>
                {labelValue.toFixed(0)}
              </text>
            </g>
          );
        })}
        {points.filter((_point, index) => index % 16 === 0 || index === points.length - 1).map((point, index) => (
          <text fill="rgb(var(--color-on-surface-variant))" fontSize="10" key={`${point.plannedTime}-${index}`} textAnchor="middle" x={xFor(point, points.indexOf(point))} y={CHART_HEIGHT - 16}>
            {formatDateTime(point.plannedTime, timezone)}
          </text>
        ))}
        <path d={pricePath} fill="none" stroke="rgb(var(--color-secondary))" strokeWidth="2" />
        <path d={floorPath} fill="none" stroke="rgb(var(--color-primary))" strokeWidth="2.5" />
        <path d={roomPath} fill="none" stroke="rgb(34 197 94)" strokeWidth="2.5" />
        <path d={setpointPath} fill="none" stroke="rgb(239 68 68)" strokeDasharray="6 6" strokeWidth="2" />
      </svg>
      <div className="mt-3 flex flex-wrap gap-3 text-xs text-on-surface-variant">
        <span className="font-bold text-secondary">Price</span>
        <span className="font-bold text-primary">Floor prediction</span>
        <span className="font-bold text-emerald-600">Room prediction</span>
        <span className="font-bold text-red-500">Setpoint</span>
      </div>
    </div>
  );
}

export default function HeatingPlannerView() {
  const { group, t } = useI18n("heatingPlanner");
  const common = useI18n("common").t;
  const heatSourceLabels = group("heatSources");
  const [data, setData] = useState<HeatingPlannerResponse | null>(null);
  const [configuration, setConfiguration] = useState<HeatingPlannerConfiguration | null>(null);
  const [selectedSiteId, setSelectedSiteId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isRecalculating, setIsRecalculating] = useState(false);
  const [isTogglingActive, setIsTogglingActive] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load(siteId?: number | null) {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetchHeatingPlanner(siteId);
      setData(response);
      setConfiguration({
        ...response.configuration,
        rooms: response.configuration.rooms.length > 0 ? response.configuration.rooms : [DEFAULT_ROOM]
      });
      setSelectedSiteId(response.selectedSiteId);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("failedLoad"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const selectedSite = data?.sites.find((site) => site.id === selectedSiteId) ?? null;
  const thermostatChoices = deviceOptions(data?.devices ?? [], "THERMOSTAT");
  const temperatureSensorChoices = deviceOptions(data?.devices ?? [], "TEMPERATURE_SENSOR");
  const floorSensorChoices = deviceOptions(data?.devices ?? [], "ANY");
  const latestReason = data?.latestPlan?.points.find((point) => point.operatingMode !== "INACTIVE")?.reason
    ?? data?.latestPlan?.points[0]?.reason
    ?? t("noPlanReason");

  function updateConfiguration(patch: Partial<HeatingPlannerConfiguration>) {
    setConfiguration((current) => current ? { ...current, ...patch } : current);
  }

  function updateRoom(index: number, patch: Partial<HeatingPlannerRoom>) {
    setConfiguration((current) => {
      if (!current) return current;
      return {
        ...current,
        rooms: current.rooms.map((room, roomIndex) => roomIndex === index ? { ...room, ...patch } : room)
      };
    });
  }

  function addRoom() {
    setConfiguration((current) => current ? {
      ...current,
      rooms: [...current.rooms, { ...DEFAULT_ROOM, name: t("newRoomName") }]
    } : current);
  }

  function removeRoom(index: number) {
    setConfiguration((current) => current ? {
      ...current,
      rooms: current.rooms.filter((_room, roomIndex) => roomIndex !== index)
    } : current);
  }

  async function handleSiteChange(event: ChangeEvent<HTMLSelectElement>) {
    const siteId = Number(event.target.value);
    setSelectedSiteId(siteId);
    await load(siteId);
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSiteId || !configuration) return;
    setIsSaving(true);
    setError(null);
    setMessage(null);
    try {
      const saved = await saveHeatingPlanner(selectedSiteId, configuration);
      setData(saved);
      setConfiguration(saved.configuration.rooms.length > 0
        ? saved.configuration
        : { ...saved.configuration, rooms: [DEFAULT_ROOM] });
      setMessage(t("saved"));
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleActiveControlToggle() {
    if (!selectedSiteId || !data) return;
    setIsTogglingActive(true);
    setError(null);
    setMessage(null);
    try {
      const activeControl = data.activeControl.active
        ? await disableHeatingPlannerActiveControl(selectedSiteId)
        : await enableHeatingPlannerActiveControl(selectedSiteId);
      setData({ ...data, activeControl });
      setMessage(activeControl.active ? t("activeEnabled") : t("activeDisabled"));
    } catch (toggleError) {
      setError(toggleError instanceof Error ? toggleError.message : t("failedActiveControl"));
    } finally {
      setIsTogglingActive(false);
    }
  }

  async function handleRecalculate() {
    if (!selectedSiteId) return;
    setIsRecalculating(true);
    setError(null);
    setMessage(null);
    try {
      const recalculated = await recalculateHeatingPlanner(selectedSiteId);
      setData(recalculated);
      setConfiguration(recalculated.configuration.rooms.length > 0
        ? recalculated.configuration
        : { ...recalculated.configuration, rooms: [DEFAULT_ROOM] });
      setMessage(t("recalculated"));
    } catch (recalculateError) {
      setError(recalculateError instanceof Error ? recalculateError.message : t("failedRecalculate"));
    } finally {
      setIsRecalculating(false);
    }
  }

  function readFormSettings(formData: FormData) {
    if (!configuration) return;
    updateConfiguration({
      cheapPricePercentile: numericValue(formData.get("cheapPricePercentile"), configuration.cheapPricePercentile),
      cheapPriceThreshold: numericValue(formData.get("cheapPriceThreshold"), configuration.cheapPriceThreshold),
      enabled: formData.get("enabled") === "on",
      expensivePricePercentile: numericValue(formData.get("expensivePricePercentile"), configuration.expensivePricePercentile),
      expensivePriceThreshold: numericValue(formData.get("expensivePriceThreshold"), configuration.expensivePriceThreshold),
      plannerActiveBelowTemperature: numericValue(formData.get("plannerActiveBelowTemperature"), configuration.plannerActiveBelowTemperature),
      stoveAvailableFrom: String(formData.get("stoveAvailableFrom") || configuration.stoveAvailableFrom),
      stoveAvailableTo: String(formData.get("stoveAvailableTo") || configuration.stoveAvailableTo),
      stoveLoaded: formData.get("stoveLoaded") === "on",
      taxPercent: numericValue(formData.get("taxPercent"), configuration.taxPercent),
      transferContractId: nullableNumber(formData.get("transferContractId")),
      woodAmount: numericValue(formData.get("woodAmount"), configuration.woodAmount),
      woodRecommendationBelowTemperature: numericValue(formData.get("woodRecommendationBelowTemperature"), configuration.woodRecommendationBelowTemperature),
      woodReleaseDelayMinutes: numericValue(formData.get("woodReleaseDelayMinutes"), configuration.woodReleaseDelayMinutes),
      woodReleaseDurationMinutes: numericValue(formData.get("woodReleaseDurationMinutes"), configuration.woodReleaseDurationMinutes)
    });
  }

  if (isLoading && !data) {
    return (
      <>
        <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} translucent />
        <main className="app-page pt-4 sm:pt-12">
          <div className="app-card p-6 text-sm text-on-surface-variant">{common("loading")}</div>
        </main>
      </>
    );
  }

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} translucent />
      <main className="app-page pt-4 sm:pt-12">
        <section className="mb-8 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-3xl">
            <p className="metric-label mb-3">{t("eyebrow")}</p>
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">{t("title")}</h1>
            <p className="text-lg leading-7 text-on-surface-variant">{t("description")}</p>
          </div>
          {data?.sites.length ? (
            <label className="block min-w-64 text-sm font-bold text-on-surface">
              {t("site")}
              <select
                className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 font-semibold outline-none focus:border-primary"
                onChange={handleSiteChange}
                value={selectedSiteId ?? ""}
              >
                {data.sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
              </select>
            </label>
          ) : null}
        </section>

        {error ? <div className="mb-6 rounded-lg border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{error}</div> : null}
        {message ? <div className="mb-6 rounded-lg bg-surface-container-high p-4 text-sm font-semibold text-on-surface">{message}</div> : null}

        {!data?.sites.length ? (
          <section className="app-card p-6">
            <h2 className="font-headline text-2xl font-black">{t("noSitesTitle")}</h2>
            <p className="mt-2 text-sm text-on-surface-variant">{t("noSitesDescription")}</p>
            <Link className="primary-action mt-5 w-fit px-5 py-3 text-sm" to="/sites">{t("openSites")}</Link>
          </section>
        ) : null}

        {configuration && selectedSite && data ? (
          <form className="space-y-8" onChange={(event) => readFormSettings(new FormData(event.currentTarget))} onSubmit={handleSave}>
            <section className="grid grid-cols-1 gap-5 lg:grid-cols-3">
              <article className="app-card p-5">
                <p className="metric-label mb-2">{t("plannerState")}</p>
                <label className="flex items-center justify-between gap-4 rounded-lg bg-surface-container-low p-4 font-bold">
                  <span>{configuration.enabled ? common("enabled") : common("disabled")}</span>
                  <input checked={configuration.enabled} name="enabled" onChange={(event) => updateConfiguration({ enabled: event.target.checked })} type="checkbox" />
                </label>
                <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
                  <div className="rounded-lg bg-surface-container-low p-3">
                    <span className="metric-label">{common("timezone")}</span>
                    <p className="font-semibold">{selectedSite.timezone ?? "-"}</p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-3">
                    <span className="metric-label">{t("weatherPlace")}</span>
                    <p className="font-semibold">{selectedSite.weatherPlace ?? "-"}</p>
                  </div>
                </div>
              </article>

              <article className="app-card p-5">
                <p className="metric-label mb-2">{t("activeControl")}</p>
                <h2 className="font-headline text-2xl font-black">{data.activeControl.active ? t("activeNow") : t("dryRun")}</h2>
                <p className="mt-2 text-sm text-on-surface-variant">{data.activeControl.ready ? t("activeReady") : data.activeControl.issues[0] ?? t("activeNotReady")}</p>
                <button
                  className={`mt-5 w-full justify-center px-5 py-3 text-sm ${data.activeControl.active ? "secondary-action" : "primary-action"} disabled:cursor-not-allowed disabled:opacity-60`}
                  disabled={isTogglingActive || (!data.activeControl.active && !data.activeControl.ready)}
                  onClick={handleActiveControlToggle}
                  type="button"
                >
                  {data.activeControl.active ? t("disableActive") : t("enableActive")}
                </button>
              </article>

              <article className="app-card p-5">
                <p className="metric-label mb-2">{t("latestPlan")}</p>
                <h2 className="font-headline text-2xl font-black">{data.latestPlan ? data.latestPlan.status : t("noPlan")}</h2>
                <p className="mt-2 text-sm text-on-surface-variant">
                  {data.latestPlan ? `${formatDateTime(data.latestPlan.horizonStart, selectedSite.timezone)} - ${formatDateTime(data.latestPlan.horizonEnd, selectedSite.timezone)}` : t("noPlanHelp")}
                </p>
              </article>
            </section>

            <section className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
              <div className="space-y-6">
                <article className="app-card p-5">
                  <h2 className="font-headline text-2xl font-black">{t("planningInputs")}</h2>
                  <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <NumberInput label={t("plannerActiveBelow")} name="plannerActiveBelowTemperature" value={configuration.plannerActiveBelowTemperature} />
                    <NumberInput label={t("woodRecommendationBelow")} name="woodRecommendationBelowTemperature" value={configuration.woodRecommendationBelowTemperature} />
                    <NumberInput label={t("taxPercent")} name="taxPercent" value={configuration.taxPercent} />
                    <label className="block text-sm font-bold">
                      {t("transferContract")}
                      <select className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" name="transferContractId" value={configuration.transferContractId ?? ""}>
                        <option value="">{common("notAvailable")}</option>
                        {data.transferContracts.map((contract) => <option key={contract.id} value={contract.id}>{contract.name}</option>)}
                      </select>
                    </label>
                    <NumberInput label={t("cheapThreshold")} name="cheapPriceThreshold" step="0.25" value={configuration.cheapPriceThreshold} />
                    <NumberInput label={t("expensiveThreshold")} name="expensivePriceThreshold" step="0.25" value={configuration.expensivePriceThreshold} />
                    <NumberInput label={t("cheapPercentile")} name="cheapPricePercentile" step="0.05" value={configuration.cheapPricePercentile} />
                    <NumberInput label={t("expensivePercentile")} name="expensivePricePercentile" step="0.05" value={configuration.expensivePricePercentile} />
                  </div>
                </article>

                <article className="app-card p-5">
                  <h2 className="font-headline text-2xl font-black">{t("woodStove")}</h2>
                  <label className="mt-5 flex items-center justify-between gap-4 rounded-lg bg-surface-container-low p-4 font-bold">
                    <span>{t("stoveLoaded")}</span>
                    <input checked={configuration.stoveLoaded} name="stoveLoaded" onChange={(event) => updateConfiguration({ stoveLoaded: event.target.checked })} type="checkbox" />
                  </label>
                  <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <TimeInput label={t("availableFrom")} name="stoveAvailableFrom" value={configuration.stoveAvailableFrom} />
                    <TimeInput label={t("availableTo")} name="stoveAvailableTo" value={configuration.stoveAvailableTo} />
                    <NumberInput label={t("woodAmount")} name="woodAmount" value={configuration.woodAmount} />
                    <NumberInput label={t("releaseDelay")} name="woodReleaseDelayMinutes" value={configuration.woodReleaseDelayMinutes} />
                    <NumberInput label={t("releaseDuration")} name="woodReleaseDurationMinutes" value={configuration.woodReleaseDurationMinutes} />
                  </div>
                </article>
              </div>

              <div className="space-y-6">
                <article className="app-card p-5">
                  <div className="flex items-center justify-between gap-4">
                    <h2 className="font-headline text-2xl font-black">{t("planPreview")}</h2>
                    <div className="flex flex-wrap items-center justify-end gap-2">
                      {data.latestPlan ? <span className="chip bg-surface-container-highest text-primary">{data.latestPlan.points.length} {t("points")}</span> : null}
                      <button
                        className="secondary-action rounded-lg px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={isRecalculating}
                        onClick={handleRecalculate}
                        type="button"
                      >
                        {isRecalculating ? common("syncing") : t("recalculate")}
                      </button>
                    </div>
                  </div>
                  <p className="mb-5 mt-2 text-sm text-on-surface-variant">{latestReason}</p>
                  <HeatingPlanChart plan={data.latestPlan} timezone={selectedSite.timezone} />
                </article>

                <article className="app-card p-5">
                  <h2 className="font-headline text-2xl font-black">{t("activeEvidence")}</h2>
                  <div className="mt-4 space-y-2">
                    {(data.activeControl.issues.length ? data.activeControl.issues : [t("noActiveIssues")]).map((issue) => (
                      <p className="rounded-lg bg-surface-container-low px-4 py-3 text-sm text-on-surface-variant" key={issue}>{issue}</p>
                    ))}
                  </div>
                </article>
              </div>
            </section>

            <section className="app-card p-5">
              <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-headline text-2xl font-black">{t("rooms")}</h2>
                  <p className="mt-1 text-sm text-on-surface-variant">{t("roomsDescription")}</p>
                </div>
                <button className="secondary-action justify-center px-4 py-3 text-sm" onClick={addRoom} type="button">+ {t("addRoom")}</button>
              </div>
              <div className="grid grid-cols-1 gap-5">
                {configuration.rooms.map((room, index) => (
                  <article className="rounded-xl border border-outline-variant/60 bg-surface-container-low p-4" key={`${room.name}-${index}`}>
                    <div className="mb-4 flex items-start justify-between gap-3">
                      <div>
                        <p className="metric-label mb-1">{t("room")} {index + 1}</p>
                        <h3 className="font-headline text-xl font-black">{room.name || t("newRoomName")}</h3>
                      </div>
                      <button className="secondary-action rounded-lg px-3 py-2 text-xs" disabled={configuration.rooms.length === 1} onClick={() => removeRoom(index)} type="button">{common("remove")}</button>
                    </div>
                    <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                      <TextInput label={t("roomName")} value={room.name} onChange={(value) => updateRoom(index, { name: value })} />
                      <label className="block text-sm font-bold">
                        {t("heatSource")}
                        <select className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" onChange={(event) => updateRoom(index, { sourceType: event.target.value as HeatingPlannerHeatSourceType })} value={room.sourceType}>
                          {(["FLOOR_HEATING", "WOOD_STOVE", "OTHER"] as HeatingPlannerHeatSourceType[]).map((type) => <option key={type} value={type}>{heatSourceLabel(type, heatSourceLabels)}</option>)}
                        </select>
                      </label>
                      <SelectDevice label={t("controller")} devices={thermostatChoices} value={room.controllingDeviceId} onChange={(value) => updateRoom(index, { controllingDeviceId: value })} />
                      <SelectDevice label={t("roomSensor")} devices={temperatureSensorChoices} value={room.roomSensorDeviceId} onChange={(value) => updateRoom(index, { roomSensorDeviceId: value })} />
                      <SelectDevice label={t("floorSensor")} devices={floorSensorChoices} value={room.floorSensorDeviceId} onChange={(value) => updateRoom(index, { floorSensorDeviceId: value })} />
                      <NumberControl label={t("targetRoom")} value={room.targetRoomTemperature} onChange={(value) => updateRoom(index, { targetRoomTemperature: value })} />
                      <NumberControl label={t("normalFloor")} value={room.normalFloorTemperature} onChange={(value) => updateRoom(index, { normalFloorTemperature: value })} />
                      <NumberControl label={t("preheatMax")} value={room.maximumPreheatFloorTemperature} onChange={(value) => updateRoom(index, { maximumPreheatFloorTemperature: value })} />
                      <NumberControl label={t("absoluteMax")} value={room.absoluteMaximumFloorTemperature} onChange={(value) => updateRoom(index, { absoluteMaximumFloorTemperature: value })} />
                      <NumberControl label={t("dischargeSetpoint")} value={room.dischargeFloorSetpoint} onChange={(value) => updateRoom(index, { dischargeFloorSetpoint: value })} />
                    </div>
                  </article>
                ))}
              </div>
            </section>

            <div className="sticky bottom-4 z-20 flex justify-end">
              <button className="primary-action justify-center px-7 py-4 disabled:cursor-not-allowed disabled:opacity-60" disabled={isSaving || !selectedSiteId} type="submit">
                {isSaving ? common("syncing") : t("save")}
              </button>
            </div>
          </form>
        ) : null}
      </main>
    </>
  );
}

function NumberInput({ label, name, step = "0.01", value }: { label: string; name: string; step?: string; value: number }) {
  return (
    <label className="block text-sm font-bold">
      {label}
      <input className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" defaultValue={value} name={name} step={step} type="number" />
    </label>
  );
}

function TimeInput({ label, name, value }: { label: string; name: string; value: string }) {
  return (
    <label className="block text-sm font-bold">
      {label}
      <input className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" defaultValue={value} name={name} type="time" />
    </label>
  );
}

function TextInput({ label, onChange, value }: { label: string; onChange: (value: string) => void; value: string }) {
  return (
    <label className="block text-sm font-bold">
      {label}
      <input className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" onChange={(event) => onChange(event.target.value)} type="text" value={value} />
    </label>
  );
}

function NumberControl({ label, onChange, value }: { label: string; onChange: (value: number) => void; value: number }) {
  return (
    <label className="block text-sm font-bold">
      {label}
      <input className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" onChange={(event) => onChange(Number(event.target.value))} step="0.1" type="number" value={value} />
    </label>
  );
}

function SelectDevice({ devices, label, onChange, value }: {
  devices: HeatingPlannerDeviceChoice[];
  label: string;
  onChange: (value: number | null) => void;
  value: number | null;
}) {
  const common = useI18n("common").t;

  return (
    <label className="block text-sm font-bold">
      {label}
      <select className="mt-2 w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 outline-none focus:border-primary" onChange={(event) => onChange(nullableNumber(event.target.value))} value={value ?? ""}>
        <option value="">{common("notAvailable")}</option>
        {devices.map((device) => <option key={device.id} value={device.id}>{device.name}</option>)}
      </select>
    </label>
  );
}

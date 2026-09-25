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
import AppDialog from "@/components/AppDialog";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import {
  createSite,
  fetchSiteWeather,
  fetchSites,
  fetchSupportedWeatherPlaces,
  formatDate,
  SITE_TYPES,
  SITE_OPERATION_STATES,
  updateSite,
  type ApiSite,
  type ApiSiteWeatherForecast,
  type SitePayload,
  type SiteOperationState,
  type SiteType
} from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

const DEFAULT_TIMEZONE = "Europe/Helsinki";

export default function SitesView() {
  const { t } = useI18n("sitesView");
  const common = useI18n("common").t;
  const siteTypeLabels: Record<SiteType, string> = {
    HOME: t("siteTypeHome"),
    APARTMENT: t("siteTypeApartment"),
    OFFICE: t("siteTypeOffice"),
    WAREHOUSE: t("siteTypeWarehouse"),
    FACTORY: t("siteTypeFactory"),
    COMMERCIAL: t("siteTypeCommercial"),
    SOLAR_PLANT: t("siteTypeSolarPlant"),
    OTHER: t("siteTypeOther")
  };
  const siteTypeLabel = (type: string) => siteTypeLabels[type as SiteType] ?? type;
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [supportedWeatherPlaces, setSupportedWeatherPlaces] = useState<string[]>([]);
  const [siteWeather, setSiteWeather] = useState<Record<number, ApiSiteWeatherForecast | null>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingSiteId, setEditingSiteId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [type, setType] = useState<SiteType>("HOME");
  const [operationState, setOperationState] = useState<SiteOperationState>("NORMAL");
  const [weatherPlace, setWeatherPlace] = useState("");
  const [weatherPlaceError, setWeatherPlaceError] = useState<string | null>(null);
  const [timezone, setTimezone] = useState(DEFAULT_TIMEZONE);
  const availableTimezones = useMemo(
    () => Array.from(new Set([DEFAULT_TIMEZONE, "UTC", ...getAvailableTimezones(), timezone])).sort(),
    [timezone]
  );
  const [enabled, setEnabled] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isFormDialogOpen, setIsFormDialogOpen] = useState(false);

  async function loadSiteWeather(sitesToLoad: ApiSite[]) {
    const weatherEntries = await Promise.all(
      sitesToLoad.map(async (site) => {
        if (!site.weatherPlace) return [site.id, null] as const;

        try {
          return [site.id, await fetchSiteWeather(site.id)] as const;
        } catch {
          return [site.id, null] as const;
        }
      })
    );

    setSiteWeather(Object.fromEntries(weatherEntries));
  }

  async function loadSites() {
    setIsLoading(true);
    setError(null);
    try {
      const [siteResponse, weatherPlaceResponse] = await Promise.all([
        fetchSites(),
        fetchSupportedWeatherPlaces()
      ]);
      setSites(siteResponse);
      setSupportedWeatherPlaces(weatherPlaceResponse);
      await loadSiteWeather(siteResponse);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("failedLoad"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadSites();
  }, []);

  function resetForm() {
    setEditingSiteId(null);
    setName("");
    setType("HOME");
    setOperationState("NORMAL");
    setWeatherPlace("");
    setWeatherPlaceError(null);
    setTimezone(DEFAULT_TIMEZONE);
    setEnabled(true);
  }

  function openCreateDialog() {
    resetForm();
    setError(null);
    setIsFormDialogOpen(true);
  }

  function startEdit(site: ApiSite) {
    setEditingSiteId(site.id);
    setName(site.name);
    setType(site.type as SiteType);
    setOperationState(site.operationState ?? "NORMAL");
    setWeatherPlace(site.weatherPlace ?? "");
    setWeatherPlaceError(null);
    setTimezone(site.timezone ?? DEFAULT_TIMEZONE);
    setEnabled(site.enabled);
    setError(null);
    setIsFormDialogOpen(true);
  }

  function normalizeSupportedWeatherPlace(value: string) {
    const trimmedValue = value.trim();
    if (!trimmedValue) return null;

    return supportedWeatherPlaces.find((place) => place.toLowerCase() === trimmedValue.toLowerCase()) ?? null;
  }

  function formatMetric(value: number | null | undefined, unit: string) {
    return typeof value === "number" && Number.isFinite(value) ? `${value.toFixed(1)} ${unit}` : "-";
  }

  function getCurrentWeatherPoint(weather: ApiSiteWeatherForecast | null | undefined) {
    if (!weather?.points?.length) return null;

    const now = Date.now();
    return weather.points.reduce((closest, point) => {
      const pointDistance = Math.abs(new Date(point.time).getTime() - now);
      const closestDistance = Math.abs(new Date(closest.time).getTime() - now);
      return pointDistance < closestDistance ? point : closest;
    });
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) return;
    const normalizedWeatherPlace = normalizeSupportedWeatherPlace(weatherPlace);
    if (weatherPlace.trim() && !normalizedWeatherPlace) {
      setWeatherPlaceError(t("unsupportedWeatherPlace"));
      return;
    }

    const payload: SitePayload = {
      enabled,
      name: name.trim(),
      operationState,
      timezone,
      type,
      weatherPlace: normalizedWeatherPlace
    };

    setIsSaving(true);
    setError(null);
    setWeatherPlaceError(null);
    try {
      if (editingSiteId === null) {
        await createSite(payload);
      } else {
        await updateSite(editingSiteId, payload);
      }
      resetForm();
      setIsFormDialogOpen(false);
      await loadSites();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} translucent />
      <main className="app-page pt-4 sm:pt-12">
        <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">{t("title")}</h1>
            <p className="max-w-2xl text-lg text-on-surface-variant">{t("description")}</p>
          </div>

          <button
            className="primary-action transition-all duration-300 hover:-translate-y-0.5 hover:shadow-soft"
            onClick={openCreateDialog}
            type="button"
          >
            <span>+</span>
            {t("addNewSite")}
          </button>
        </section>

        {isLoading ? <div className="app-card p-4 text-sm text-on-surface-variant sm:p-6">{t("loading")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:p-6">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && sites.map((site) => (
            <article className={`group app-card border-l-4 ${site.enabled ? "border-primary" : "border-outline"} p-4 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft sm:p-6`} key={site.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-surface-container-lowest">{siteTypeLabel(site.type)}</span>
                <span className={`rounded px-2 py-1 text-[10px] font-bold ${site.enabled ? "bg-primary-fixed text-[#004342]" : "bg-error-container text-on-error-container"}`}>{site.enabled ? common("enabled") : common("disabled")}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold">{site.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">{common("id", { id: site.id })}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{t("operationState")}</span><p className="font-semibold">{t(site.operationState === "POWER_SAVE" ? "statePowerSave" : "stateNormal")}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{common("timezone")}</span><p className="font-semibold">{site.timezone ?? "-"}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{t("weatherPlace")}</span><p className="font-semibold">{site.weatherPlace ?? "-"}</p></div>
              </div>
              {(() => {
                const currentWeatherPoint = getCurrentWeatherPoint(siteWeather[site.id]);

                return (
                  <div className="mb-6 rounded-2xl border border-surface-container-low bg-surface-container-low/60 p-4">
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <p className="font-headline text-base font-bold">{t("currentWeather")}</p>
                      <span className="text-xs text-on-surface-variant">
                        {currentWeatherPoint ? formatDate(currentWeatherPoint.time, siteWeather[site.id]?.timezone || site.timezone) : t("weatherUnavailableShort")}
                      </span>
                    </div>
                    {currentWeatherPoint ? (
                      <div className="grid grid-cols-2 gap-3 text-sm">
                        <div className="rounded-lg bg-surface px-3 py-3"><span className="metric-label">{t("temperature")}</span><p className="font-semibold">{formatMetric(currentWeatherPoint.temperature, "°C")}</p></div>
                        <div className="rounded-lg bg-surface px-3 py-3"><span className="metric-label">{t("humidity")}</span><p className="font-semibold">{formatMetric(currentWeatherPoint.humidity, "%")}</p></div>
                      </div>
                    ) : (
                      <p className="text-sm text-on-surface-variant">{t("weatherUnavailable")}</p>
                    )}
                  </div>
                );
              })()}
              <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                <span className="text-sm text-on-surface-variant">{formatDate(site.updatedAt, site.timezone)}</span>
                <button className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" onClick={() => startEdit(site)} type="button">{common("edit")}</button>
              </div>
            </article>
          ))}

          <button
            className="group flex flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low p-4 text-center transition-all duration-300 hover:-translate-y-1 hover:border-primary hover:bg-surface-container-high hover:shadow-soft sm:p-6"
            onClick={openCreateDialog}
            type="button"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest font-headline text-xl font-black text-primary transition-all duration-300 group-hover:scale-110 group-hover:bg-surface-container-lowest">
              +
            </div>
            <div>
              <h3 className="font-headline text-lg font-bold text-on-surface">{t("createSite")}</h3>
              <p className="px-8 text-xs text-on-surface-variant">{t("createSiteDescription")}</p>
            </div>
          </button>
        </section>
      </main>

      <AppDialog
        description={editingSiteId === null ? t("createSiteDescription") : t("updateSiteDescription")}
        eyebrow={editingSiteId === null ? t("createSiteEyebrow") : t("updateSiteEyebrow")}
        isOpen={isFormDialogOpen}
        maxWidthClassName="max-w-2xl"
        onClose={() => {
          setIsFormDialogOpen(false);
          resetForm();
          setError(null);
        }}
        title={editingSiteId === null ? t("createSite") : t("update")}
      >
        <form className="grid gap-x-5 gap-y-5 sm:grid-cols-2" onSubmit={handleSubmit}>
          <div className="sm:col-span-2">
            <label className="mb-2 block text-sm font-semibold text-on-surface" htmlFor="site-name">{t("siteName")}</label>
            <input className="w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:ring-2 focus:ring-primary" id="site-name" onChange={(event) => setName(event.target.value)} required type="text" value={name} />
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-on-surface" htmlFor="site-type">{t("siteType")}</label>
            <select className="w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:ring-2 focus:ring-primary" id="site-type" onChange={(event) => setType(event.target.value as SiteType)} value={type}>
              {SITE_TYPES.map((item) => <option key={item} value={item}>{siteTypeLabel(item)}</option>)}
            </select>
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-on-surface" htmlFor="site-operation-state">{t("operationState")}</label>
            <select className="w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:ring-2 focus:ring-primary" id="site-operation-state" onChange={(event) => setOperationState(event.target.value as SiteOperationState)} value={operationState}>
              {SITE_OPERATION_STATES.map((state) => <option key={state} value={state}>{t(state === "POWER_SAVE" ? "statePowerSave" : "stateNormal")}</option>)}
            </select>
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-on-surface" htmlFor="site-weather-place">{t("weatherPlace")}</label>
            <input
              className="w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:ring-2 focus:ring-primary"
              id="site-weather-place"
              list="site-weather-place-options"
              onChange={(event) => {
                setWeatherPlace(event.target.value);
                if (weatherPlaceError) setWeatherPlaceError(null);
              }}
              type="text"
              value={weatherPlace}
            />
            <datalist id="site-weather-place-options">
              {supportedWeatherPlaces.map((place) => <option key={place} value={place} />)}
            </datalist>
            <p className="mt-2 text-xs text-on-surface-variant">{t("weatherPlaceHelp")}</p>
            {weatherPlaceError ? <p className="mt-1 text-xs text-error">{weatherPlaceError}</p> : null}
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-on-surface" htmlFor="site-timezone">{common("timezone")}</label>
            <select className="w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:ring-2 focus:ring-primary" id="site-timezone" onChange={(event) => setTimezone(event.target.value)} value={timezone}>
              {availableTimezones.map((zone) => <option key={zone} value={zone}>{zone}</option>)}
            </select>
          </div>
          <label className="flex items-center gap-3 rounded-lg bg-surface-container p-4 text-sm font-semibold text-on-surface sm:col-span-2"><input checked={enabled} className="h-4 w-4 accent-primary" onChange={(event) => setEnabled(event.target.checked)} type="checkbox" />{common("enabled")}</label>

          {error ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:col-span-2">
              {error}
            </div>
          ) : null}

          <div className="flex flex-col-reverse gap-3 sm:col-span-2 sm:flex-row sm:justify-end">
            <button
              className="secondary-action justify-center"
              onClick={() => {
                setIsFormDialogOpen(false);
                resetForm();
                setError(null);
              }}
              type="button"
            >
              {common("cancel")}
            </button>
            <button className="primary-action justify-center disabled:opacity-60" disabled={isSaving || !name.trim()} type="submit">
              {isSaving ? (editingSiteId === null ? common("creating") : common("save")) : editingSiteId === null ? t("add") : t("update")}
            </button>
          </div>
        </form>
      </AppDialog>

      <button
        className="signature-gradient fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full text-3xl text-on-primary shadow-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_40px_rgba(0,67,66,0.22)] active:scale-90 md:hidden"
        onClick={openCreateDialog}
        type="button"
      >
        +
      </button>
    </>
  );
}

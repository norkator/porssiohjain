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
  createSite,
  fetchSites,
  formatDate,
  SITE_TYPES,
  updateSite,
  type ApiSite,
  type SitePayload,
  type SiteType
} from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";

const DEFAULT_TIMEZONE = "Europe/Helsinki";

function siteTypeLabel(type: string) {
  return type.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char) => char.toUpperCase());
}

export default function SitesView() {
  const { t } = useI18n("sitesView");
  const common = useI18n("common").t;
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingSiteId, setEditingSiteId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [type, setType] = useState<SiteType>("HOME");
  const [weatherPlace, setWeatherPlace] = useState("");
  const [timezone, setTimezone] = useState(DEFAULT_TIMEZONE);
  const [enabled, setEnabled] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  async function loadSites() {
    setIsLoading(true);
    setError(null);
    try {
      setSites(await fetchSites());
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
    setWeatherPlace("");
    setTimezone(DEFAULT_TIMEZONE);
    setEnabled(true);
  }

  function startEdit(site: ApiSite) {
    setEditingSiteId(site.id);
    setName(site.name);
    setType(site.type as SiteType);
    setWeatherPlace(site.weatherPlace ?? "");
    setTimezone(site.timezone ?? DEFAULT_TIMEZONE);
    setEnabled(site.enabled);
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) return;

    const payload: SitePayload = {
      enabled,
      name: name.trim(),
      timezone: timezone.trim() || DEFAULT_TIMEZONE,
      type,
      weatherPlace: weatherPlace.trim() || null
    };

    setIsSaving(true);
    setError(null);
    try {
      if (editingSiteId === null) {
        await createSite(payload);
      } else {
        await updateSite(editingSiteId, payload);
      }
      resetForm();
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
        <section className="mb-10">
          <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">{t("title")}</h1>
          <p className="max-w-2xl text-lg text-on-surface-variant">{t("description")}</p>
        </section>

        <form className="app-card mb-8 grid gap-4 p-6 md:grid-cols-2 lg:grid-cols-5" onSubmit={handleSubmit}>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} placeholder={t("siteName")} value={name} />
          <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setType(event.target.value as SiteType)} value={type}>
            {SITE_TYPES.map((item) => <option key={item} value={item}>{siteTypeLabel(item)}</option>)}
          </select>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setWeatherPlace(event.target.value)} placeholder={t("weatherPlace")} value={weatherPlace} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setTimezone(event.target.value)} placeholder={common("timezone")} value={timezone} />
          <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">{common("enabled")}</span><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" /></label>
          <button className="primary-action justify-center disabled:opacity-60 lg:col-span-4" disabled={isSaving || !name.trim()} type="submit">{isSaving ? (editingSiteId === null ? common("creating") : common("save")) : editingSiteId === null ? t("add") : t("update")}</button>
          {editingSiteId !== null ? <button className="secondary-action justify-center lg:col-span-1" onClick={resetForm} type="button">{common("cancel")}</button> : null}
        </form>

        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">{t("loading")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && sites.map((site) => (
            <article className={`group app-card border-l-4 ${site.enabled ? "border-primary" : "border-outline"} p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft`} key={site.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">{siteTypeLabel(site.type)}</span>
                <span className={`rounded px-2 py-1 text-[10px] font-bold ${site.enabled ? "bg-primary-fixed text-primary" : "bg-error-container text-on-error-container"}`}>{site.enabled ? common("enabled") : common("disabled")}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold">{site.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">{common("id", { id: site.id })}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{common("timezone")}</span><p className="font-semibold">{site.timezone ?? "-"}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{t("weatherPlace")}</span><p className="font-semibold">{site.weatherPlace ?? "-"}</p></div>
              </div>
              <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                <span className="text-sm text-on-surface-variant">{formatDate(site.updatedAt, site.timezone)}</span>
                <button className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" onClick={() => startEdit(site)} type="button">{common("edit")}</button>
              </div>
            </article>
          ))}
        </section>
      </main>
    </>
  );
}

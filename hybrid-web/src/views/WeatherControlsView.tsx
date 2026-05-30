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
import {
  createWeatherControl,
  fetchSites,
  fetchWeatherControls,
  formatDate,
  type ApiSite,
  type ApiWeatherControl
} from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

export default function WeatherControlsView() {
  const { t } = useI18n("weatherControls");
  const common = useI18n("common").t;
  const [controls, setControls] = useState<ApiWeatherControl[]>([]);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [siteId, setSiteId] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const latestUpdate = useMemo(() => [...controls].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))[0]?.updatedAt, [controls]);

  const resetForm = () => {
    setName("");
    setSiteId("");
    setCreateError(null);
  };

  async function loadData() {
    setIsLoading(true);
    setLoadError(null);
    try {
      const [controlResponse, siteResponse] = await Promise.all([fetchWeatherControls(), fetchSites()]);
      setControls(controlResponse);
      setSites(siteResponse);
    } catch (loadError) {
      setLoadError(loadError instanceof Error ? loadError.message : t("failedLoad"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedSiteId = Number(siteId);
    if (!name.trim() || !Number.isFinite(parsedSiteId)) return;
    setIsCreating(true);
    setCreateError(null);
    try {
      await createWeatherControl({ name: name.trim(), siteId: parsedSiteId });
      resetForm();
      setIsCreateDialogOpen(false);
      await loadData();
    } catch (createError) {
      setCreateError(createError instanceof Error ? createError.message : t("failedCreate"));
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} translucent />
      <main className="app-page pt-4 sm:pt-12">
        <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">{t("title")}</h1>
            <p className="max-w-lg text-lg text-on-surface-variant">{t("description")}</p>
          </div>

          <button
            className="primary-action transition-all duration-300 hover:-translate-y-0.5 hover:shadow-soft"
            onClick={() => {
              setCreateError(null);
              setIsCreateDialogOpen(true);
            }}
            type="button"
          >
            <span>+</span>
            {t("addNewWeatherControl")}
          </button>
        </section>

        {isLoading ? <div className="app-card p-4 text-sm text-on-surface-variant sm:p-6">{t("loading")}</div> : null}
        {loadError ? <div className="app-card border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:p-6">{loadError}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && controls.map((control) => (
            <article className="group app-card border-l-4 border-secondary p-4 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft sm:p-6" key={control.id}>
              <div className="mb-5 flex items-start justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-surface-container-lowest">{control.siteName ?? t("noSite")}</span>
                <span className="rounded bg-primary-fixed px-2 py-1 text-[10px] font-bold text-[#004342]">{control.shared ? common("shared") : common("active")}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold text-on-surface">{control.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">{common("id", { id: control.id })}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{common("timezone")}</span><p className="font-semibold">{control.siteTimezone ?? "-"}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{common("updated")}</span><p className="font-semibold">{formatDate(control.updatedAt, control.siteTimezone)}</p></div>
              </div>
              <Link className="secondary-action justify-center rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" to={`/weather-controls/${control.id}`}>{common("manage")}</Link>
            </article>
          ))}

          <button
            className="group flex flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low p-4 text-center transition-all duration-300 hover:-translate-y-1 hover:border-primary hover:bg-surface-container-high hover:shadow-soft sm:p-6"
            onClick={() => {
              setCreateError(null);
              setIsCreateDialogOpen(true);
            }}
            type="button"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest font-headline text-xl font-black text-primary transition-all duration-300 group-hover:scale-110 group-hover:bg-surface-container-lowest">
              +
            </div>
            <div>
              <h3 className="font-headline text-lg font-bold text-on-surface">{t("createWeatherControl")}</h3>
              <p className="px-8 text-xs text-on-surface-variant">{t("createWeatherControlDescription")}</p>
            </div>
          </button>
        </section>

        <section className="mt-12 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="rounded-2xl bg-surface-container p-4 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high sm:p-6"><p className="font-bold">{t("configured")}</p><p className="text-sm text-on-surface-variant">{t("configuredCount", { count: controls.length })}</p></div>
          <div className="rounded-2xl bg-surface-container p-4 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high sm:p-6"><p className="font-bold">{t("sites")}</p><p className="text-sm text-on-surface-variant">{t("sitesCount", { count: sites.length })}</p></div>
          <div className="rounded-2xl bg-surface-container p-4 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high sm:p-6"><p className="font-bold">{t("lastUpdate")}</p><p className="text-sm text-on-surface-variant">{latestUpdate ? formatDate(latestUpdate) : t("noChanges")}</p></div>
        </section>
      </main>

      <AppDialog
        description={t("description")}
        eyebrow={t("createWeatherControlEyebrow")}
        isOpen={isCreateDialogOpen}
        onClose={() => {
          setIsCreateDialogOpen(false);
          resetForm();
        }}
        title={t("createWeatherControl")}
      >
        <form className="grid gap-4 md:grid-cols-2" onSubmit={handleCreate}>
          <input
            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary"
            onChange={(event) => setName(event.target.value)}
            placeholder={t("controlName")}
            value={name}
          />
          <select
            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary"
            onChange={(event) => setSiteId(event.target.value)}
            value={siteId}
          >
            <option value="">{t("selectSite")}</option>
            {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
          </select>

          {createError ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container md:col-span-2">
              {createError}
            </div>
          ) : null}

          <div className="flex flex-col-reverse gap-3 md:col-span-2 sm:flex-row sm:justify-end">
            <button
              className="secondary-action justify-center"
              onClick={() => {
                setIsCreateDialogOpen(false);
                resetForm();
              }}
              type="button"
            >
              {common("cancel")}
            </button>
            <button className="primary-action justify-center disabled:opacity-60" disabled={isCreating || !name.trim() || !siteId} type="submit">
              {isCreating ? common("creating") : t("add")}
            </button>
          </div>
        </form>
      </AppDialog>

      <button
        className="signature-gradient fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full text-3xl text-on-primary shadow-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_40px_rgba(0,67,66,0.22)] active:scale-90 md:hidden"
        onClick={() => {
          setCreateError(null);
          setIsCreateDialogOpen(true);
        }}
        type="button"
      >
        +
      </button>
    </>
  );
}

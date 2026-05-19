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
import { createPowerLimit, fetchPowerLimits, formatDate, formatKw, type ApiPowerLimit } from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";

export default function PowerLimitsView() {
  const { t } = useI18n("powerLimits");
  const common = useI18n("common").t;
  const [limits, setLimits] = useState<ApiPowerLimit[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [limitKw, setLimitKw] = useState("5");
  const [enabled, setEnabled] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

  const resetForm = () => {
    setName("");
    setLimitKw("5");
    setEnabled(true);
    setCreateError(null);
  };

  async function loadLimits() {
    setIsLoading(true);
    setLoadError(null);
    try {
      setLimits(await fetchPowerLimits());
    } catch (loadError) {
      setLoadError(loadError instanceof Error ? loadError.message : t("failedLoad"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadLimits();
  }, []);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedLimit = Number(limitKw);
    if (!name.trim() || !Number.isFinite(parsedLimit)) return;
    setIsCreating(true);
    setCreateError(null);
    try {
      await createPowerLimit({ enabled, limitKw: parsedLimit, name: name.trim() });
      resetForm();
      setIsCreateDialogOpen(false);
      await loadLimits();
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
            {t("addNewPowerLimit")}
          </button>
        </section>

        {isLoading ? <div className="app-card p-4 text-sm text-on-surface-variant sm:p-6">{t("loading")}</div> : null}
        {loadError ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:p-6">{loadError}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && limits.map((limit) => (
            <article className={`group app-card border-l-4 ${limit.enabled ? "border-primary" : "border-outline"} p-4 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft sm:p-6`} key={limit.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-surface-container-lowest">{limit.limitIntervalMinutes ?? 15} min</span>
                <span className={`rounded px-2 py-1 text-[10px] font-bold ${limit.enabled ? "bg-primary-fixed text-primary" : "bg-error-container text-on-error-container"}`}>{limit.enabled ? common("enabled") : common("disabled")}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold">{limit.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">{common("id", { id: limit.id })}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{t("limitKw")}</span><p className="font-semibold">{formatKw(limit.limitKw)}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">{common("currentKw")}</span><p className="font-semibold">{formatKw(limit.currentKw)}</p></div>
              </div>
              <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                <span className="text-sm text-on-surface-variant">{formatDate(limit.updatedAt, limit.timezone)}</span>
                <Link className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" to={`/power-limits/${limit.id}`}>{common("manage")}</Link>
              </div>
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
              <h3 className="font-headline text-lg font-bold text-on-surface">{t("createPowerLimit")}</h3>
              <p className="px-8 text-xs text-on-surface-variant">{t("createPowerLimitDescription")}</p>
            </div>
          </button>
        </section>
      </main>

      <AppDialog
        description={t("description")}
        eyebrow={t("createPowerLimitEyebrow")}
        isOpen={isCreateDialogOpen}
        onClose={() => {
          setIsCreateDialogOpen(false);
          resetForm();
        }}
        title={t("createPowerLimit")}
      >
        <form className="grid gap-4 md:grid-cols-3" onSubmit={handleCreate}>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none md:col-span-3" onChange={(event) => setName(event.target.value)} placeholder={t("limitName")} value={name} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" min="0" onChange={(event) => setLimitKw(event.target.value)} step="0.1" type="number" value={limitKw} />
          <label className="flex items-center justify-between rounded-xl bg-surface-container p-4 md:col-span-2">
            <span className="font-headline text-sm font-bold">{common("enabled")}</span>
            <input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" />
          </label>

          {createError ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container md:col-span-3">
              {createError}
            </div>
          ) : null}

          <div className="flex flex-col-reverse gap-3 md:col-span-3 sm:flex-row sm:justify-end">
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
            <button className="primary-action justify-center disabled:opacity-60" disabled={isCreating || !name.trim()} type="submit">
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

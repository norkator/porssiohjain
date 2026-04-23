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
import { createPowerLimit, fetchPowerLimits, formatDate, formatKw, type ApiPowerLimit } from "@/lib/automation-resources";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";

export default function PowerLimitsView() {
  const [limits, setLimits] = useState<ApiPowerLimit[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [limitKw, setLimitKw] = useState("5");
  const [enabled, setEnabled] = useState(true);
  const [isCreating, setIsCreating] = useState(false);

  async function loadLimits() {
    setIsLoading(true);
    setError(null);
    try {
      setLimits(await fetchPowerLimits());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load power limits");
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
    setError(null);
    try {
      await createPowerLimit({ enabled, limitKw: parsedLimit, name: name.trim() });
      setName("");
      setLimitKw("5");
      setEnabled(true);
      await loadLimits();
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Failed to create power limit");
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">Menu</Link>} translucent />
      <main className="app-page pt-12">
        <section className="mb-10">
          <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">Power Limits</h1>
          <p className="max-w-lg text-lg text-on-surface-variant">Keep total consumption under configured kilowatt limits.</p>
        </section>

        <form className="app-card mb-8 grid gap-4 p-6 md:grid-cols-4" onSubmit={handleCreate}>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} placeholder="Limit name" value={name} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" min="0" onChange={(event) => setLimitKw(event.target.value)} step="0.1" type="number" value={limitKw} />
          <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">Enabled</span><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" /></label>
          <button className="primary-action justify-center disabled:opacity-60" disabled={isCreating || !name.trim()} type="submit">{isCreating ? "Creating..." : "Add Power Limit"}</button>
        </form>

        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">Loading power limits...</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && limits.map((limit) => (
            <article className={`group app-card border-l-4 ${limit.enabled ? "border-primary" : "border-outline"} p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft`} key={limit.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">{limit.limitIntervalMinutes ?? 15} min</span>
                <span className={`rounded px-2 py-1 text-[10px] font-bold ${limit.enabled ? "bg-primary-fixed text-primary" : "bg-error-container text-on-error-container"}`}>{limit.enabled ? "Enabled" : "Disabled"}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold">{limit.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">ID: {limit.id}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">Limit kW</span><p className="font-semibold">{formatKw(limit.limitKw)}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">Current kW</span><p className="font-semibold">{formatKw(limit.currentKw)}</p></div>
              </div>
              <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                <span className="text-sm text-on-surface-variant">{formatDate(limit.updatedAt, limit.timezone)}</span>
                <Link className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" to={`/power-limits/${limit.id}`}>Manage</Link>
              </div>
            </article>
          ))}
        </section>
      </main>
    </>
  );
}

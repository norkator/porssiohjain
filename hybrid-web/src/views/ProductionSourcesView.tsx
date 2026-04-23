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
  createProductionSource,
  fetchProductionSources,
  formatDate,
  formatKw,
  PRODUCTION_API_TYPES,
  type ApiProductionSource,
  type ProductionApiType
} from "@/lib/automation-resources";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";

const label = (value: string) => value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char: string) => char.toUpperCase());

export default function ProductionSourcesView() {
  const [sources, setSources] = useState<ApiProductionSource[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [apiType, setApiType] = useState<ProductionApiType>("SHELLY");
  const [appId, setAppId] = useState("");
  const [appSecret, setAppSecret] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [stationId, setStationId] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [isCreating, setIsCreating] = useState(false);

  async function loadSources() {
    setIsLoading(true);
    setError(null);
    try {
      setSources(await fetchProductionSources());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load production sources");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadSources();
  }, []);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) return;
    setIsCreating(true);
    setError(null);
    try {
      await createProductionSource({
        apiType,
        appId: appId.trim() || null,
        appSecret: appSecret.trim() || null,
        email: email.trim() || null,
        enabled,
        name: name.trim(),
        password: password.trim() || null,
        stationId: stationId.trim() || null
      });
      setName("");
      setAppId("");
      setAppSecret("");
      setEmail("");
      setPassword("");
      setStationId("");
      setEnabled(true);
      await loadSources();
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Failed to create production source");
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">Menu</Link>} translucent />
      <main className="app-page pt-12">
        <section className="mb-10">
          <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">Own Production</h1>
          <p className="max-w-lg text-lg text-on-surface-variant">Track local generation and switch loads by production level.</p>
        </section>

        <form className="app-card mb-8 grid gap-4 p-6 md:grid-cols-2 lg:grid-cols-4" onSubmit={handleCreate}>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} placeholder="Source name" value={name} />
          <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setApiType(event.target.value as ProductionApiType)} value={apiType}>{PRODUCTION_API_TYPES.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setAppId(event.target.value)} placeholder="App ID" value={appId} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setAppSecret(event.target.value)} placeholder="App secret" type="password" value={appSecret} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setEmail(event.target.value)} placeholder="Email" type="email" value={email} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setPassword(event.target.value)} placeholder="Password" type="password" value={password} />
          <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setStationId(event.target.value)} placeholder="Station ID" value={stationId} />
          <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">Enabled</span><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" /></label>
          <button className="primary-action justify-center disabled:opacity-60 lg:col-span-4" disabled={isCreating || !name.trim()} type="submit">{isCreating ? "Creating..." : "Add Production Source"}</button>
        </form>

        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">Loading production sources...</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && sources.map((source) => (
            <article className={`group app-card border-l-4 ${source.enabled ? "border-primary" : "border-outline"} p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft`} key={source.id}>
              <div className="mb-5 flex justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">{label(source.apiType)}</span>
                <span className={`rounded px-2 py-1 text-[10px] font-bold ${source.enabled ? "bg-primary-fixed text-primary" : "bg-error-container text-on-error-container"}`}>{source.enabled ? "Enabled" : "Disabled"}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold">{source.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">ID: {source.id}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">Current kW</span><p className="font-semibold">{formatKw(source.currentKw)}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">Peak kW</span><p className="font-semibold">{formatKw(source.peakKw)}</p></div>
              </div>
              <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                <span className="text-sm text-on-surface-variant">{formatDate(source.updatedAt, source.timezone)}</span>
                <Link className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" to={`/production-sources/${source.id}`}>Manage</Link>
              </div>
            </article>
          ))}
        </section>
      </main>
    </>
  );
}

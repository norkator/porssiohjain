import PageHeader from "@/components/PageHeader";
import {
  createWeatherControl,
  fetchSites,
  fetchWeatherControls,
  formatDate,
  type ApiSite,
  type ApiWeatherControl
} from "@/lib/automation-resources";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

export default function WeatherControlsView() {
  const [controls, setControls] = useState<ApiWeatherControl[]>([]);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [siteId, setSiteId] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const latestUpdate = useMemo(() => [...controls].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))[0]?.updatedAt, [controls]);

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [controlResponse, siteResponse] = await Promise.all([fetchWeatherControls(), fetchSites()]);
      setControls(controlResponse);
      setSites(siteResponse);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load weather controls");
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
    setError(null);
    try {
      await createWeatherControl({ name: name.trim(), siteId: parsedSiteId });
      setName("");
      setSiteId("");
      await loadData();
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Failed to create weather control");
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">Menu</Link>} translucent />
      <main className="app-page pt-12">
        <section className="mb-10">
          <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">Weather Controls</h1>
          <p className="max-w-lg text-lg text-on-surface-variant">Switch devices from site weather thresholds.</p>
        </section>

        <form className="app-card mb-8 grid gap-4 p-6 md:grid-cols-3" onSubmit={handleCreate}>
          <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary" onChange={(event) => setName(event.target.value)} placeholder="Control name" value={name} />
          <select className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary" onChange={(event) => setSiteId(event.target.value)} value={siteId}>
            <option value="">Select site</option>
            {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
          </select>
          <button className="primary-action justify-center disabled:opacity-60" disabled={isCreating || !name.trim() || !siteId} type="submit">{isCreating ? "Creating..." : "Add Weather Control"}</button>
        </form>

        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">Loading weather controls...</div> : null}
        {error ? <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {!isLoading && controls.map((control) => (
            <article className="group app-card border-l-4 border-secondary p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft" key={control.id}>
              <div className="mb-5 flex items-start justify-between gap-3">
                <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">{control.siteName ?? "No site"}</span>
                <span className="rounded bg-primary-fixed px-2 py-1 text-[10px] font-bold text-primary">{control.shared ? "Shared" : "Active"}</span>
              </div>
              <h3 className="font-headline text-2xl font-bold text-on-surface">{control.name}</h3>
              <p className="mb-6 mt-1 font-mono text-xs text-outline">ID: {control.id}</p>
              <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">Timezone</span><p className="font-semibold">{control.siteTimezone ?? "-"}</p></div>
                <div className="rounded-lg bg-surface-container-low p-3"><span className="metric-label">Updated</span><p className="font-semibold">{formatDate(control.updatedAt, control.siteTimezone)}</p></div>
              </div>
              <Link className="secondary-action justify-center rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" to={`/weather-controls/${control.id}`}>Manage</Link>
            </article>
          ))}
        </section>

        <section className="mt-12 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high"><p className="font-bold">Configured</p><p className="text-sm text-on-surface-variant">{controls.length} weather controls</p></div>
          <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high"><p className="font-bold">Sites</p><p className="text-sm text-on-surface-variant">{sites.length} available sites</p></div>
          <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high"><p className="font-bold">Last Update</p><p className="text-sm text-on-surface-variant">{latestUpdate ? formatDate(latestUpdate) : "No changes yet"}</p></div>
        </section>
      </main>
    </>
  );
}

import PageHeader from "@/components/PageHeader";
import { useControls } from "@/hooks/useControls";
import {
  formatControlDate,
  formatControlMode,
  getControlAccent,
  getControlStatus
} from "@/lib/controls";
import { Link } from "react-router-dom";

export default function ControlsView() {
  const { controls, error, isLoading, latestUpdate, manualCount, sharedCount, totalCount } = useControls();

  return (
    <>
      <PageHeader translucent />

      <main className="app-page pt-12">
        <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
              Your Controls
            </h1>
            <p className="max-w-lg text-lg text-on-surface-variant">
              Manage price limits, cheapest-hour schedules, manual overrides, and shared control resources.
            </p>
          </div>

          <Link className="primary-action transition-all duration-300 hover:-translate-y-0.5 hover:shadow-soft" to="/controls/add">
            <span>+</span>
            Add New Control
          </Link>
        </section>

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {isLoading ? (
            <div className="app-card p-6 text-sm text-on-surface-variant">Loading controls...</div>
          ) : null}

          {!isLoading && error ? (
            <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
              Failed to load controls. {error}
            </div>
          ) : null}

          {!isLoading && !error
            ? controls.map((control) => {
                const status = getControlStatus(control);

                return (
                  <article
                    className={`group app-card border-l-4 ${getControlAccent(control)} p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft`}
                    key={control.id}
                  >
                    <div className="mb-6 flex items-start justify-between gap-4">
                      <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">
                        {formatControlMode(control.mode)}
                      </span>
                      <span
                        className={`inline-flex items-center gap-1 rounded px-2 py-1 text-[10px] font-bold ${
                          status.tone === "online"
                            ? "bg-primary-fixed text-primary"
                            : "bg-error-container text-on-error-container"
                        }`}
                      >
                        <span className={`h-1.5 w-1.5 rounded-full ${status.tone === "online" ? "bg-primary" : "bg-red-500"}`} />
                        {status.label}
                      </span>
                    </div>

                    <h3 className="font-headline text-2xl font-bold text-on-surface">{control.name}</h3>
                    <p className="mb-6 mt-1 font-mono text-xs tracking-tight text-outline">ID: {control.id}</p>

                    <div className="mb-6 grid grid-cols-2 gap-3 text-sm">
                      <div className="rounded-lg bg-surface-container-low p-3">
                        <span className="metric-label">Max Price</span>
                        <p className="font-semibold text-on-surface">{control.maxPriceSnt ?? "-"} snt</p>
                      </div>
                      <div className="rounded-lg bg-surface-container-low p-3">
                        <span className="metric-label">Daily On</span>
                        <p className="font-semibold text-on-surface">{control.dailyOnMinutes ?? 0} min</p>
                      </div>
                    </div>

                    <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
                      <div className="flex flex-col">
                        <span className="metric-label">Updated</span>
                        <span className="text-sm font-semibold text-on-surface">{formatControlDate(control.updatedAt, control.timezone)}</span>
                      </div>
                      <Link className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5" to={`/controls/${control.id}`}>
                        Manage
                      </Link>
                    </div>
                  </article>
                );
              })
            : null}

          <Link
            className="group flex flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low p-6 text-center transition-all duration-300 hover:-translate-y-1 hover:border-primary hover:bg-surface-container-high hover:shadow-soft"
            to="/controls/add"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest font-headline text-xl font-black text-primary transition-all duration-300 group-hover:scale-110 group-hover:bg-white">
              +
            </div>
            <div>
              <h3 className="font-headline text-lg font-bold text-on-surface">Create Control</h3>
              <p className="px-8 text-xs text-on-surface-variant">Add price and schedule logic for connected loads</p>
            </div>
          </Link>
        </section>

        <section className="mt-20 grid grid-cols-1 gap-8 items-center md:grid-cols-12">
          <div className="group relative overflow-hidden rounded-3xl bg-primary-container p-6 shadow-2xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_24px_48px_rgba(0,67,66,0.22)] md:col-span-4">
            <div className="absolute inset-0 bg-gradient-to-t from-primary/60 to-transparent" />
            <div className="relative text-white">
              <p className="text-xs font-semibold uppercase tracking-widest opacity-80">Controls Configured</p>
              <p className="font-headline text-4xl font-black">{totalCount}</p>
            </div>
          </div>

          <div className="flex flex-col gap-6 md:col-span-8">
            <h3 className="font-headline text-3xl font-bold text-primary">Price-Aware Automation.</h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">Manual Controls</p>
                <p className="text-sm text-on-surface-variant">{isLoading ? "Syncing..." : `${manualCount} manual overrides configured.`}</p>
              </div>
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">Shared Resources</p>
                <p className="text-sm text-on-surface-variant">{isLoading ? "Syncing..." : `${sharedCount} controls shared to this account.`}</p>
              </div>
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">Last Update</p>
                <p className="text-sm text-on-surface-variant">
                  {latestUpdate ? formatControlDate(latestUpdate) : "No control changes reported yet."}
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      <Link
        className="signature-gradient fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full text-3xl text-on-primary shadow-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_40px_rgba(0,67,66,0.22)] active:scale-90 md:hidden"
        to="/controls/add"
      >
        +
      </Link>
    </>
  );
}

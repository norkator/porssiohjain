import PageHeader from "@/components/PageHeader";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import {
  CONTROL_MODES,
  deleteControl,
  fetchControl,
  formatControlDate,
  formatControlMode,
  type ApiControl,
  type ControlMode,
  type ControlPayload,
  updateControl
} from "@/lib/controls";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

function toInputNumber(value: number | null | undefined, fallback: string) {
  return value === null || value === undefined ? fallback : String(value);
}

function toNumber(value: string, fallback = 0) {
  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : fallback;
}

export default function ManageControlView() {
  const navigate = useNavigate();
  const params = useParams();
  const controlId = Number(params.controlId);
  const availableTimezones = useMemo(() => getAvailableTimezones(), []);
  const [control, setControl] = useState<ApiControl | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [timezone, setTimezone] = useState("");
  const [maxPriceSnt, setMaxPriceSnt] = useState("100");
  const [minPriceSnt, setMinPriceSnt] = useState("0");
  const [dailyOnMinutes, setDailyOnMinutes] = useState("60");
  const [taxPercent, setTaxPercent] = useState("25.5");
  const [mode, setMode] = useState<ControlMode>("BELOW_MAX_PRICE");
  const [manualOn, setManualOn] = useState(false);
  const [alwaysOnBelowMinPrice, setAlwaysOnBelowMinPrice] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const timezoneIsValid = availableTimezones.includes(timezone);
  const canSave = name.trim().length > 0 && timezoneIsValid && !control?.shared && !isSaving && !isDeleting;

  useEffect(() => {
    let isActive = true;

    async function loadControl() {
      try {
        const response = await fetchControl(controlId);

        if (!isActive) {
          return;
        }

        setControl(response);
        setName(response.name);
        setTimezone(response.timezone);
        setMaxPriceSnt(toInputNumber(response.maxPriceSnt, "100"));
        setMinPriceSnt(toInputNumber(response.minPriceSnt, "0"));
        setDailyOnMinutes(toInputNumber(response.dailyOnMinutes, "60"));
        setTaxPercent(toInputNumber(response.taxPercent, "25.5"));
        setMode(response.mode);
        setManualOn(Boolean(response.manualOn));
        setAlwaysOnBelowMinPrice(Boolean(response.alwaysOnBelowMinPrice));
        setIsLoading(false);
        setLoadError(null);
      } catch (error) {
        if (!isActive) {
          return;
        }

        setIsLoading(false);
        setLoadError(error instanceof Error ? error.message : "Failed to load control");
      }
    }

    if (Number.isFinite(controlId)) {
      loadControl();
    }

    return () => {
      isActive = false;
    };
  }, [controlId]);

  if (!Number.isFinite(controlId)) {
    return <Navigate replace to="/controls" />;
  }

  const buildPayload = (): ControlPayload => ({
    alwaysOnBelowMinPrice,
    dailyOnMinutes: Math.max(0, Math.round(toNumber(dailyOnMinutes))),
    manualOn: mode === "MANUAL" ? manualOn : false,
    maxPriceSnt: Math.max(0, toNumber(maxPriceSnt)),
    minPriceSnt: Math.max(0, toNumber(minPriceSnt)),
    mode,
    name: name.trim(),
    taxPercent: Math.max(0, toNumber(taxPercent)),
    timezone
  });

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canSave) {
      return;
    }

    setIsSaving(true);
    setSaveError(null);
    setSaveMessage(null);

    try {
      const response = await updateControl(controlId, buildPayload());

      setControl(response);
      setSaveMessage("Control saved.");
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Failed to save control");
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setSaveError(null);
    setSaveMessage(null);

    try {
      await deleteControl(controlId);
      navigate("/controls");
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Failed to delete control");
      setIsDeleting(false);
    }
  };

  return (
    <>
      <PageHeader title="Manage Control" compact />

      <main className="app-page py-8">
        {isLoading ? (
          <div className="app-card p-6 text-sm text-on-surface-variant">Loading control...</div>
        ) : null}

        {!isLoading && loadError ? (
          <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
            Failed to load control. {loadError}
          </div>
        ) : null}

        {!isLoading && !loadError ? (
          <div className="grid gap-12 items-start lg:grid-cols-12">
            <section className="space-y-8 lg:col-span-8">
              <div>
                <p className="metric-label mb-3">Control #{controlId}</p>
                <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
                  {control?.name ?? "Control"}
                </h1>
                <p className="max-w-xl text-lg text-on-surface-variant">
                  Tune scheduler thresholds, mode selection, and override behavior for this control.
                </p>
              </div>

              <form className="app-card space-y-8 p-8" onSubmit={handleSubmit}>
                <div className="grid gap-6 md:grid-cols-2">
                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="control-name">
                      Control Name
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="control-name"
                      onChange={(event) => setName(event.target.value)}
                      type="text"
                      value={name}
                    />
                  </div>

                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="timezone">
                      Timezone
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 disabled:opacity-70"
                      disabled
                      id="timezone"
                      type="text"
                      value={timezone}
                    />
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="max-price">
                      Max Price snt
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="max-price"
                      min="0"
                      onChange={(event) => setMaxPriceSnt(event.target.value)}
                      step="0.1"
                      type="number"
                      value={maxPriceSnt}
                    />
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="min-price">
                      Min Price snt
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="min-price"
                      min="0"
                      onChange={(event) => setMinPriceSnt(event.target.value)}
                      step="0.1"
                      type="number"
                      value={minPriceSnt}
                    />
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="daily-minutes">
                      Daily On Minutes
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="daily-minutes"
                      min="0"
                      onChange={(event) => setDailyOnMinutes(event.target.value)}
                      step="1"
                      type="number"
                      value={dailyOnMinutes}
                    />
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="tax-percent">
                      Tax Percent
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="tax-percent"
                      min="0"
                      onChange={(event) => setTaxPercent(event.target.value)}
                      step="0.1"
                      type="number"
                      value={taxPercent}
                    />
                  </div>

                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="mode">
                      Mode
                    </label>
                    <select
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="mode"
                      onChange={(event) => {
                        const nextMode = event.target.value as ControlMode;
                        setMode(nextMode);
                        if (nextMode !== "MANUAL") {
                          setManualOn(false);
                        }
                      }}
                      value={mode}
                    >
                      {CONTROL_MODES.map((option) => (
                        <option key={option} value={option}>{formatControlMode(option)}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <label className={`flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4 ${mode === "MANUAL" ? "" : "opacity-50"}`}>
                    <span className="font-headline text-sm font-bold text-on-surface">Manual On</span>
                    <input
                      checked={manualOn}
                      disabled={mode !== "MANUAL"}
                      onChange={(event) => setManualOn(event.target.checked)}
                      type="checkbox"
                    />
                  </label>
                  <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4">
                    <span className="font-headline text-sm font-bold text-on-surface">Always On Below Min Price</span>
                    <input
                      checked={alwaysOnBelowMinPrice}
                      onChange={(event) => setAlwaysOnBelowMinPrice(event.target.checked)}
                      type="checkbox"
                    />
                  </label>
                </div>

                <div className="flex flex-col gap-4 sm:flex-row">
                  {!control?.shared ? (
                    <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canSave} type="submit">
                      {isSaving ? "Saving..." : "Save Control"}
                    </button>
                  ) : null}
                  <Link className="secondary-action justify-center" to="/controls">
                    Back
                  </Link>
                </div>

                {saveMessage ? (
                  <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary">{saveMessage}</div>
                ) : null}

                {saveError ? (
                  <div className="rounded-xl border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
                    {saveError}
                  </div>
                ) : null}
              </form>
            </section>

            <aside className="space-y-6 lg:col-span-4">
              <div className="app-card p-6">
                <p className="metric-label mb-2">Origin</p>
                <p className="font-headline text-2xl font-bold text-on-surface">{control?.shared ? "Shared" : "Mine"}</p>
              </div>
              <div className="app-card p-6">
                <p className="metric-label mb-2">Created</p>
                <p className="font-semibold text-on-surface">{formatControlDate(control?.createdAt, timezone)}</p>
              </div>
              <div className="app-card p-6">
                <p className="metric-label mb-2">Updated</p>
                <p className="font-semibold text-on-surface">{formatControlDate(control?.updatedAt, timezone)}</p>
              </div>
              <div className="app-card p-6">
                <p className="mb-4 font-headline text-lg font-bold text-on-surface">Linked Devices</p>
                <p className="text-sm text-on-surface-variant">Device and heat pump linking can be added here using the `/api/controls/{controlId}/links` endpoints.</p>
              </div>
              {!control?.shared ? (
                <button
                  className="w-full rounded-xl bg-error-container px-5 py-4 font-headline font-bold text-on-error-container transition-all hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={isDeleting || isSaving}
                  onClick={handleDelete}
                  type="button"
                >
                  {isDeleting ? "Deleting..." : "Delete Control"}
                </button>
              ) : null}
            </aside>
          </div>
        ) : null}
      </main>
    </>
  );
}

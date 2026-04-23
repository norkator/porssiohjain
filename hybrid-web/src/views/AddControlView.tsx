import PageHeader from "@/components/PageHeader";
import ProgressHeader from "@/components/ProgressHeader";
import { getAvailableTimezones, getCurrentTimezone } from "@/lib/add-device-flow";
import { CONTROL_MODES, createControl, formatControlMode, type ControlMode, type ControlPayload } from "@/lib/controls";
import { FormEvent, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

function toNumber(value: string, fallback = 0) {
  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : fallback;
}

export default function AddControlView() {
  const navigate = useNavigate();
  const availableTimezones = useMemo(() => getAvailableTimezones(), []);
  const [name, setName] = useState("");
  const [timezone, setTimezone] = useState(getCurrentTimezone());
  const [maxPriceSnt, setMaxPriceSnt] = useState("100");
  const [minPriceSnt, setMinPriceSnt] = useState("0");
  const [dailyOnMinutes, setDailyOnMinutes] = useState("60");
  const [taxPercent, setTaxPercent] = useState("25.5");
  const [mode, setMode] = useState<ControlMode>("BELOW_MAX_PRICE");
  const [manualOn, setManualOn] = useState(false);
  const [alwaysOnBelowMinPrice, setAlwaysOnBelowMinPrice] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const timezoneIsValid = availableTimezones.includes(timezone);
  const canSubmit = name.trim().length > 0 && timezoneIsValid && !isSubmitting;

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

    if (!canSubmit) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const control = await createControl(buildPayload());

      navigate(`/controls/${control.id}`);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Failed to create control");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader title="Add Control" compact />

      <main className="app-page py-8">
        <section className="mb-10">
          <ProgressHeader label="Control Configuration" step={1} total={1} />
        </section>

        <div className="grid gap-12 items-start lg:grid-cols-12">
          <section className="space-y-8 lg:col-span-8">
            <div>
              <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
                Create Control
              </h1>
              <p className="max-w-xl text-lg text-on-surface-variant">
                Define the price thresholds, daily run target, timezone, and operating mode used by the control scheduler.
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
                    placeholder="e.g., Water Heater Control"
                    type="text"
                    value={name}
                  />
                </div>

                <div className="md:col-span-2">
                  <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="timezone">
                    Timezone
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                    id="timezone"
                    list="control-timezone-options"
                    onChange={(event) => setTimezone(event.target.value)}
                    placeholder="Start typing to search timezones"
                    type="text"
                    value={timezone}
                  />
                  <datalist id="control-timezone-options">
                    {availableTimezones.map((option) => (
                      <option key={option} value={option} />
                    ))}
                  </datalist>
                  {!timezoneIsValid ? (
                    <p className="mt-2 ml-1 text-sm text-on-error-container">Select a valid timezone from the available list.</p>
                  ) : null}
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
                <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canSubmit} type="submit">
                  {isSubmitting ? "Creating..." : "Create Control"}
                </button>
                <Link className="secondary-action justify-center" to="/controls">
                  Cancel
                </Link>
              </div>

              {submitError ? (
                <div className="rounded-xl border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
                  {submitError}
                </div>
              ) : null}
            </form>
          </section>
        </div>
      </main>
    </>
  );
}

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
import ProgressHeader from "@/components/ProgressHeader";
import { getAvailableTimezones, getCurrentTimezone } from "@/lib/add-device-flow";
import { CONTROL_MODES, createControl, formatControlMode, type ControlMode, type ControlPayload } from "@/lib/controls";
import { fetchElectricityContracts, type ElectricityContract } from "@/lib/electricity-contracts";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

const STEP_COUNT = 5;

function toNumber(value: string, fallback = 0) {
  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : fallback;
}

function getContractName(contracts: ElectricityContract[], selectedId: string, fallback: string) {
  if (!selectedId) {
    return fallback;
  }

  return contracts.find((contract) => String(contract.id) === selectedId)?.name ?? fallback;
}

export default function AddControlView() {
  const navigate = useNavigate();
  const { group, t } = useI18n("addControl");
  const common = useI18n("common").t;
  const modeLabels = group("modeLabels");
  const modeDescriptions = group("modeDescriptions");
  const availableTimezones = useMemo(() => getAvailableTimezones(), []);
  const [step, setStep] = useState(1);
  const [name, setName] = useState("");
  const [timezone, setTimezone] = useState(getCurrentTimezone());
  const [maxPriceSnt, setMaxPriceSnt] = useState("100");
  const [minPriceSnt, setMinPriceSnt] = useState("0");
  const [dailyOnMinutes, setDailyOnMinutes] = useState("180");
  const [taxPercent, setTaxPercent] = useState("25.5");
  const [mode, setMode] = useState<ControlMode>("BELOW_MAX_PRICE");
  const [manualOn, setManualOn] = useState(false);
  const [alwaysOnBelowMinPrice, setAlwaysOnBelowMinPrice] = useState(false);
  const [energyContracts, setEnergyContracts] = useState<ElectricityContract[]>([]);
  const [transferContracts, setTransferContracts] = useState<ElectricityContract[]>([]);
  const [selectedEnergyContractId, setSelectedEnergyContractId] = useState("");
  const [selectedTransferContractId, setSelectedTransferContractId] = useState("");
  const [contractsError, setContractsError] = useState<string | null>(null);
  const [isContractsLoading, setIsContractsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const timezoneIsValid = availableTimezones.includes(timezone);
  const basicsComplete = name.trim().length > 0 && timezoneIsValid;
  const canSubmit = basicsComplete && !isSubmitting;

  useEffect(() => {
    let active = true;

    Promise.all([
      fetchElectricityContracts("ENERGY"),
      fetchElectricityContracts("TRANSFER")
    ])
      .then(([energyResponse, transferResponse]) => {
        if (!active) return;

        setEnergyContracts(energyResponse);
        setTransferContracts(transferResponse);
        setContractsError(null);
      })
      .catch((error: unknown) => {
        if (!active) return;

        setContractsError(error instanceof Error ? error.message : t("failedLoadContracts"));
      })
      .finally(() => {
        if (active) {
          setIsContractsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const buildPayload = (): ControlPayload => ({
    alwaysOnBelowMinPrice,
    dailyOnMinutes: Math.max(0, Math.round(toNumber(dailyOnMinutes))),
    energyContractId: selectedEnergyContractId ? Number(selectedEnergyContractId) : null,
    manualOn: mode === "MANUAL" ? manualOn : false,
    maxPriceSnt: Math.max(0, toNumber(maxPriceSnt)),
    minPriceSnt: Math.max(0, toNumber(minPriceSnt)),
    mode,
    name: name.trim(),
    taxPercent: Math.max(0, toNumber(taxPercent)),
    transferContractId: selectedTransferContractId ? Number(selectedTransferContractId) : null,
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
      setSubmitError(error instanceof Error ? error.message : t("failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const goNext = () => {
    if (step === 1 && !basicsComplete) {
      return;
    }

    setSubmitError(null);
    setStep((current) => Math.min(current + 1, STEP_COUNT));
  };

  const goBack = () => {
    setSubmitError(null);
    setStep((current) => Math.max(current - 1, 1));
  };

  const selectedEnergyContractName = getContractName(energyContracts, selectedEnergyContractId, t("noEnergyContract"));
  const selectedTransferContractName = getContractName(transferContracts, selectedTransferContractId, t("noTransferContract"));
  const dailyOnHours = (Math.max(0, toNumber(dailyOnMinutes)) / 60).toFixed(1);
  const energyContractHelp = energyContracts.length === 0 ? t("noEnergyContractsAvailableHelp") : t("energyContractHelp");
  const transferContractHelp = transferContracts.length === 0 ? t("noTransferContractsAvailableHelp") : t("transferContractHelp");

  return (
    <>
      <PageHeader title={t("title")} compact />

      <main className="app-page pb-8 pt-4 sm:py-8">
        <section className="mb-10">
          <ProgressHeader label={t("stepLabel")} step={step} total={STEP_COUNT} />
        </section>

        <div className="grid gap-8 items-start lg:grid-cols-12">
          <section className="space-y-8 lg:col-span-8">
            <div>
              <p className="metric-label mb-3">{t("wizardEyebrow", { step, total: STEP_COUNT })}</p>
              <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
                {t(`step${step}Title` as "step1Title")}
              </h1>
              <p className="max-w-2xl text-lg text-on-surface-variant">
                {t(`step${step}Description` as "step1Description")}
              </p>
            </div>

            <form className="app-card space-y-8 p-4 sm:p-6 lg:p-8" onSubmit={handleSubmit}>
              {step === 1 ? (
                <div className="grid gap-6 md:grid-cols-2">
                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="control-name">
                      {t("controlName")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="control-name"
                      onChange={(event) => setName(event.target.value)}
                      placeholder={t("controlNamePlaceholder")}
                      type="text"
                      value={name}
                    />
                    <p className="mt-2 ml-1 text-sm text-on-surface-variant">{t("controlNameHelp")}</p>
                  </div>

                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="timezone">
                      {t("timezone")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="timezone"
                      list="control-timezone-options"
                      onChange={(event) => setTimezone(event.target.value)}
                      placeholder={t("timezonePlaceholder")}
                      type="text"
                      value={timezone}
                    />
                    <datalist id="control-timezone-options">
                      {availableTimezones.map((option) => (
                        <option key={option} value={option} />
                      ))}
                    </datalist>
                    {!timezoneIsValid ? (
                      <p className="mt-2 ml-1 text-sm text-on-error-container">{t("invalidTimezone")}</p>
                    ) : (
                      <p className="mt-2 ml-1 text-sm text-on-surface-variant">{t("timezoneHelp")}</p>
                    )}
                  </div>
                </div>
              ) : null}

              {step === 2 ? (
                <div className="grid gap-4">
                  {CONTROL_MODES.map((option) => (
                    <label
                      className={`cursor-pointer rounded-xl border p-4 transition-all hover:border-primary hover:bg-surface-container-high ${
                        mode === option ? "border-primary bg-surface-container-high" : "border-outline-variant/60 bg-surface-container"
                      }`}
                      key={option}
                    >
                      <div className="flex items-start gap-4">
                        <input
                          checked={mode === option}
                          className="mt-1"
                          onChange={() => {
                            setMode(option);
                            if (option !== "MANUAL") {
                              setManualOn(false);
                            }
                          }}
                          type="radio"
                        />
                        <div>
                          <p className="font-headline text-lg font-bold text-on-surface">{modeLabels[option] ?? formatControlMode(option)}</p>
                          <p className="mt-1 text-sm leading-6 text-on-surface-variant">{modeDescriptions[option] ?? ""}</p>
                        </div>
                      </div>
                    </label>
                  ))}

                  <label className={`flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4 ${mode === "MANUAL" ? "" : "opacity-50"}`}>
                    <span>
                      <span className="block font-headline text-sm font-bold text-on-surface">{t("manualOn")}</span>
                      <span className="block text-sm text-on-surface-variant">{t("manualOnHelp")}</span>
                    </span>
                    <input
                      checked={manualOn}
                      disabled={mode !== "MANUAL"}
                      onChange={(event) => setManualOn(event.target.checked)}
                      type="checkbox"
                    />
                  </label>
                </div>
              ) : null}

              {step === 3 ? (
                <div className="grid gap-6 md:grid-cols-2">
                  {mode === "BELOW_MAX_PRICE" ? (
                    <div>
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="max-price">
                        {t("maxPrice")}
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
                      <p className="mt-2 ml-1 text-sm text-on-surface-variant">{t("maxPriceHelp")}</p>
                    </div>
                  ) : null}

                  {(mode === "CHEAPEST_HOURS" || mode === "CHEAPEST_HOURS_TOMORROW_AWARE") ? (
                    <div>
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="daily-minutes">
                        {t("dailyOnMinutes")}
                      </label>
                      <input
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                        id="daily-minutes"
                        min="0"
                        onChange={(event) => setDailyOnMinutes(event.target.value)}
                        step="15"
                        type="number"
                        value={dailyOnMinutes}
                      />
                      <p className="mt-2 ml-1 text-sm text-on-surface-variant">
                        {t("dailyOnMinutesHelp", { hours: dailyOnHours })}
                      </p>
                    </div>
                  ) : null}

                  {mode === "SCHEDULED" ? (
                    <div className="rounded-xl bg-surface-container p-4 md:col-span-2">
                      <p className="font-headline text-sm font-bold text-on-surface">{t("scheduledNoticeTitle")}</p>
                      <p className="mt-1 text-sm leading-6 text-on-surface-variant">{t("scheduledNoticeDescription")}</p>
                    </div>
                  ) : null}

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="min-price">
                      {t("minPrice")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="min-price"
                      min="0"
                      onChange={(event) => setMinPriceSnt(event.target.value)}
                      step="0.5"
                      type="number"
                      value={minPriceSnt}
                    />
                    <p className="mt-2 ml-1 text-sm text-on-surface-variant">{t("minPriceHelp")}</p>
                  </div>

                  <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4 md:col-span-2">
                    <span>
                      <span className="block font-headline text-sm font-bold text-on-surface">{t("alwaysOnBelowMinPrice")}</span>
                      <span className="block text-sm text-on-surface-variant">{t("alwaysOnBelowMinPriceHelp")}</span>
                    </span>
                    <input
                      checked={alwaysOnBelowMinPrice}
                      onChange={(event) => setAlwaysOnBelowMinPrice(event.target.checked)}
                      type="checkbox"
                    />
                  </label>
                </div>
              ) : null}

              {step === 4 ? (
                <div className="grid gap-6 md:grid-cols-2">
                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="tax-percent">
                      {t("taxPercent")}
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
                    <p className="mt-2 ml-1 text-sm text-on-surface-variant">{t("taxPercentHelp")}</p>
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="energy-contract">
                      {t("energyContract")}
                    </label>
                    <select
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      disabled={isContractsLoading}
                      id="energy-contract"
                      onChange={(event) => setSelectedEnergyContractId(event.target.value)}
                      value={selectedEnergyContractId}
                    >
                      <option value="">{energyContracts.length === 0 ? t("noEnergyContractsAvailable") : t("noEnergyContract")}</option>
                      {energyContracts.map((contract) => (
                        <option key={contract.id} value={contract.id}>{contract.name}</option>
                      ))}
                    </select>
                    <p className="mt-2 ml-1 text-sm text-on-surface-variant">{energyContractHelp}</p>
                  </div>

                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="transfer-contract">
                      {t("transferContract")}
                    </label>
                    <select
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      disabled={isContractsLoading}
                      id="transfer-contract"
                      onChange={(event) => setSelectedTransferContractId(event.target.value)}
                      value={selectedTransferContractId}
                    >
                      <option value="">{transferContracts.length === 0 ? t("noTransferContractsAvailable") : t("noTransferContract")}</option>
                      {transferContracts.map((contract) => (
                        <option key={contract.id} value={contract.id}>{contract.name}</option>
                      ))}
                    </select>
                    <p className="mt-2 ml-1 text-sm text-on-surface-variant">{transferContractHelp}</p>
                    {contractsError ? (
                      <p className="mt-2 ml-1 text-sm text-on-error-container">{contractsError}</p>
                    ) : null}
                  </div>
                </div>
              ) : null}

              {step === 5 ? (
                <div className="space-y-4">
                  <div className="rounded-xl bg-surface-container p-4">
                    <p className="metric-label mb-2">{t("reviewSummary")}</p>
                    <p className="text-lg font-bold text-on-surface">{name.trim() || t("unnamedControl")}</p>
                    <p className="mt-1 text-sm leading-6 text-on-surface-variant">
                      {t("reviewDescription", {
                        mode: modeLabels[mode] ?? formatControlMode(mode),
                        timezone
                      })}
                    </p>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="rounded-xl bg-surface-container p-4">
                      <p className="metric-label mb-1">{t("mode")}</p>
                      <p className="font-semibold">{modeLabels[mode] ?? formatControlMode(mode)}</p>
                    </div>
                    <div className="rounded-xl bg-surface-container p-4">
                      <p className="metric-label mb-1">{t("timezone")}</p>
                      <p className="font-semibold">{timezone}</p>
                    </div>
                    <div className="rounded-xl bg-surface-container p-4">
                      <p className="metric-label mb-1">{t("maxPrice")}</p>
                      <p className="font-semibold">{maxPriceSnt} snt</p>
                    </div>
                    <div className="rounded-xl bg-surface-container p-4">
                      <p className="metric-label mb-1">{t("dailyOnMinutes")}</p>
                      <p className="font-semibold">{dailyOnMinutes} min</p>
                    </div>
                    <div className="rounded-xl bg-surface-container p-4">
                      <p className="metric-label mb-1">{t("energyContract")}</p>
                      <p className="font-semibold">{selectedEnergyContractName}</p>
                    </div>
                    <div className="rounded-xl bg-surface-container p-4">
                      <p className="metric-label mb-1">{t("transferContract")}</p>
                      <p className="font-semibold">{selectedTransferContractName}</p>
                    </div>
                  </div>
                </div>
              ) : null}

              <div className="flex flex-col gap-4 sm:flex-row">
                {step > 1 ? (
                  <button className="secondary-action justify-center" onClick={goBack} type="button">
                    {common("back")}
                  </button>
                ) : (
                  <Link className="secondary-action justify-center" to="/controls">
                    {common("cancel")}
                  </Link>
                )}

                {step < STEP_COUNT ? (
                  <button
                    className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={step === 1 && !basicsComplete}
                    onClick={goNext}
                    type="button"
                  >
                    {t("continue")}
                  </button>
                ) : (
                  <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canSubmit} type="submit">
                    {isSubmitting ? common("creating") : t("create")}
                  </button>
                )}
              </div>

              {submitError ? (
                <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:p-6">
                  {submitError}
                </div>
              ) : null}
            </form>
          </section>

          <aside className="app-card p-4 sm:p-6 lg:col-span-4">
            <p className="metric-label mb-3">{t("guideTitle")}</p>
            <div className="space-y-4">
              {Array.from({ length: STEP_COUNT }, (_, index) => {
                const itemStep = index + 1;
                const isActive = itemStep === step;
                const isDone = itemStep < step;

                return (
                  <button
                    className={`w-full rounded-xl p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
                      isActive ? "bg-primary text-on-primary" : "bg-surface-container text-on-surface hover:bg-surface-container-high"
                    }`}
                    disabled={itemStep > step || (itemStep > 1 && !basicsComplete)}
                    key={itemStep}
                    onClick={() => setStep(itemStep)}
                    type="button"
                  >
                    <span className={`mb-1 block text-[10px] font-bold uppercase tracking-[0.16em] ${isActive ? "text-on-primary/75" : "text-on-surface-variant"}`}>
                      {isDone ? t("completeStep") : t("stepNumber", { step: itemStep })}
                    </span>
                    <span className="block font-headline text-sm font-black">{t(`step${itemStep}ShortTitle` as "step1ShortTitle")}</span>
                  </button>
                );
              })}
            </div>
          </aside>
        </div>
      </main>
    </>
  );
}

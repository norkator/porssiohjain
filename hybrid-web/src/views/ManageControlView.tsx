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

import ControlPriceChartCard from "@/components/ControlPriceChartCard";
import HeatPumpStateDialog from "@/components/HeatPumpStateDialog";
import ControlNotificationsCard from "@/components/ControlNotificationsCard";
import PageHeader from "@/components/PageHeader";
import { fetchElectricityContracts, type ElectricityContract } from "@/lib/electricity-contracts";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import {
  addControlDeviceLink,
  addControlHeatPumpLink,
  CONTROL_MODES,
  deleteControl,
  deleteControlDeviceLink,
  deleteControlHeatPumpLink,
  fetchControl,
  fetchControlDeviceLinks,
  fetchControlHeatPumpLinks,
  formatControlDate,
  formatControlMode,
  type ApiControl,
  type ControlHeatPumpLink,
  type ControlDeviceLink,
  type ControlMode,
  type ControlPayload,
  updateControl
} from "@/lib/controls";
import { fetchDevices, fetchHeatPumpState, type ApiDevice, type AcType } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

type DeviceTab = "STANDARD" | "HEAT_PUMP" | "NOTIFICATIONS";

function toInputNumber(value: number | null | undefined, fallback: string) {
  return value === null || value === undefined ? fallback : String(value);
}

function toNumber(value: string, fallback = 0) {
  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : fallback;
}

export default function ManageControlView() {
  const { t, group } = useI18n("manageControl");
  const common = useI18n("common").t;
  const modeLabels: Record<string, string> = group("modes");
  const actionLabels: Record<string, string> = group("actions");
  const comparisonLabels: Record<string, string> = group("comparisons");
  const acTypeLabels: Record<string, string> = group("acTypes");
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
  const [transferContracts, setTransferContracts] = useState<ElectricityContract[]>([]);
  const [selectedTransferContractId, setSelectedTransferContractId] = useState("");
  const [contractsError, setContractsError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [activeDeviceTab, setActiveDeviceTab] = useState<DeviceTab>("STANDARD");
  const [deviceLinks, setDeviceLinks] = useState<ControlDeviceLink[]>([]);
  const [standardDevices, setStandardDevices] = useState<ApiDevice[]>([]);
  const [heatPumpLinks, setHeatPumpLinks] = useState<ControlHeatPumpLink[]>([]);
  const [heatPumpDevices, setHeatPumpDevices] = useState<ApiDevice[]>([]);
  const [isLoadingLinks, setIsLoadingLinks] = useState(false);
  const [linksError, setLinksError] = useState<string | null>(null);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [deviceChannel, setDeviceChannel] = useState("1");
  const [estimatedPowerKw, setEstimatedPowerKw] = useState("");
  const [isAddingLink, setIsAddingLink] = useState(false);
  const [deleteLinkConfirmId, setDeleteLinkConfirmId] = useState<number | null>(null);
  const [isDeletingLinkId, setIsDeletingLinkId] = useState<number | null>(null);
  const [selectedHeatPumpDeviceId, setSelectedHeatPumpDeviceId] = useState("");
  const [heatPumpStateHex, setHeatPumpStateHex] = useState("");
  const [heatPumpAction, setHeatPumpAction] = useState<ControlHeatPumpLink["controlAction"]>("TURN_ON");
  const [heatPumpComparisonType, setHeatPumpComparisonType] = useState<ControlHeatPumpLink["comparisonType"]>(null);
  const [heatPumpPriceLimit, setHeatPumpPriceLimit] = useState("");
  const [heatPumpEstimatedPowerKw, setHeatPumpEstimatedPowerKw] = useState("");
  const [isAddingHeatPumpLink, setIsAddingHeatPumpLink] = useState(false);
  const [deleteHeatPumpConfirmId, setDeleteHeatPumpConfirmId] = useState<number | null>(null);
  const [isDeletingHeatPumpId, setIsDeletingHeatPumpId] = useState<number | null>(null);
  const [isHeatPumpStateDialogOpen, setIsHeatPumpStateDialogOpen] = useState(false);
  const [isLoadingHeatPumpState, setIsLoadingHeatPumpState] = useState(false);
  const [heatPumpStateDialogValue, setHeatPumpStateDialogValue] = useState("");
  const [heatPumpCurrentState, setHeatPumpCurrentState] = useState<string | null>(null);
  const [heatPumpLastPolledState, setHeatPumpLastPolledState] = useState<string | null>(null);
  const [heatPumpDialogAcType, setHeatPumpDialogAcType] = useState<AcType>("NONE");
  const timezoneIsValid = availableTimezones.includes(timezone);
  const canSave = name.trim().length > 0 && timezoneIsValid && !control?.shared && !isSaving && !isDeleting;
  const standardLinks = deviceLinks.filter((link) => link.device.deviceType === "STANDARD");
  const linkedDeviceKeys = new Set(standardLinks.map((link) => `${link.deviceId}:${link.deviceChannel}`));
  const selectableDevices = standardDevices.filter((device) => !device.shared);
  const selectableHeatPumpDevices = heatPumpDevices.filter((device) => !device.shared);
  const selectedDevice = selectableDevices.find((device) => device.id === Number(selectedDeviceId));
  const selectedHeatPumpDevice = selectableHeatPumpDevices.find((device) => device.id === Number(selectedHeatPumpDeviceId));
  const channelNumber = Number(deviceChannel);
  const canAddLink =
    Boolean(selectedDevice) &&
    Number.isInteger(channelNumber) &&
    channelNumber >= 0 &&
    !linkedDeviceKeys.has(`${selectedDevice?.id}:${channelNumber}`) &&
    !isAddingLink &&
    !control?.shared;
  const canAddHeatPumpLink =
    Boolean(selectedHeatPumpDevice) &&
    heatPumpStateHex.trim().length > 0 &&
    !isAddingHeatPumpLink &&
    !control?.shared;

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
        setSelectedTransferContractId(response.transferContractId === null ? "" : String(response.transferContractId));
        setIsLoading(false);
        setLoadError(null);
      } catch (error) {
        if (!isActive) {
          return;
        }

        setIsLoading(false);
        setLoadError(error instanceof Error ? error.message : t("failedLoad"));
      }
    }

    if (Number.isFinite(controlId)) {
      loadControl();
    }

    return () => {
      isActive = false;
    };
  }, [controlId]);

  useEffect(() => {
    let isActive = true;

    fetchElectricityContracts("TRANSFER")
      .then((response) => {
        if (!isActive) {
          return;
        }

        setTransferContracts(response);
        setContractsError(null);
      })
      .catch((error: unknown) => {
        if (!isActive) {
          return;
        }

        setContractsError(error instanceof Error ? error.message : t("failedLoadContracts"));
      });

    return () => {
      isActive = false;
    };
  }, []);

  useEffect(() => {
    let isActive = true;

    async function loadLinksAndDevices() {
      setIsLoadingLinks(true);
      setLinksError(null);

      try {
        const [linksResponse, heatPumpLinksResponse, devicesResponse] = await Promise.all([
          fetchControlDeviceLinks(controlId),
          fetchControlHeatPumpLinks(controlId),
          fetchDevices()
        ]);

        if (!isActive) {
          return;
        }

        setDeviceLinks(linksResponse);
        setHeatPumpLinks(heatPumpLinksResponse);
        setStandardDevices(devicesResponse.filter((device) => device.deviceType === "STANDARD"));
        setHeatPumpDevices(devicesResponse.filter((device) => device.deviceType === "HEAT_PUMP"));
        setIsLoadingLinks(false);
      } catch (error) {
        if (!isActive) {
          return;
        }

        setIsLoadingLinks(false);
        setLinksError(error instanceof Error ? error.message : t("failedLoadLinks"));
      }
    }

    if (Number.isFinite(controlId)) {
      loadLinksAndDevices();
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
    transferContractId: selectedTransferContractId ? Number(selectedTransferContractId) : null,
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
      setSaveMessage(t("saved"));
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : t("failedSave"));
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
      setSaveError(error instanceof Error ? error.message : t("failedDelete"));
      setIsDeleting(false);
    }
  };

  const handleAddDeviceLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canAddLink || !selectedDevice) {
      return;
    }

    setIsAddingLink(true);
    setLinksError(null);

    try {
      await addControlDeviceLink(controlId, {
        deviceChannel: channelNumber,
        deviceId: selectedDevice.id,
        estimatedPowerKw: estimatedPowerKw.trim().length > 0 ? Math.max(0, toNumber(estimatedPowerKw)) : null
      });

      const linksResponse = await fetchControlDeviceLinks(controlId);
      setDeviceLinks(linksResponse);
      setSelectedDeviceId("");
      setDeviceChannel("1");
      setEstimatedPowerKw("");
    } catch (error) {
      setLinksError(error instanceof Error ? error.message : t("failedLinkDevice"));
    } finally {
      setIsAddingLink(false);
    }
  };

  const handleDeleteDeviceLink = async (linkId: number) => {
    setLinksError(null);
    setIsDeletingLinkId(linkId);

    try {
      await deleteControlDeviceLink(linkId);
      setDeviceLinks((current) => current.filter((link) => link.id !== linkId));
      setDeleteLinkConfirmId((current) => (current === linkId ? null : current));
    } catch (error) {
      setLinksError(error instanceof Error ? error.message : t("failedRemoveDevice"));
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
  };

  const loadHeatPumpStateForDialog = async (deviceId: number) => {
    setIsLoadingHeatPumpState(true);
    setLinksError(null);

    try {
      const response = await fetchHeatPumpState(deviceId);
      const bestState = response.currentState || response.lastPolledState || "";
      setHeatPumpDialogAcType(response.acType);
      setHeatPumpCurrentState(response.currentState);
      setHeatPumpLastPolledState(response.lastPolledState);
      setHeatPumpStateDialogValue(bestState || heatPumpStateHex);
    } catch (error) {
      setLinksError(error instanceof Error ? error.message : t("failedLoadHeatPumpState"));
      setHeatPumpCurrentState(null);
      setHeatPumpLastPolledState(null);
      setHeatPumpDialogAcType("NONE");
      setHeatPumpStateDialogValue(heatPumpStateHex);
    } finally {
      setIsLoadingHeatPumpState(false);
    }
  };

  const handleOpenHeatPumpStateDialog = async () => {
    const deviceId = Number(selectedHeatPumpDeviceId);
    if (!Number.isFinite(deviceId)) {
      return;
    }

    setIsHeatPumpStateDialogOpen(true);
    setHeatPumpStateDialogValue(heatPumpStateHex);
    await loadHeatPumpStateForDialog(deviceId);
  };

  const handleAddHeatPumpLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedHeatPumpDevice || !canAddHeatPumpLink) {
      return;
    }

    setIsAddingHeatPumpLink(true);
    setLinksError(null);

    try {
      await addControlHeatPumpLink(controlId, {
        comparisonType: heatPumpComparisonType,
        controlAction: heatPumpAction,
        deviceId: selectedHeatPumpDevice.id,
        estimatedPowerKw: heatPumpEstimatedPowerKw === "" ? null : Math.max(0, toNumber(heatPumpEstimatedPowerKw)),
        priceLimit: heatPumpPriceLimit === "" ? null : toNumber(heatPumpPriceLimit),
        stateHex: heatPumpStateHex.trim()
      });

      setHeatPumpLinks(await fetchControlHeatPumpLinks(controlId));
      setSelectedHeatPumpDeviceId("");
      setHeatPumpStateHex("");
      setHeatPumpAction("TURN_ON");
      setHeatPumpComparisonType(null);
      setHeatPumpPriceLimit("");
      setHeatPumpEstimatedPowerKw("");
    } catch (error) {
      setLinksError(error instanceof Error ? error.message : t("failedLinkHeatPump"));
    } finally {
      setIsAddingHeatPumpLink(false);
    }
  };

  const handleDeleteHeatPumpLink = async (linkId: number) => {
    setLinksError(null);
    setIsDeletingHeatPumpId(linkId);

    try {
      await deleteControlHeatPumpLink(linkId);
      setHeatPumpLinks((current) => current.filter((link) => link.id !== linkId));
      setDeleteHeatPumpConfirmId((current) => (current === linkId ? null : current));
    } catch (error) {
      setLinksError(error instanceof Error ? error.message : t("failedRemoveHeatPump"));
    } finally {
      setIsDeletingHeatPumpId((current) => (current === linkId ? null : current));
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} title={t("title")} compact />

      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? (
          <div className="app-card p-6 text-sm text-on-surface-variant">{t("loading")}</div>
        ) : null}

        {!isLoading && loadError ? (
          <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
            {t("failedLoad")}. {loadError}
          </div>
        ) : null}

        {!isLoading && !loadError ? (
          <div className="space-y-12">
            <div className="grid gap-12 items-start lg:grid-cols-12">
              <section className="space-y-8 lg:col-span-8">
                <div>
                  <p className="metric-label mb-3">{t("label", { id: controlId })}</p>
                  <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
                    {control?.name ?? t("fallbackName")}
                  </h1>
                  <p className="max-w-xl text-lg text-on-surface-variant">
                    {t("description")}
                  </p>
                </div>

                <form className="app-card space-y-8 p-8" onSubmit={handleSubmit}>
                  <div className="grid gap-6 md:grid-cols-2">
                    <div className="md:col-span-2">
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="control-name">
                        {t("controlName")}
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
                        {common("timezone")}
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
                    </div>

                    <div>
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="min-price">
                        {t("minPrice")}
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
                        {t("dailyOnMinutes")}
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
                    </div>

                    <div className="md:col-span-2">
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="mode">
                        {t("mode")}
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
                          <option key={option} value={option}>{modeLabels[option] ?? formatControlMode(option)}</option>
                        ))}
                      </select>
                    </div>

                    <div className="md:col-span-2">
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="transfer-contract">
                        {t("transferContract")}
                      </label>
                      <select
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                        id="transfer-contract"
                        onChange={(event) => setSelectedTransferContractId(event.target.value)}
                        value={selectedTransferContractId}
                      >
                        <option value="">{t("noTransferContract")}</option>
                        {transferContracts.map((contract) => (
                          <option key={contract.id} value={contract.id}>{contract.name}</option>
                        ))}
                      </select>
                      {contractsError ? (
                        <p className="mt-2 text-sm text-on-error-container">{contractsError}</p>
                      ) : null}
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className={`flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4 ${mode === "MANUAL" ? "" : "opacity-50"}`}>
                      <span className="font-headline text-sm font-bold text-on-surface">{t("manualOn")}</span>
                      <input
                        checked={manualOn}
                        disabled={mode !== "MANUAL"}
                        onChange={(event) => setManualOn(event.target.checked)}
                        type="checkbox"
                      />
                    </label>
                    <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4">
                      <span className="font-headline text-sm font-bold text-on-surface">{t("alwaysOnBelowMinPrice")}</span>
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
                        {isSaving ? t("saving") : t("save")}
                      </button>
                    ) : null}
                    <Link className="secondary-action justify-center" to="/controls">
                      {common("back")}
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
                  <p className="metric-label mb-2">{common("origin")}</p>
                  <p className="font-headline text-2xl font-bold text-on-surface">{control?.shared ? common("shared") : common("mine")}</p>
                </div>
                <div className="app-card p-6">
                  <p className="metric-label mb-2">{common("created")}</p>
                  <p className="font-semibold text-on-surface">{formatControlDate(control?.createdAt, timezone)}</p>
                </div>
                <div className="app-card p-6">
                  <p className="metric-label mb-2">{common("updated")}</p>
                  <p className="font-semibold text-on-surface">{formatControlDate(control?.updatedAt, timezone)}</p>
                </div>
              </aside>
            </div>

            <section className="app-card p-6">
              <div className="mb-5 flex items-center justify-between">
                <h2 className="font-headline text-xl font-bold text-on-surface">{t("controlLinks")}</h2>
                <span className="chip bg-surface-container-highest text-primary-container">{standardLinks.length + heatPumpLinks.length}</span>
              </div>

              <div className="mb-6 flex flex-wrap gap-2 rounded-2xl bg-surface-container p-2">
                <button
                  className={activeDeviceTab === "STANDARD" ? "primary-action px-4 py-3 text-sm" : "secondary-action px-4 py-3 text-sm"}
                  onClick={() => setActiveDeviceTab("STANDARD")}
                  type="button"
                >
                  {t("standard")}
                </button>
                <button
                  className={activeDeviceTab === "HEAT_PUMP" ? "primary-action px-4 py-3 text-sm" : "secondary-action px-4 py-3 text-sm"}
                  onClick={() => setActiveDeviceTab("HEAT_PUMP")}
                  type="button"
                >
                  {t("heatPump")}
                </button>
                <button
                  className={activeDeviceTab === "NOTIFICATIONS" ? "primary-action px-4 py-3 text-sm" : "secondary-action px-4 py-3 text-sm"}
                  onClick={() => setActiveDeviceTab("NOTIFICATIONS")}
                  type="button"
                >
                  {t("controlNotifications")}
                </button>
              </div>

              {activeDeviceTab === "STANDARD" ? (
                <>
                  {isLoadingLinks ? (
                    <p className="text-sm text-on-surface-variant">{t("loadingLinkedDevices")}</p>
                  ) : null}

                  {!isLoadingLinks && standardLinks.length === 0 ? (
                    <p className="text-sm text-on-surface-variant">{t("noStandardLinks")}</p>
                  ) : null}

                  <div className="space-y-3">
                    {standardLinks.map((link) => (
                      <div className="rounded-xl bg-surface-container p-4" key={link.id}>
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-headline font-bold text-on-surface">{link.device.deviceName}</p>
                            <p className="font-mono text-xs text-outline">UUID: {link.device.uuid}</p>
                          </div>
                          {!control?.shared ? (
                            deleteLinkConfirmId === link.id ? (
                              <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                                <div>
                                  <p className="font-headline text-sm font-bold text-on-error-container">{common("confirmRemoval")}</p>
                                  <p className="text-xs text-on-error-container">{t("unlinkDeviceDescription")}</p>
                                </div>
                                <div className="grid grid-cols-2 gap-2">
                                  <button
                                    className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                                    disabled={isDeletingLinkId === link.id}
                                    onClick={() => handleDeleteDeviceLink(link.id)}
                                    type="button"
                                  >
                                    {isDeletingLinkId === link.id ? common("removing") : common("confirm")}
                                  </button>
                                  <button
                                    className="secondary-action justify-center px-3 py-2 text-xs"
                                    disabled={isDeletingLinkId === link.id}
                                    onClick={() => setDeleteLinkConfirmId(null)}
                                    type="button"
                                  >
                                    {common("cancel")}
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <button
                                className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container"
                                onClick={() => setDeleteLinkConfirmId(link.id)}
                                type="button"
                              >
                                {common("remove")}
                              </button>
                            )
                          ) : null}
                        </div>
                        <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
                          <div>
                            <span className="metric-label">{common("channel")}</span>
                            <p className="font-semibold text-on-surface">{link.deviceChannel}</p>
                          </div>
                          <div>
                            <span className="metric-label">{t("estimatedKw")}</span>
                            <p className="font-semibold text-on-surface">{link.estimatedPowerKw ?? "-"}</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>

                  {!control?.shared ? (
                    <form className="mt-6 space-y-4 border-t border-outline-variant/50 pt-6" onSubmit={handleAddDeviceLink}>
                      <div>
                        <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="standard-device">
                          {t("device")}
                        </label>
                        <select
                          className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                          id="standard-device"
                          onChange={(event) => setSelectedDeviceId(event.target.value)}
                          value={selectedDeviceId}
                        >
                          <option value="">{common("selectStandardDevice")}</option>
                          {selectableDevices.map((device) => (
                            <option key={device.id} value={device.id}>{device.deviceName}</option>
                          ))}
                        </select>
                      </div>

                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="device-channel">
                            {common("channel")}
                          </label>
                          <input
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="device-channel"
                            min="0"
                            onChange={(event) => setDeviceChannel(event.target.value)}
                            step="1"
                            type="number"
                            value={deviceChannel}
                          />
                        </div>
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="estimated-power">
                            {t("estKw")}
                          </label>
                          <input
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="estimated-power"
                            min="0"
                            onChange={(event) => setEstimatedPowerKw(event.target.value)}
                            placeholder={t("optional")}
                            step="0.1"
                            type="number"
                            value={estimatedPowerKw}
                          />
                        </div>
                      </div>

                      {selectedDevice && linkedDeviceKeys.has(`${selectedDevice.id}:${channelNumber}`) ? (
                        <p className="text-sm text-on-error-container">{t("alreadyLinked")}</p>
                      ) : null}

                      <button className="secondary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canAddLink} type="submit">
                        {isAddingLink ? t("linking") : common("linkStandardDevice")}
                      </button>
                    </form>
                  ) : null}
                </>
              ) : null}

              {activeDeviceTab === "HEAT_PUMP" ? (
                <>
                  {isLoadingLinks ? (
                    <p className="text-sm text-on-surface-variant">{t("loadingHeatPumpLinks")}</p>
                  ) : null}

                  {!isLoadingLinks && heatPumpLinks.length === 0 ? (
                    <p className="text-sm text-on-surface-variant">{t("noHeatPumpLinks")}</p>
                  ) : null}

                  <div className="space-y-3">
                    {heatPumpLinks.map((link) => (
                      <div className="rounded-xl bg-surface-container p-4" key={link.id}>
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-headline font-bold text-on-surface">{link.device.deviceName}</p>
                            <p className="text-sm text-on-surface-variant">
                              {actionLabels[link.controlAction] ?? link.controlAction.replace(/_/g, " ")} {link.comparisonType ? `${comparisonLabels[link.comparisonType] ?? link.comparisonType.replace(/_/g, " ")} ${link.priceLimit ?? "-"}` : ""}
                            </p>
                          </div>
                          {!control?.shared ? (
                            deleteHeatPumpConfirmId === link.id ? (
                              <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                                <div>
                                  <p className="font-headline text-sm font-bold text-on-error-container">{common("confirmRemoval")}</p>
                                  <p className="text-xs text-on-error-container">{t("unlinkHeatPumpDescription")}</p>
                                </div>
                                <div className="grid grid-cols-2 gap-2">
                                  <button
                                    className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                                    disabled={isDeletingHeatPumpId === link.id}
                                    onClick={() => handleDeleteHeatPumpLink(link.id)}
                                    type="button"
                                  >
                                    {isDeletingHeatPumpId === link.id ? common("removing") : common("confirm")}
                                  </button>
                                  <button
                                    className="secondary-action justify-center px-3 py-2 text-xs"
                                    disabled={isDeletingHeatPumpId === link.id}
                                    onClick={() => setDeleteHeatPumpConfirmId(null)}
                                    type="button"
                                  >
                                    {common("cancel")}
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <button
                                className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container"
                                onClick={() => setDeleteHeatPumpConfirmId(link.id)}
                                type="button"
                              >
                                {common("remove")}
                              </button>
                            )
                          ) : null}
                        </div>
                        <div className="mt-3 grid gap-2 text-sm md:grid-cols-3">
                          <div>
                            <span className="metric-label">{t("priceLimit")}</span>
                            <p className="font-semibold text-on-surface">{link.priceLimit ?? "-"}</p>
                          </div>
                          <div>
                            <span className="metric-label">{t("estimatedKw")}</span>
                            <p className="font-semibold text-on-surface">{link.estimatedPowerKw ?? "-"}</p>
                          </div>
                          <div>
                            <span className="metric-label">{t("state")}</span>
                            <p className="line-clamp-3 whitespace-pre-wrap break-all rounded-lg bg-surface-container-highest p-3 font-mono text-xs text-on-surface">{link.stateHex}</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>

                  {!control?.shared ? (
                    <form className="mt-6 space-y-4 border-t border-outline-variant/50 pt-6" onSubmit={handleAddHeatPumpLink}>
                      <div className="grid gap-4 md:grid-cols-2">
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="heat-pump-device">
                            {t("device")}
                          </label>
                          <select
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="heat-pump-device"
                            onChange={(event) => setSelectedHeatPumpDeviceId(event.target.value)}
                            value={selectedHeatPumpDeviceId}
                          >
                            <option value="">{t("selectHeatPumpDevice")}</option>
                            {selectableHeatPumpDevices.map((device) => (
                              <option key={device.id} value={device.id}>{device.deviceName}</option>
                            ))}
                          </select>
                        </div>
                        <button className="secondary-action justify-center self-end disabled:cursor-not-allowed disabled:opacity-60" disabled={!selectedHeatPumpDeviceId} onClick={handleOpenHeatPumpStateDialog} type="button">
                          {t("queryEditState")}
                        </button>
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="heat-pump-action">
                            {common("action")}
                          </label>
                          <select
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="heat-pump-action"
                            onChange={(event) => setHeatPumpAction(event.target.value as ControlHeatPumpLink["controlAction"])}
                            value={heatPumpAction}
                          >
                            <option value="TURN_ON">{actionLabels.TURN_ON}</option>
                            <option value="TURN_OFF">{actionLabels.TURN_OFF}</option>
                            <option value="SET_TEMPERATURE">{actionLabels.SET_TEMPERATURE}</option>
                            <option value="SET_MODE">{actionLabels.SET_MODE}</option>
                          </select>
                        </div>
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="heat-pump-comparison">
                            {t("comparison")}
                          </label>
                          <select
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="heat-pump-comparison"
                            onChange={(event) => setHeatPumpComparisonType(event.target.value === "" ? null : event.target.value as ControlHeatPumpLink["comparisonType"])}
                            value={heatPumpComparisonType ?? ""}
                          >
                            <option value="">{t("noComparison")}</option>
                            <option value="GREATER_THAN">{comparisonLabels.GREATER_THAN}</option>
                            <option value="LESS_THAN">{comparisonLabels.LESS_THAN}</option>
                          </select>
                        </div>
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="heat-pump-price-limit">
                            {t("priceLimit")}
                          </label>
                          <input
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="heat-pump-price-limit"
                            onChange={(event) => setHeatPumpPriceLimit(event.target.value)}
                            placeholder={t("optional")}
                            step="0.1"
                            type="number"
                            value={heatPumpPriceLimit}
                          />
                        </div>
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="heat-pump-estimated-power">
                            {t("estKw")}
                          </label>
                          <input
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="heat-pump-estimated-power"
                            min="0"
                            onChange={(event) => setHeatPumpEstimatedPowerKw(event.target.value)}
                            placeholder={t("optional")}
                            step="0.1"
                            type="number"
                            value={heatPumpEstimatedPowerKw}
                          />
                        </div>
                      </div>

                      <div>
                        <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="heat-pump-state">
                          {t("state")}
                        </label>
                        <textarea
                          className="min-h-40 w-full rounded-xl bg-surface-container-highest px-4 py-3 font-mono text-xs text-on-surface outline-none"
                          id="heat-pump-state"
                          onChange={(event) => setHeatPumpStateHex(event.target.value)}
                          placeholder={t("statePlaceholder")}
                          value={heatPumpStateHex}
                        />
                      </div>

                      <button className="secondary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canAddHeatPumpLink} type="submit">
                        {isAddingHeatPumpLink ? t("linking") : t("linkHeatPumpDevice")}
                      </button>
                    </form>
                  ) : null}
                </>
              ) : null}

              {activeDeviceTab === "NOTIFICATIONS" ? (
                <ControlNotificationsCard controlId={controlId} isReadOnly={Boolean(control?.shared)} timezone={timezone} />
              ) : null}

              {linksError ? (
                <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                  {linksError}
                </div>
              ) : null}
            </section>

            <section>
              <ControlPriceChartCard controlId={controlId} />
            </section>

            {!control?.shared ? (
              <section className="app-card border-error-container bg-error-container/40 p-6">
                {!deleteConfirmOpen ? (
                  <button
                    className="w-full rounded-xl bg-error-container px-5 py-4 font-headline font-bold text-on-error-container transition-all hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={isDeleting || isSaving}
                    onClick={() => setDeleteConfirmOpen(true)}
                    type="button"
                  >
                    {t("delete")}
                  </button>
                ) : (
                  <div className="space-y-4">
                    <div>
                      <p className="font-headline text-lg font-bold text-on-error-container">{common("confirmDeletion")}</p>
                      <p className="text-sm text-on-error-container">{t("deleteDescription")}</p>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <button
                        className="rounded-xl bg-error-container px-4 py-3 font-headline font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={isDeleting || isSaving}
                        onClick={handleDelete}
                        type="button"
                      >
                        {isDeleting ? common("deleting") : common("confirm")}
                      </button>
                      <button
                        className="secondary-action justify-center"
                        disabled={isDeleting}
                        onClick={() => setDeleteConfirmOpen(false)}
                        type="button"
                      >
                        {common("cancel")}
                      </button>
                    </div>
                  </div>
                )}
              </section>
            ) : null}
          </div>
        ) : null}
      </main>

      <HeatPumpStateDialog
        acType={heatPumpDialogAcType}
        currentState={heatPumpCurrentState}
        formatAcType={(value) => acTypeLabels[value] ?? value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char) => char.toUpperCase())}
        isLoading={isLoadingHeatPumpState}
        isOpen={isHeatPumpStateDialogOpen}
        labels={{
          acType: t("acType"),
          auto: t("auto"),
          cancel: common("cancel"),
          close: common("close"),
          cool: t("cool"),
          dry: t("dry"),
          fanOnly: t("fanOnly"),
          fanSpeed: t("fanSpeed"),
          heat: t("heat"),
          heatPumpState: t("heatPumpState"),
          heatPumpStateHelp: t("heatPumpStateHelp"),
          invalidState: t("invalidMitsubishiState"),
          loading: common("loading"),
          mitsubishiEditorHelp: t("mitsubishiEditorHelp"),
          mode: t("workingMode"),
          off: t("off"),
          on: t("on"),
          power: t("powerMode"),
          rawState: t("rawState"),
          refreshCurrentState: t("refreshCurrentState"),
          saveState: t("saveState"),
          selectCommandState: t("selectCommandState"),
          targetTemperature: t("targetTemperature"),
          useCurrent: t("useCurrent"),
          useLastPolled: t("useLastPolled")
        }}
        lastPolledState={heatPumpLastPolledState}
        onClose={() => setIsHeatPumpStateDialogOpen(false)}
        onRefresh={() => loadHeatPumpStateForDialog(Number(selectedHeatPumpDeviceId))}
        onSave={(value) => {
          setHeatPumpStateHex(value);
          setIsHeatPumpStateDialogOpen(false);
        }}
        onStateChange={setHeatPumpStateDialogValue}
        stateValue={heatPumpStateDialogValue}
      />
    </>
  );
}

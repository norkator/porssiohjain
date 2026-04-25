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
import PageHeader from "@/components/PageHeader";
import { fetchElectricityContracts, type ElectricityContract } from "@/lib/electricity-contracts";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import {
  addControlDeviceLink,
  CONTROL_MODES,
  deleteControl,
  deleteControlDeviceLink,
  fetchControl,
  fetchControlDeviceLinks,
  formatControlDate,
  formatControlMode,
  type ApiControl,
  type ControlDeviceLink,
  type ControlMode,
  type ControlPayload,
  updateControl
} from "@/lib/controls";
import { fetchDevices, type ApiDevice } from "@/lib/devices";
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
  const [transferContracts, setTransferContracts] = useState<ElectricityContract[]>([]);
  const [selectedTransferContractId, setSelectedTransferContractId] = useState("");
  const [contractsError, setContractsError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [deviceLinks, setDeviceLinks] = useState<ControlDeviceLink[]>([]);
  const [standardDevices, setStandardDevices] = useState<ApiDevice[]>([]);
  const [isLoadingLinks, setIsLoadingLinks] = useState(false);
  const [linksError, setLinksError] = useState<string | null>(null);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [deviceChannel, setDeviceChannel] = useState("1");
  const [estimatedPowerKw, setEstimatedPowerKw] = useState("");
  const [isAddingLink, setIsAddingLink] = useState(false);
  const [deleteLinkConfirmId, setDeleteLinkConfirmId] = useState<number | null>(null);
  const [isDeletingLinkId, setIsDeletingLinkId] = useState<number | null>(null);
  const timezoneIsValid = availableTimezones.includes(timezone);
  const canSave = name.trim().length > 0 && timezoneIsValid && !control?.shared && !isSaving && !isDeleting;
  const standardLinks = deviceLinks.filter((link) => link.device.deviceType === "STANDARD");
  const linkedDeviceKeys = new Set(standardLinks.map((link) => `${link.deviceId}:${link.deviceChannel}`));
  const selectableDevices = standardDevices.filter((device) => !device.shared);
  const selectedDevice = selectableDevices.find((device) => device.id === Number(selectedDeviceId));
  const channelNumber = Number(deviceChannel);
  const canAddLink =
    Boolean(selectedDevice) &&
    Number.isInteger(channelNumber) &&
    channelNumber >= 0 &&
    !linkedDeviceKeys.has(`${selectedDevice?.id}:${channelNumber}`) &&
    !isAddingLink &&
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

        setContractsError(error instanceof Error ? error.message : "Failed to load transfer contracts");
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
        const [linksResponse, devicesResponse] = await Promise.all([
          fetchControlDeviceLinks(controlId),
          fetchDevices()
        ]);

        if (!isActive) {
          return;
        }

        setDeviceLinks(linksResponse);
        setStandardDevices(devicesResponse.filter((device) => device.deviceType === "STANDARD"));
        setIsLoadingLinks(false);
      } catch (error) {
        if (!isActive) {
          return;
        }

        setIsLoadingLinks(false);
        setLinksError(error instanceof Error ? error.message : "Failed to load linked devices");
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
      setLinksError(error instanceof Error ? error.message : "Failed to link device");
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
      setLinksError(error instanceof Error ? error.message : "Failed to remove linked device");
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
  };

  return (
    <>
      <PageHeader title="Manage Control" compact />

      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? (
          <div className="app-card p-6 text-sm text-on-surface-variant">Loading control...</div>
        ) : null}

        {!isLoading && loadError ? (
          <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
            Failed to load control. {loadError}
          </div>
        ) : null}

        {!isLoading && !loadError ? (
          <div className="space-y-12">
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

                    <div className="md:col-span-2">
                      <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="transfer-contract">
                        Transfer Contract
                      </label>
                      <select
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                        id="transfer-contract"
                        onChange={(event) => setSelectedTransferContractId(event.target.value)}
                        value={selectedTransferContractId}
                      >
                        <option value="">No transfer contract</option>
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
                <section className="app-card p-6">
                  <div className="mb-5 flex items-start justify-between gap-4">
                    <div>
                      <p className="metric-label mb-2">Standard Devices</p>
                      <h2 className="font-headline text-xl font-bold text-on-surface">Linked Devices</h2>
                    </div>
                    <span className="chip bg-surface-container-highest text-primary-container">{standardLinks.length}</span>
                  </div>

                  {isLoadingLinks ? (
                    <p className="text-sm text-on-surface-variant">Loading linked devices...</p>
                  ) : null}

                  {!isLoadingLinks && standardLinks.length === 0 ? (
                    <p className="text-sm text-on-surface-variant">No standard devices linked to this control yet.</p>
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
                                  <p className="font-headline text-sm font-bold text-on-error-container">Confirm removal</p>
                                  <p className="text-xs text-on-error-container">This unlinks the device from this control.</p>
                                </div>
                                <div className="grid grid-cols-2 gap-2">
                                  <button
                                    className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                                    disabled={isDeletingLinkId === link.id}
                                    onClick={() => handleDeleteDeviceLink(link.id)}
                                    type="button"
                                  >
                                    {isDeletingLinkId === link.id ? "Removing..." : "Confirm"}
                                  </button>
                                  <button
                                    className="secondary-action justify-center px-3 py-2 text-xs"
                                    disabled={isDeletingLinkId === link.id}
                                    onClick={() => setDeleteLinkConfirmId(null)}
                                    type="button"
                                  >
                                    Cancel
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <button
                                className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container"
                                onClick={() => setDeleteLinkConfirmId(link.id)}
                                type="button"
                              >
                                Remove
                              </button>
                            )
                          ) : null}
                        </div>
                        <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
                          <div>
                            <span className="metric-label">Channel</span>
                            <p className="font-semibold text-on-surface">{link.deviceChannel}</p>
                          </div>
                          <div>
                            <span className="metric-label">Estimated kW</span>
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
                          Device
                        </label>
                        <select
                          className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                          id="standard-device"
                          onChange={(event) => setSelectedDeviceId(event.target.value)}
                          value={selectedDeviceId}
                        >
                          <option value="">Select standard device</option>
                          {selectableDevices.map((device) => (
                            <option key={device.id} value={device.id}>{device.deviceName}</option>
                          ))}
                        </select>
                      </div>

                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="device-channel">
                            Channel
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
                            Est. kW
                          </label>
                          <input
                            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                            id="estimated-power"
                            min="0"
                            onChange={(event) => setEstimatedPowerKw(event.target.value)}
                            placeholder="Optional"
                            step="0.1"
                            type="number"
                            value={estimatedPowerKw}
                          />
                        </div>
                      </div>

                      {selectedDevice && linkedDeviceKeys.has(`${selectedDevice.id}:${channelNumber}`) ? (
                        <p className="text-sm text-on-error-container">This device channel is already linked to the control.</p>
                      ) : null}

                      <button className="secondary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canAddLink} type="submit">
                        {isAddingLink ? "Linking..." : "Link Standard Device"}
                      </button>
                    </form>
                  ) : null}

                  {linksError ? (
                    <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                      {linksError}
                    </div>
                  ) : null}
                </section>
                {!control?.shared ? (
                  <div className="app-card border-error-container bg-error-container/40 p-6">
                    {!deleteConfirmOpen ? (
                      <button
                        className="w-full rounded-xl bg-error-container px-5 py-4 font-headline font-bold text-on-error-container transition-all hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={isDeleting || isSaving}
                        onClick={() => setDeleteConfirmOpen(true)}
                        type="button"
                      >
                        Delete Control
                      </button>
                    ) : (
                      <div className="space-y-4">
                        <div>
                          <p className="font-headline text-lg font-bold text-on-error-container">Confirm deletion</p>
                          <p className="text-sm text-on-error-container">This removes the control and its linked device rules.</p>
                        </div>
                        <div className="grid grid-cols-2 gap-3">
                          <button
                            className="rounded-xl bg-error-container px-4 py-3 font-headline font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                            disabled={isDeleting || isSaving}
                            onClick={handleDelete}
                            type="button"
                          >
                            {isDeleting ? "Deleting..." : "Confirm"}
                          </button>
                          <button
                            className="secondary-action justify-center"
                            disabled={isDeleting}
                            onClick={() => setDeleteConfirmOpen(false)}
                            type="button"
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                ) : null}
              </aside>
            </div>

            <section>
              <ControlPriceChartCard controlId={controlId} />
            </section>
          </div>
        ) : null}
      </main>
    </>
  );
}

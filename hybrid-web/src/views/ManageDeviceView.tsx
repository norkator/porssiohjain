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
import { getAvailableTimezones } from "@/lib/add-device-flow";
import {
  deleteDevice,
  fetchDevice,
  formatAcType,
  formatDeviceLastCommunication,
  formatDeviceType,
  type AcType,
  type ApiDevice,
  type DevicePayload,
  type DeviceType,
  updateDevice
} from "@/lib/devices";
import {
  listSelectableHeatPumpAcDevices,
  type HeatPumpAcDevice,
  type HeatPumpAcType
} from "@/lib/heat-pump-devices";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

const DEVICE_TYPES: DeviceType[] = ["STANDARD", "HEAT_PUMP"];
const AC_TYPES: AcType[] = ["NONE", "TOSHIBA", "MITSUBISHI"];

export default function ManageDeviceView() {
  const { t, group } = useI18n("manageDevice");
  const common = useI18n("common").t;
  const deviceTypeLabels: Record<string, string> = group("deviceTypes");
  const acTypeLabels: Record<string, string> = group("acTypes");
  const navigate = useNavigate();
  const params = useParams();
  const deviceId = Number(params.deviceId);
  const availableTimezones = useMemo(() => getAvailableTimezones(), []);
  const [device, setDevice] = useState<ApiDevice | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [deviceName, setDeviceName] = useState("");
  const [timezone, setTimezone] = useState("");
  const [deviceType, setDeviceType] = useState<DeviceType>("STANDARD");
  const [enabled, setEnabled] = useState(true);
  const [hpName, setHpName] = useState("");
  const [acType, setAcType] = useState<AcType>("NONE");
  const [acUsername, setAcUsername] = useState("");
  const [acPassword, setAcPassword] = useState("");
  const [acDeviceId, setAcDeviceId] = useState("");
  const [acDeviceLabel, setAcDeviceLabel] = useState("");
  const [buildingId, setBuildingId] = useState("");
  const [acDeviceUniqueId, setAcDeviceUniqueId] = useState("");
  const [selectableAcDevices, setSelectableAcDevices] = useState<HeatPumpAcDevice[]>([]);
  const [isAcDialogOpen, setIsAcDialogOpen] = useState(false);
  const [isLoadingAcDevices, setIsLoadingAcDevices] = useState(false);
  const [acSelectionError, setAcSelectionError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const lastAcCredentialsRef = useRef({
    acPassword: "",
    acType: "NONE" as AcType,
    acUsername: ""
  });
  const timezoneIsValid = availableTimezones.includes(timezone);
  const isHeatPump = deviceType === "HEAT_PUMP";
  const hasAcCredentials = acUsername.trim().length > 0 && acPassword.trim().length > 0;
  const heatPumpReady =
    !isHeatPump ||
    (
      hpName.trim().length > 0 &&
      acType !== "NONE" &&
      hasAcCredentials &&
      acDeviceId.trim().length > 0
    );
  const canSave = deviceName.trim().length > 0 && timezoneIsValid && heatPumpReady && !device?.shared && !isSaving && !isDeleting;

  useEffect(() => {
    let isActive = true;

    async function loadDevice() {
      try {
        const response = await fetchDevice(deviceId);

        if (!isActive) {
          return;
        }

        const nextAcType = (response.acType ?? "NONE") as AcType;
        setDevice(response);
        setDeviceName(response.deviceName);
        setTimezone(response.timezone);
        setDeviceType(response.deviceType as DeviceType);
        setEnabled(response.enabled);
        setHpName(response.hpName ?? "");
        setAcType(nextAcType);
        setAcUsername(response.acUsername ?? "");
        setAcPassword(response.acPassword ?? "");
        setAcDeviceId(response.acDeviceId ?? "");
        setAcDeviceLabel(response.acDeviceId ? `Device ${response.acDeviceId}` : "");
        setBuildingId(response.buildingId !== null && response.buildingId !== undefined ? String(response.buildingId) : "");
        setAcDeviceUniqueId(response.acDeviceUniqueId ?? "");
        lastAcCredentialsRef.current = {
          acPassword: response.acPassword ?? "",
          acType: nextAcType,
          acUsername: response.acUsername ?? ""
        };
        setLoadError(null);
        setIsLoading(false);
      } catch (error) {
        if (!isActive) {
          return;
        }

        setLoadError(error instanceof Error ? error.message : t("failedLoad"));
        setIsLoading(false);
      }
    }

    if (Number.isFinite(deviceId)) {
      loadDevice();
    }

    return () => {
      isActive = false;
    };
  }, [deviceId]);

  useEffect(() => {
    const lastAcCredentials = lastAcCredentialsRef.current;
    const credentialsChanged =
      lastAcCredentials.acUsername !== acUsername ||
      lastAcCredentials.acPassword !== acPassword ||
      lastAcCredentials.acType !== acType;

    if (!credentialsChanged) {
      return;
    }

    setAcDeviceId("");
    setAcDeviceLabel("");
    setBuildingId("");
    setAcDeviceUniqueId("");
    setSelectableAcDevices([]);
    setIsAcDialogOpen(false);
    lastAcCredentialsRef.current = {
      acPassword,
      acType,
      acUsername
    };
  }, [acPassword, acType, acUsername]);

  if (!Number.isFinite(deviceId)) {
    return <Navigate replace to="/devices" />;
  }

  const buildPayload = (): DevicePayload => ({
    acDeviceId: isHeatPump ? acDeviceId : undefined,
    acPassword: isHeatPump ? acPassword : undefined,
    acType: isHeatPump ? acType : "NONE",
    acUsername: isHeatPump ? acUsername.trim() : undefined,
    buildingId: isHeatPump ? buildingId : undefined,
    deviceName: deviceName.trim(),
    deviceType,
    enabled,
    hpName: isHeatPump ? hpName.trim() : undefined,
    timezone
  });

  const handleOpenAcSelection = async () => {
    if (!isHeatPump || acType === "NONE" || !hasAcCredentials || isLoadingAcDevices) {
      return;
    }

    setIsLoadingAcDevices(true);
    setAcSelectionError(null);

    try {
      const devices = await listSelectableHeatPumpAcDevices({
        acPassword,
        acType: acType as HeatPumpAcType,
        acUsername: acUsername.trim(),
        hpName: hpName.trim()
      });

      setSelectableAcDevices(devices);
      setIsAcDialogOpen(true);
    } catch (error) {
      setAcSelectionError(error instanceof Error ? error.message : t("failedLoadAcDevices"));
      setSelectableAcDevices([]);
    } finally {
      setIsLoadingAcDevices(false);
    }
  };

  const handleSelectAcDevice = (selection: HeatPumpAcDevice) => {
    setAcDeviceId(selection.id);
    setAcDeviceLabel(selection.name);
    setBuildingId(selection.buildingId ?? "");
    setAcDeviceUniqueId(selection.deviceUniqueId ?? "");
    setIsAcDialogOpen(false);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canSave) {
      return;
    }

    setIsSaving(true);
    setSaveError(null);
    setSaveMessage(null);

    try {
      const response = await updateDevice(deviceId, buildPayload());

      setDevice(response);
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

    try {
      await deleteDevice(deviceId);
      navigate("/devices");
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : t("failedDelete"));
      setIsDeleting(false);
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
          <div className="grid gap-12 items-start lg:grid-cols-12">
            <section className="space-y-8 lg:col-span-8">
              <div>
                <p className="metric-label mb-3">{t("label", { id: deviceId })}</p>
                <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
                  {device?.deviceName ?? t("fallbackName")}
                </h1>
                <p className="max-w-xl text-lg text-on-surface-variant">
                  {t("description")}
                </p>
              </div>

              <form className="app-card space-y-8 p-8" onSubmit={handleSubmit}>
                <div className="grid gap-6 md:grid-cols-2">
                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="device-name">
                      {t("deviceName")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      disabled={device?.shared}
                      id="device-name"
                      onChange={(event) => setDeviceName(event.target.value)}
                      type="text"
                      value={deviceName}
                    />
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="timezone">
                      {common("timezone")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      disabled={device?.shared}
                      id="timezone"
                      list="device-timezone-options"
                      onChange={(event) => setTimezone(event.target.value)}
                      type="text"
                      value={timezone}
                    />
                    <datalist id="device-timezone-options">
                      {availableTimezones.map((option) => (
                        <option key={option} value={option} />
                      ))}
                    </datalist>
                    {!timezoneIsValid ? (
                      <p className="mt-2 ml-1 text-sm text-on-error-container">{t("invalidTimezone")}</p>
                    ) : null}
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="device-type">
                      {t("deviceType")}
                    </label>
                    <select
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      disabled={device?.shared}
                      id="device-type"
                      onChange={(event) => setDeviceType(event.target.value as DeviceType)}
                      value={deviceType}
                    >
                      {DEVICE_TYPES.map((option) => (
                        <option key={option} value={option}>{deviceTypeLabels[option] ?? formatDeviceType(option)}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4">
                  <span className="font-headline text-sm font-bold text-on-surface">{common("enabled")}</span>
                  <input
                    checked={enabled}
                    disabled={device?.shared}
                    onChange={(event) => setEnabled(event.target.checked)}
                    type="checkbox"
                  />
                </label>

                {isHeatPump ? (
                  <section className="space-y-6 border-t border-outline-variant/50 pt-8">
                    <div>
                      <p className="metric-label mb-2">{t("heatPump")}</p>
                      <h2 className="font-headline text-2xl font-bold text-primary">{t("acIntegration")}</h2>
                    </div>

                    <div className="grid gap-6 md:grid-cols-2">
                      <div>
                        <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="hp-name">
                          {t("heatPumpName")}
                        </label>
                        <input
                          className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                          disabled={device?.shared}
                          id="hp-name"
                          onChange={(event) => setHpName(event.target.value)}
                          type="text"
                          value={hpName}
                        />
                      </div>

                      <div>
                        <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="ac-type">
                          {t("acType")}
                        </label>
                        <select
                          className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                          disabled={device?.shared}
                          id="ac-type"
                          onChange={(event) => setAcType(event.target.value as AcType)}
                          value={acType}
                        >
                          {AC_TYPES.map((option) => (
                            <option key={option} value={option}>{acTypeLabels[option] ?? formatAcType(option)}</option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="ac-username">
                          {t("appUsername")}
                        </label>
                        <input
                          autoComplete="username"
                          className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                          disabled={device?.shared}
                          id="ac-username"
                          onChange={(event) => setAcUsername(event.target.value)}
                          type="text"
                          value={acUsername}
                        />
                      </div>

                      <div>
                        <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="ac-password">
                          {t("appPassword")}
                        </label>
                        <input
                          autoComplete="current-password"
                          className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                          disabled={device?.shared}
                          id="ac-password"
                          onChange={(event) => setAcPassword(event.target.value)}
                          type="password"
                          value={acPassword}
                        />
                      </div>
                    </div>

                    <div className="rounded-xl bg-surface-container p-5">
                      <div className="mb-4">
                        <p className="metric-label mb-1">{t("selectedAcDevice")}</p>
                        <p className="font-headline text-lg font-bold text-on-surface">{acDeviceLabel || t("noAcDeviceSelected")}</p>
                        {acDeviceId ? (
                          <p className="mt-1 font-mono text-xs text-outline">{common("id", { id: acDeviceId })}</p>
                        ) : null}
                        {buildingId ? (
                          <p className="mt-1 font-mono text-xs text-outline">{t("buildingId", { id: buildingId })}</p>
                        ) : null}
                      </div>
                      {!device?.shared ? (
                        <button
                          className="secondary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                          disabled={acType === "NONE" || !hasAcCredentials || isLoadingAcDevices}
                          onClick={handleOpenAcSelection}
                          type="button"
                        >
                          {isLoadingAcDevices ? t("loadingAcDevices") : acDeviceId ? t("changeAcDevice") : t("selectAcDevice")}
                        </button>
                      ) : null}
                      {acSelectionError ? (
                        <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                          {acSelectionError}
                        </div>
                      ) : null}
                    </div>
                  </section>
                ) : null}

                <div className="flex flex-col gap-4 sm:flex-row">
                  {!device?.shared ? (
                    <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={!canSave} type="submit">
                      {isSaving ? t("saving") : t("save")}
                    </button>
                  ) : null}
                  <Link className="secondary-action justify-center" to="/devices">
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
                <p className="font-headline text-2xl font-bold text-on-surface">{device?.shared ? common("shared") : common("mine")}</p>
              </div>
              <div className="app-card p-6">
                <p className="metric-label mb-2">{t("connection")}</p>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <span className="metric-label">API</span>
                    <p className="font-semibold text-on-surface">{device?.apiOnline ? t("online") : t("offline")}</p>
                  </div>
                  <div>
                    <span className="metric-label">MQTT</span>
                    <p className="font-semibold text-on-surface">{device?.mqttOnline ? t("online") : t("offline")}</p>
                  </div>
                </div>
              </div>
              <div className="app-card p-6">
                <p className="metric-label mb-2">UUID</p>
                <p className="break-all font-mono text-sm text-on-surface">{device?.uuid}</p>
              </div>
              <div className="app-card p-6">
                <p className="metric-label mb-2">{t("lastSeen")}</p>
                <p className="font-semibold text-on-surface">{formatDeviceLastCommunication(device?.lastCommunication ?? null)}</p>
              </div>

              {!device?.shared ? (
                <div className="app-card border-error-container bg-error-container/40 p-6">
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
                </div>
              ) : null}
            </aside>
          </div>
        ) : null}
      </main>

      {isAcDialogOpen ? (
        <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
          <div className="w-full max-w-2xl rounded-xl bg-surface-container-lowest p-6 shadow-2xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <p className="metric-label mb-2">{t("acDevices")}</p>
                <h2 className="font-headline text-2xl font-bold text-primary">{t("selectDevice")}</h2>
              </div>
              <button className="secondary-action px-3 py-2 text-sm" onClick={() => setIsAcDialogOpen(false)} type="button">
                {common("close")}
              </button>
            </div>
            <div className="max-h-[60vh] space-y-3 overflow-auto">
              {selectableAcDevices.map((selection) => (
                <button
                  className="w-full rounded-xl bg-surface-container p-4 text-left transition-all hover:-translate-y-0.5 hover:bg-surface-container-high"
                  key={`${selection.acType}-${selection.id}-${selection.buildingId ?? ""}`}
                  onClick={() => handleSelectAcDevice(selection)}
                  type="button"
                >
                  <p className="font-headline font-bold text-on-surface">{selection.name}</p>
                  <p className="mt-1 font-mono text-xs text-outline">{common("id", { id: selection.id })}</p>
                  {selection.buildingId ? (
                    <p className="mt-1 font-mono text-xs text-outline">{t("buildingId", { id: selection.buildingId })}</p>
                  ) : null}
                </button>
              ))}
              {selectableAcDevices.length === 0 ? (
                <p className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">{t("noAcDevices")}</p>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

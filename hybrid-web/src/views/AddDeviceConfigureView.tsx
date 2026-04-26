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
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import ProgressHeader from "@/components/ProgressHeader";
import {
  getAvailableTimezones,
  getDeviceTypeOption,
  readAddDeviceDraft,
  updateAddDeviceDraft
} from "@/lib/add-device-flow";
import {
  type HeatPumpAcDevice,
  listSelectableHeatPumpAcDevices
} from "@/lib/heat-pump-devices";
import { useI18n } from "@/lib/i18n";

export default function AddDeviceConfigureView() {
  const navigate = useNavigate();
  const { t } = useI18n("addDeviceConfigure");
  const common = useI18n("common").t;
  const draft = readAddDeviceDraft();
  const deviceType = getDeviceTypeOption(draft.deviceTypeId);
  const isHeatPump = deviceType?.id === "toshiba-heat-pump" || deviceType?.id === "mitsubishi-heat-pump";
  const availableTimezones = useMemo(() => getAvailableTimezones(), []);
  const [deviceName, setDeviceName] = useState(draft.deviceName);
  const [timezone, setTimezone] = useState(draft.timezone);
  const [hpName, setHpName] = useState(draft.hpName);
  const [acUsername, setAcUsername] = useState(draft.acUsername);
  const [acPassword, setAcPassword] = useState(draft.acPassword);
  const [acDeviceId, setAcDeviceId] = useState(draft.acDeviceId);
  const [acDeviceLabel, setAcDeviceLabel] = useState(draft.acDeviceLabel);
  const [acBuildingId, setAcBuildingId] = useState(draft.acBuildingId);
  const [acDeviceUniqueId, setAcDeviceUniqueId] = useState(draft.acDeviceUniqueId);
  const [selectableAcDevices, setSelectableAcDevices] = useState<HeatPumpAcDevice[]>([]);
  const [isAcDialogOpen, setIsAcDialogOpen] = useState(false);
  const [isLoadingAcDevices, setIsLoadingAcDevices] = useState(false);
  const [acSelectionError, setAcSelectionError] = useState<string | null>(null);
  const lastAcCredentialsRef = useRef({
    acPassword: draft.acPassword,
    acUsername: draft.acUsername,
    deviceTypeId: draft.deviceTypeId ?? ""
  });
  const timezoneIsValid = availableTimezones.includes(timezone);
  const hasAcCredentials = acUsername.trim().length > 0 && acPassword.trim().length > 0;
  const heatPumpReady =
    hpName.trim().length > 0 &&
    hasAcCredentials &&
    acDeviceId.trim().length > 0;
  const canContinue = deviceName.trim().length > 0 && timezoneIsValid && (!isHeatPump || heatPumpReady);
  const acType = deviceType?.id === "toshiba-heat-pump" ? "TOSHIBA" : "MITSUBISHI";

  useEffect(() => {
    const lastAcCredentials = lastAcCredentialsRef.current;
    const credentialsChanged =
      lastAcCredentials.acUsername !== acUsername ||
      lastAcCredentials.acPassword !== acPassword ||
      lastAcCredentials.deviceTypeId !== (deviceType?.id ?? "");

    if (!credentialsChanged) {
      return;
    }

    setAcDeviceId("");
    setAcDeviceLabel("");
    setAcBuildingId("");
    setAcDeviceUniqueId("");
    setSelectableAcDevices([]);
    setIsAcDialogOpen(false);
    lastAcCredentialsRef.current = {
      acPassword,
      acUsername,
      deviceTypeId: deviceType?.id ?? ""
    };
  }, [acUsername, acPassword, deviceType?.id]);

  if (!deviceType) {
    return <Navigate replace to="/devices/add/type" />;
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canContinue) {
      return;
    }

    updateAddDeviceDraft({
      deviceName: deviceName.trim(),
      timezone,
      hpName: hpName.trim(),
      acUsername: acUsername.trim(),
      acPassword,
      acDeviceId,
      acDeviceLabel,
      acBuildingId,
      acDeviceUniqueId
    });
    navigate("/devices/add/review");
  };

  const handleOpenAcSelection = async () => {
    if (!hasAcCredentials || isLoadingAcDevices) {
      return;
    }

    setIsLoadingAcDevices(true);
    setAcSelectionError(null);

    try {
      const devices = await listSelectableHeatPumpAcDevices({
        acPassword,
        acType,
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

  const handleSelectAcDevice = (device: HeatPumpAcDevice) => {
    setAcDeviceId(device.id);
    setAcDeviceLabel(device.name);
    setAcBuildingId(device.buildingId ?? "");
    setAcDeviceUniqueId(device.deviceUniqueId ?? "");
    setIsAcDialogOpen(false);
  };

  return (
    <>
      <PageHeader title={t("title")} compact />

      <main className="app-page pb-8 pt-4 sm:py-8">
        <section className="mb-10">
          <ProgressHeader label={t("stepLabel")} step={2} total={4} />
        </section>

        <section className="space-y-8">
          <div className="relative overflow-hidden rounded-xl bg-surface-container-low p-8">
            <div className="relative z-10 max-w-2xl">
              <p className="metric-label mb-4">{t("selectedDeviceType")}</p>
              <h1 className="mb-4 font-headline text-3xl font-extrabold leading-tight text-primary md:text-4xl">
                {deviceType.title}
              </h1>
              <p className="text-sm leading-relaxed text-on-surface-variant">
                {deviceType.setupNotice}
              </p>
            </div>
            <div className="absolute -bottom-4 -right-4 opacity-10">
              <span className="font-headline text-[120px] font-black text-primary">⚡</span>
            </div>
          </div>

          <form className="app-card max-w-4xl space-y-8 p-8" onSubmit={handleSubmit}>
              <div>
                <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="device-name">
                  {t("deviceName")}
                </label>
                <div className="relative">
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                    id="device-name"
                    onChange={(event) => setDeviceName(event.target.value)}
                    placeholder={t("deviceNamePlaceholder", { deviceType: deviceType.title })}
                    type="text"
                    value={deviceName}
                  />
                  <div className="absolute right-4 top-1/2 -translate-y-1/2 text-on-surface-variant/30">✎</div>
                </div>
                <p className="mt-2 ml-1 text-[11px] uppercase tracking-wider text-on-surface-variant">
                  {t("deviceNameHelp")}
                </p>
              </div>

              <div>
                <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="timezone">
                  {t("timezoneSelection")}
                </label>
                <div className="relative">
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                    id="timezone"
                    list="timezone-options"
                    onChange={(event) => setTimezone(event.target.value)}
                    placeholder={t("timezonePlaceholder")}
                    type="text"
                    value={timezone}
                  />
                  <div className="absolute right-4 top-1/2 -translate-y-1/2 text-on-surface-variant/50">🕘</div>
                </div>
                <datalist id="timezone-options">
                  {availableTimezones.map((option) => (
                    <option key={option} value={option} />
                  ))}
                </datalist>
                <p className="mt-2 ml-1 text-[11px] uppercase tracking-wider text-on-surface-variant">
                  {t("timezoneHelp")}
                </p>
                {!timezoneIsValid ? (
                  <p className="mt-2 ml-1 text-sm text-on-error-container">
                    {t("invalidTimezone")}
                  </p>
                ) : null}
              </div>

              {isHeatPump ? (
                <>
                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="hp-name">
                      {t("heatPumpDeviceName")}
                    </label>
                    <div className="relative">
                      <input
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                        id="hp-name"
                        onChange={(event) => setHpName(event.target.value)}
                        placeholder={t("heatPumpDevicePlaceholder")}
                        type="text"
                        value={hpName}
                      />
                    </div>
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="ac-username">
                      {t("heatPumpUsername")}
                    </label>
                    <div className="relative">
                      <input
                        autoComplete="username"
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                        id="ac-username"
                        onChange={(event) => setAcUsername(event.target.value)}
                        placeholder={deviceType.id === "toshiba-heat-pump" ? t("toshibaUsernamePlaceholder") : t("mitsubishiUsernamePlaceholder")}
                        type="text"
                        value={acUsername}
                      />
                    </div>
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="ac-password">
                      {t("heatPumpPassword")}
                    </label>
                    <div className="relative">
                      <input
                        autoComplete="current-password"
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                        id="ac-password"
                        onChange={(event) => setAcPassword(event.target.value)}
                        placeholder={t("heatPumpPasswordPlaceholder")}
                        type="password"
                        value={acPassword}
                      />
                    </div>
                  </div>

                  <div className="rounded-xl bg-surface-container-low p-6">
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p className="font-headline text-lg font-bold text-on-surface">{t("chooseAcDevice")}</p>
                        <p className="text-sm text-on-surface-variant">
                          {t("chooseAcDescription")}
                        </p>
                      </div>
                      <button
                        className="secondary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                        disabled={!hasAcCredentials || isLoadingAcDevices}
                        onClick={handleOpenAcSelection}
                        type="button"
                      >
                        {isLoadingAcDevices ? common("loading") : t("chooseAcDevice")}
                      </button>
                    </div>
                    {!hasAcCredentials ? (
                      <p className="mt-4 text-sm text-on-surface-variant">
                        {t("credentialsRequired")}
                      </p>
                    ) : null}
                    {acSelectionError ? (
                      <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                        {acSelectionError}
                      </div>
                    ) : null}
                    <div className="mt-4 rounded-xl bg-surface-container-highest p-4">
                      <p className="metric-label mb-2">{t("selectedAcDevice")}</p>
                      <p className="text-sm text-on-surface">
                        {acDeviceLabel || t("noAcDeviceSelected")}
                      </p>
                      {acDeviceId ? (
                        <p className="mt-2 text-xs text-on-surface-variant">
                          {t("deviceId", { id: acDeviceId })}
                        </p>
                      ) : null}
                    </div>
                  </div>
                </>
              ) : null}

              <div className="pt-2">
                <button
                  className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={!canContinue}
                  type="submit"
                >
                  {t("nextStep")}
                </button>
                <Link className="mt-4 block w-full py-3 text-center text-sm font-bold text-primary/60 transition-colors hover:text-primary" to="/devices/add/type">
                  {common("back")}
                </Link>
              </div>
            </form>
        </section>
      </main>

      {isAcDialogOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-on-surface/35 px-4 py-8">
          <div className="app-card w-full max-w-2xl p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="metric-label mb-2">{t("heatPumpAccount")}</p>
                <h2 className="font-headline text-2xl font-bold text-on-surface">
                  {t("selectAcDevice")}
                </h2>
                <p className="mt-2 text-sm text-on-surface-variant">
                  {t("chooseReturnedDevice", { deviceType: deviceType.title })}
                </p>
              </div>
              <button
                className="secondary-action"
                onClick={() => setIsAcDialogOpen(false)}
                type="button"
              >
                {common("close")}
              </button>
            </div>

            <div className="mt-6 max-h-[60vh] space-y-3 overflow-y-auto">
              {selectableAcDevices.length > 0 ? selectableAcDevices.map((device) => (
                <button
                  key={[device.id, device.buildingId ?? "", device.deviceUniqueId ?? ""].join(":")}
                  className="w-full rounded-xl border border-outline-variant/50 bg-surface-container-low p-4 text-left transition-colors hover:bg-surface-container"
                  onClick={() => handleSelectAcDevice(device)}
                  type="button"
                >
                  <p className="font-headline text-lg font-bold text-on-surface">{device.name}</p>
                  <p className="mt-2 text-sm text-on-surface-variant">{t("deviceId", { id: device.id })}</p>
                  {device.buildingId ? (
                    <p className="mt-1 text-sm text-on-surface-variant">{t("buildingId", { id: device.buildingId })}</p>
                  ) : null}
                  {device.deviceUniqueId ? (
                    <p className="mt-1 text-sm text-on-surface-variant">{t("uniqueId", { id: device.deviceUniqueId })}</p>
                  ) : null}
                </button>
              )) : (
                <div className="rounded-xl bg-surface-container-low p-5 text-sm text-on-surface-variant">
                  {t("noSelectableDevices")}
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

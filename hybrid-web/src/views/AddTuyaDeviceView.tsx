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
import { getAvailableTimezones, getCurrentTimezone } from "@/lib/add-device-flow";
import { useI18n } from "@/lib/i18n";
import {
  fetchTuyaConnections, fetchTuyaDevices, fetchTuyaHomes, importTuyaDevice, TuyaRequestError,
  type TuyaCloudDevice, type TuyaConnection, type TuyaHome
} from "@/lib/tuya";
import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

const fieldClass = "w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface disabled:opacity-60";
const actionClass = "secondary-action justify-center disabled:opacity-60";

export default function AddTuyaDeviceView() {
  const { t, locale } = useI18n("tuya");
  const common = useI18n("common").t;
  const navigate = useNavigate();
  const location = useLocation();
  const timezones = useMemo(() => [...new Set(["UTC", ...getAvailableTimezones()])], []);
  const [connections, setConnections] = useState<TuyaConnection[] | null>(null);
  const [connectionId, setConnectionId] = useState<number | null>(location.state?.tuyaConnectionId ?? null);
  const [homes, setHomes] = useState<TuyaHome[] | null>(null);
  const [homeId, setHomeId] = useState("");
  const [devices, setDevices] = useState<TuyaCloudDevice[] | null>(null);
  const [externalId, setExternalId] = useState("");
  const [name, setName] = useState("");
  const [timezone, setTimezone] = useState(getCurrentTimezone);
  const [enabled, setEnabled] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [saveError, setSaveError] = useState("");
  const isLoading = !loadError && (!connections || (connectionId !== null && !homes) || (homeId !== "" && !devices));

  useEffect(() => {
    const controller = new AbortController();
    fetchTuyaConnections(controller.signal).then((result) => {
      if (controller.signal.aborted) return;
      const connected = result.filter((connection) => connection.connected);
      setConnections(connected);
      setConnectionId((id) => connected.some((connection) => connection.id === id)
        ? id : connected.length === 1 ? connected[0].id : null);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setLoadError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
      }
    });
    return () => controller.abort();
  }, [loadAttempt]);

  useEffect(() => {
    if (!connections || connectionId === null) return;
    const controller = new AbortController();
    fetchTuyaHomes(connectionId, controller.signal).then((result) => {
      if (controller.signal.aborted) return;
      setHomes(result);
      if (result.length === 1) setHomeId(result[0].id);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setLoadError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
      }
    });
    return () => controller.abort();
  }, [connections, connectionId]);

  useEffect(() => {
    if (connectionId === null || !homeId) return;
    const controller = new AbortController();
    fetchTuyaDevices(connectionId, homeId, controller.signal).then((result) => {
      if (!controller.signal.aborted) {
        setDevices([...result].sort((left, right) => left.name.localeCompare(right.name, locale, { numeric: true })));
      }
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setLoadError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
      }
    });
    return () => controller.abort();
  }, [connectionId, homeId, locale]);

  function clearDeviceSelection() {
    setDevices(null);
    setExternalId("");
    setName("");
    setLoadError("");
    setSaveError("");
  }

  function clearHomeSelection() {
    setHomes(null);
    setHomeId("");
    clearDeviceSelection();
  }

  return (
    <>
      <PageHeader title="Tuya Smart" compact
        rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} />
      <main className="app-page max-w-3xl space-y-5 pb-8 pt-4 sm:py-8">
        <form className="app-card space-y-5 p-4 sm:p-6" onSubmit={async (event) => {
          event.preventDefault();
          if (saving || connectionId === null || !externalId || !name.trim() || !timezone.trim()) return;
          setSaving(true);
          setSaveError("");
          try {
            const { deviceId } = await importTuyaDevice({ connectionId, externalId, name: name.trim(), timezone: timezone.trim(), enabled });
            navigate(`/devices/${deviceId}`, { replace: true });
          } catch (failure) {
            setSaveError(failure instanceof TuyaRequestError ? failure.message : t("importFailed"));
          } finally {
            setSaving(false);
          }
        }}>
          <h1 className="font-headline text-2xl font-bold text-primary">{t("importTitle")}</h1>
          <p className="text-sm text-on-surface-variant">{t("importHelp")}</p>
          {connections?.length === 0 && <p>{t("noLinkedAccounts")}</p>}
          <div className="flex flex-wrap gap-3">
            <Link className={actionClass} to="/tuya/accounts?from=add-device">{t("manageAccounts")}</Link>
            {(connectionId !== null || loadError) && (
              <button className={actionClass} disabled={isLoading || saving} onClick={() => {
                clearHomeSelection();
                setConnections(null);
                setLoadAttempt((attempt) => attempt + 1);
              }} type="button">{t(loadError ? "retry" : "refreshDiscovery")}</button>
            )}
          </div>
          {connections && connections.length > 0 && (
            <fieldset className="min-w-0 space-y-5" disabled={saving}>
              <label className="block space-y-2 text-sm font-semibold">
                <span>{t("account")}</span>
                <select className={fieldClass} value={connectionId ?? ""} onChange={(event) => {
                  clearHomeSelection();
                  setConnectionId(event.target.value ? Number(event.target.value) : null);
                }}>
                  <option value="">{t("chooseAccount")}</option>
                  {connections.map((connection) => <option key={connection.id} value={connection.id}>{connection.label}</option>)}
                </select>
              </label>
              {homes?.length === 0 && <p>{t("noHomes")}</p>}
              {homes && homes.length > 0 && (
                <label className="block space-y-2 text-sm font-semibold">
                  <span>{t("home")}</span>
                  <select className={fieldClass} value={homeId} onChange={(event) => {
                    clearDeviceSelection();
                    setHomeId(event.target.value);
                  }}>
                    <option value="">{t("chooseHome")}</option>
                    {homes.map((home) => <option key={home.id} value={home.id}>{home.name || home.id}</option>)}
                  </select>
                </label>
              )}
              {devices?.length === 0 && <p>{t("noDevices")}</p>}
              {devices && devices.length > 0 && (
                <label className="block space-y-2 text-sm font-semibold">
                  <span>{t("device")}</span>
                  <select className={fieldClass} value={externalId} onChange={(event) => {
                    const selected = devices.find((device) => device.id === event.target.value);
                    setExternalId(selected?.id ?? "");
                    setName(selected?.name.slice(0, 255) ?? "");
                    setSaveError("");
                  }}>
                    <option value="">{t("chooseDevice")}</option>
                    {devices.map((device) => (
                      <option key={device.id} value={device.id}>
                        {device.name || device.id}{device.category && ` (${device.category})`}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {externalId && (
                <>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="block space-y-2 text-sm font-semibold">
                      <span>{t("deviceName")}</span>
                      <input className={fieldClass} maxLength={255} required value={name} onChange={(event) => setName(event.target.value)} />
                    </label>
                    <label className="block space-y-2 text-sm font-semibold">
                      <span>{common("timezone")}</span>
                      <input className={fieldClass} list="tuya-timezones" required value={timezone} onChange={(event) => setTimezone(event.target.value)} />
                      <datalist id="tuya-timezones">
                        {timezones.map((option) => <option key={option} value={option} />)}
                      </datalist>
                    </label>
                  </div>
                  <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4">
                    <span className="text-sm font-semibold">{common("enabled")}</span>
                    <input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" />
                  </label>
                  <button className="primary-action justify-center disabled:opacity-60"
                    disabled={saving || !name.trim() || !timezone.trim()} type="submit">
                    {t(saving ? "importing" : "importDevice")}
                  </button>
                </>
              )}
            </fieldset>
          )}
          {isLoading && <p className="text-sm" role="status">{t(connections ? "working" : "loadingAccounts")}</p>}
          {(saveError || loadError) && (
            <p className="rounded-xl bg-error-container/50 p-3 text-sm text-on-error-container" role="alert">{saveError || loadError}</p>
          )}
        </form>
        <Link className={actionClass} to="/devices/add/type">{common("back")}</Link>
      </main>
    </>
  );
}

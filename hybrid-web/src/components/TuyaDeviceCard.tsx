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

import type { ApiDevice } from "@/lib/devices";
import { getCurrentIntlLocales, useI18n } from "@/lib/i18n";
import {
  fetchTuyaDeviceConnection, pollTuyaDevice, switchTuyaRelay, TuyaRequestError, type TuyaConnection, type TuyaSnapshot
} from "@/lib/tuya";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

export default function TuyaDeviceCard({ device, disabled, onStatusChanged }: {
  device: ApiDevice;
  disabled: boolean;
  onStatusChanged: () => Promise<void>;
}) {
  const { t, group } = useI18n("tuya");
  const readingLabels: Record<string, string> = group("readingLabels");
  const [connection, setConnection] = useState<TuyaConnection | null>(null);
  const [connectionError, setConnectionError] = useState("");
  const [snapshot, setSnapshot] = useState<TuyaSnapshot | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const isRelay = device.deviceType === "STANDARD";

  useEffect(() => {
    const controller = new AbortController();
    fetchTuyaDeviceConnection(device.id, controller.signal).then((result) => {
      if (!controller.signal.aborted) setConnection(result);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setConnectionError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
      }
    });
    return () => controller.abort();
  }, [device.id]);

  async function readDevice(command?: { code: string; on: boolean }) {
    setBusy(true);
    setError("");
    setFeedback("");
    try {
      const result = command ? await switchTuyaRelay(device.id, command.code, command.on) : await pollTuyaDevice(device.id);
      setSnapshot(result);
      if (command) {
        const matches = result.online && result.readings.some(
          (reading) => reading.code === command.code && reading.value === String(command.on)
        );
        setFeedback(t(matches ? "relayConfirmed" : "relayPending"));
      }
    } catch (failure) {
      setSnapshot(null);
      setError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
    } finally {
      await onStatusChanged();
      setBusy(false);
    }
  }

  function readingLabel(code: string) {
    if (/^switch(?:_[1-9]\d*)?$/.test(code)) return t("relayLabel", { code });
    return readingLabels[code] ?? code;
  }

  const actionClass = "secondary-action justify-center disabled:opacity-60";

  return (
    <section className="app-card min-w-0 space-y-4 p-4 sm:p-6" aria-labelledby="tuya-device-tools">
      <h2 className="font-headline text-2xl font-bold text-primary" id="tuya-device-tools">Tuya Smart</h2>
      {connection && (
        <p className="text-sm text-on-surface-variant">
          {t("deviceAccount", { name: connection.label })} ({t(connection.connected ? "linked" : "disconnected")})
        </p>
      )}
      <p className="text-sm text-on-surface-variant">{t("pollHelp")}</p>
      <div className="flex flex-wrap gap-3">
        <button className={actionClass} disabled={busy || disabled || connection?.connected === false}
          onClick={() => void readDevice()} type="button">{t(busy ? "working" : "pollNow")}</button>
        <Link className={actionClass} to="/tuya/accounts">{t("manageAccounts")}</Link>
      </div>
      {(error || connectionError) && (
        <p className="rounded-xl bg-error-container/50 p-3 text-sm text-on-error-container" role="alert">{error || connectionError}</p>
      )}
      {snapshot && (
        <>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-on-surface-variant" role="status">
            <span className="font-semibold">{t(snapshot.online ? "cloudOnline" : "cloudOffline")}</span>
            <span>{t("fetchedAt", { time: new Intl.DateTimeFormat(getCurrentIntlLocales(), {
              dateStyle: "medium", timeStyle: "short"
            }).format(new Date(snapshot.fetchedAt)) })}</span>
          </div>
          {snapshot.readings.length === 0 ? <p className="text-sm">{t("noReadings")}</p> : (
            <dl className="divide-y divide-outline-variant/50 rounded-xl bg-surface-container px-4">
              {snapshot.readings.map(({ code, value, unit }) => (
                <div className="flex flex-wrap justify-between gap-2 py-3 text-sm" key={code}>
                  <dt>{readingLabel(code)}</dt>
                  <dd className="font-semibold">
                    {value === "" ? t("unavailable") : value === "true" ? t("on") : value === "false" ? t("off") : [value, unit].filter(Boolean).join(" ")}
                  </dd>
                </div>
              ))}
            </dl>
          )}
          {isRelay && snapshot.writableRelays.length === 0 && <p className="text-sm">{t("noWritableRelays")}</p>}
          {isRelay && snapshot.online && device.enabled && snapshot.writableRelays.length > 0 && (
            <div className="space-y-3">
              <p className="text-sm text-on-surface-variant">{t("manualSwitchHelp")}</p>
              {snapshot.writableRelays.map((code) => (
                <fieldset className="flex flex-wrap gap-3" disabled={busy || disabled} key={code}>
                  <legend className="mb-2 text-sm font-semibold">{readingLabel(code)}</legend>
                  <button className={actionClass} onClick={() => void readDevice({ code, on: true })} type="button">{t("switchOn")}</button>
                  <button className={actionClass} onClick={() => void readDevice({ code, on: false })} type="button">{t("switchOff")}</button>
                </fieldset>
              ))}
            </div>
          )}
          {feedback && <p className="text-sm font-semibold text-primary" role="status">{feedback}</p>}
        </>
      )}
    </section>
  );
}

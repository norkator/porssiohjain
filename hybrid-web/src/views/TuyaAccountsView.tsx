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
import { useI18n } from "@/lib/i18n";
import {
  beginTuyaLogin, cancelTuyaLogin, disconnectTuyaConnection, fetchTuyaConnections,
  finishTuyaLogin, renameTuyaConnection, TuyaRequestError, type TuyaConnection
} from "@/lib/tuya";
import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

const fieldClass = "w-full rounded-lg bg-surface-container-highest px-4 py-3 text-on-surface";
const actionClass = "secondary-action justify-center disabled:opacity-60";

export default function TuyaAccountsView() {
  const { t } = useI18n("tuya");
  const common = useI18n("common").t;
  const [searchParams] = useSearchParams();
  const fromAddDevice = searchParams.get("from") === "add-device";
  const [connections, setConnections] = useState<TuyaConnection[] | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [label, setLabel] = useState("");
  const [userCode, setUserCode] = useState("");
  const [showLinkForm, setShowLinkForm] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [qr, setQr] = useState<{ id: string; expiresAt: string; image: string } | null>(null);
  const [qrExpired, setQrExpired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const selected = connections?.find((connection) => connection.id === selectedId);
  const linking = !selected?.connected || showLinkForm;

  useEffect(() => {
    const controller = new AbortController();
    fetchTuyaConnections(controller.signal).then((result) => {
      if (controller.signal.aborted) return;
      setConnections(result);
      setSelectedId(result[0]?.id ?? null);
      setLabel(result[0]?.label ?? "");
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
      }
    });
    return () => controller.abort();
  }, [loadAttempt]);

  useEffect(() => {
    if (!qr) return;
    const timeout = window.setTimeout(() => setQrExpired(true), Math.max(0, Date.parse(qr.expiresAt) - Date.now()));
    return () => window.clearTimeout(timeout);
  }, [qr]);

  function clearQr() {
    if (qr) void cancelTuyaLogin(qr.id).catch(() => undefined);
    setQr(null);
    setQrExpired(false);
  }

  function selectAccount(connection?: TuyaConnection) {
    clearQr();
    setSelectedId(connection?.id ?? null);
    setLabel(connection?.label ?? "");
    setUserCode("");
    setShowLinkForm(false);
    setConfirmDisconnect(false);
    setError("");
    setFeedback("");
  }

  function saveConnection(connection: TuyaConnection) {
    setConnections((current) => [
      ...(current ?? []).filter((item) => item.id !== connection.id),
      connection
    ].sort((left, right) => left.id - right.id));
    setSelectedId(connection.id);
    setLabel(connection.label);
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    setFeedback("");
    try {
      await action();
    } catch (failure) {
      setError(failure instanceof TuyaRequestError ? failure.message : t("requestFailed"));
    } finally {
      setBusy(false);
    }
  }

  function generateQr() {
    clearQr();
    return run(async () => {
      const attempt = await beginTuyaLogin(selectedId, label.trim(), userCode.trim());
      try {
        const image = await QRCode.toDataURL(attempt.payload, { width: 256, margin: 4, errorCorrectionLevel: "M" });
        setQr({ id: attempt.id, expiresAt: attempt.expiresAt, image });
        setQrExpired(Date.parse(attempt.expiresAt) <= Date.now());
      } catch {
        void cancelTuyaLogin(attempt.id).catch(() => undefined);
        setError(t("qrFailed"));
      }
    });
  }

  return (
    <>
      <PageHeader title={t("accountsTitle")} compact
        rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} />
      <main className="app-page max-w-3xl space-y-5 pb-8 pt-4 sm:py-8">
        <section className="app-card space-y-5 p-4 sm:p-6">
          <h1 className="sr-only">{t("accountsTitle")}</h1>
          {connections === null ? (
            error ? (
              <button className={actionClass} onClick={() => { setError(""); setLoadAttempt(loadAttempt + 1); }} type="button">
                {t("retry")}
              </button>
            ) : <p role="status">{t("loadingAccounts")}</p>
          ) : (
            <fieldset className="min-w-0 space-y-5" disabled={busy}>
              {connections.length > 0 && (
                <label className="block space-y-2 text-sm font-semibold">
                  <span>{t("account")}</span>
                  <select className={fieldClass} value={selectedId ?? ""} onChange={(event) =>
                    selectAccount(connections.find((connection) => String(connection.id) === event.target.value))}>
                    {connections.map((connection) => (
                      <option key={connection.id} value={connection.id}>
                        {connection.label} ({t(connection.connected ? "linked" : "disconnected")})
                      </option>
                    ))}
                    <option value="">{t("addAccount")}</option>
                  </select>
                </label>
              )}

              <form className="space-y-4" onSubmit={(event) => {
                event.preventDefault();
                if (!label.trim() || (linking && !userCode.trim())) return;
                if (linking) void generateQr();
                else if (selected && label.trim() !== selected.label) {
                  void run(async () => {
                    saveConnection(await renameTuyaConnection(selected.id, label.trim()));
                    setFeedback(t("nameSaved"));
                  });
                }
              }}>
                {linking && selected && <p className="text-sm text-on-surface-variant">{t("reconnectHelp")}</p>}
                <label className="block space-y-2 text-sm font-semibold">
                  <span>{t("accountName")}</span>
                  <input className={fieldClass} maxLength={255} required value={label}
                    onChange={(event) => { clearQr(); setLabel(event.target.value); }} />
                </label>
                {linking && (
                  <label className="block space-y-2 text-sm font-semibold">
                    <span>{t("userCode")}</span>
                    <input aria-describedby="tuya-user-code-help" autoComplete="off" className={fieldClass}
                      maxLength={128} required spellCheck={false} value={userCode}
                      onChange={(event) => { clearQr(); setUserCode(event.target.value); }} />
                    <span className="block font-normal text-on-surface-variant" id="tuya-user-code-help">{t("userCodeHelp")}</span>
                  </label>
                )}
                <button className="primary-action justify-center disabled:opacity-60"
                  disabled={!label.trim() || (linking ? !userCode.trim() : label.trim() === selected?.label)} type="submit">
                  {t(linking ? "generateQr" : "saveName")}
                </button>
              </form>

              {qr && (
                <div className="space-y-4 rounded-xl bg-surface-container p-4">
                  {qrExpired ? <p role="status">{t("qrExpired")}</p> : (
                    <>
                      <p className="text-sm text-on-surface-variant">{t("scanHelp")}</p>
                      <img alt={t("qrAlt")} className="h-auto max-w-full rounded-lg" height={256} src={qr.image} width={256} />
                      <button className="primary-action justify-center" onClick={() => void run(async () => {
                        const connection = await finishTuyaLogin(qr.id);
                        saveConnection(connection);
                        setQr(null);
                        setUserCode("");
                        setShowLinkForm(false);
                        setFeedback(t("accountLinked", { name: connection.label }));
                      })} type="button">{t("completeLinking")}</button>
                    </>
                  )}
                </div>
              )}

              {selected?.connected && !linking && (
                confirmDisconnect ? (
                  <div className="space-y-3 rounded-xl bg-error-container/50 p-4">
                    <p className="text-sm text-on-error-container">{t("disconnectHelp", { name: selected.label })}</p>
                    <div className="flex flex-wrap gap-3">
                      <button className={actionClass} onClick={() => void run(async () => {
                        await disconnectTuyaConnection(selected.id);
                        saveConnection({ ...selected, connected: false });
                        setConfirmDisconnect(false);
                        setFeedback(t("disconnected"));
                      })} type="button">{t("disconnect")}</button>
                      <button className={actionClass} onClick={() => setConfirmDisconnect(false)} type="button">{common("cancel")}</button>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-wrap gap-3 border-t border-outline-variant/50 pt-4">
                    <button className={actionClass} onClick={() => setShowLinkForm(true)} type="button">{t("reconnect")}</button>
                    <button className={actionClass} onClick={() => setConfirmDisconnect(true)} type="button">{t("disconnect")}</button>
                  </div>
                )
              )}
              {showLinkForm && (
                <button className={actionClass} onClick={() => selectAccount(selected)} type="button">{common("cancel")}</button>
              )}
            </fieldset>
          )}
          {busy && <p className="text-sm" role="status">{t("working")}</p>}
          {error && <p className="rounded-xl bg-error-container/50 p-3 text-sm text-on-error-container" role="alert">{error}</p>}
          {feedback && <p className="rounded-xl bg-primary-fixed p-3 text-sm font-semibold text-primary" role="status">{feedback}</p>}
        </section>
        <Link className={actionClass} to={fromAddDevice ? "/devices/add/tuya" : "/menu"}
          state={fromAddDevice && selected?.connected ? { tuyaConnectionId: selected.id } : undefined}>
          {fromAddDevice ? t("backToImport") : common("back")}
        </Link>
      </main>
    </>
  );
}

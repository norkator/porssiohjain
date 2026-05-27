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

import QRCode from "qrcode";
import { FormEvent, useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import HeaderLogo from "@/components/HeaderLogo";
import { loginWithCredentials } from "@/lib/auth";
import { getCurrentLocale, setCurrentLocale, supportedLocales, syncDocumentLocale, type SupportedLocale, useI18n } from "@/lib/i18n";
import { cancelQrLoginChallenge, completeQrLoginChallenge, createQrLoginChallenge, isQrLoginComplete, type QrLoginChallenge } from "@/lib/qr-login";
import { getSessionData, setBrowserSession } from "@/lib/session";

const DEMO_UUID = "78b7823f-d5cc-4376-8910-cd62e7b32400";
const DEMO_SECRET = "103058b63f9245099d0c30d81e1636bc";

export default function LoginView() {
  const navigate = useNavigate();
  const session = getSessionData();
  const { t } = useI18n("login");
  const common = useI18n("common").t;
  const [uuid, setUuid] = useState("");
  const [secret, setSecret] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedLocale, setSelectedLocale] = useState<SupportedLocale>(getCurrentLocale());
  const [qrChallenge, setQrChallenge] = useState<QrLoginChallenge | null>(null);
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string | null>(null);
  const [qrError, setQrError] = useState<string | null>(null);
  const [qrStatus, setQrStatus] = useState<string | null>(null);
  const [isQrStarting, setIsQrStarting] = useState(false);

  useEffect(() => {
    if (!qrChallenge) {
      setQrCodeDataUrl(null);
      return;
    }

    let active = true;

    QRCode.toDataURL(qrChallenge.qrPayload, {
      errorCorrectionLevel: "M",
      margin: 2,
      scale: 8
    })
      .then((dataUrl) => {
        if (!active) return;
        setQrCodeDataUrl(dataUrl);
      })
      .catch(() => {
        if (!active) return;
        setQrError(t("qrRenderFailed"));
      });

    return () => {
      active = false;
    };
  }, [qrChallenge]);

  useEffect(() => {
    if (!qrChallenge) {
      return;
    }

    let active = true;
    let timeoutId: number | undefined;

    const poll = async () => {
      try {
        if (new Date(qrChallenge.expiresAt).getTime() <= Date.now()) {
          setQrStatus(t("qrExpired"));
          return;
        }

        const result = await completeQrLoginChallenge(qrChallenge.challengeId, qrChallenge.browserSecret);

        if (!active) return;

        if (isQrLoginComplete(result)) {
          setBrowserSession({
            token: result.token,
            accountId: result.accountId,
            locale: result.locale ?? getCurrentLocale()
          });
          syncDocumentLocale(result.locale);
          navigate("/menu", { replace: true });
          return;
        }

        setQrStatus(t("qrWaiting"));
        timeoutId = window.setTimeout(poll, qrChallenge.pollIntervalMs || 1500);
      } catch (pollError) {
        if (!active) return;
        setQrError(pollError instanceof Error ? pollError.message : t("qrLoginFailed"));
      }
    };

    timeoutId = window.setTimeout(poll, qrChallenge.pollIntervalMs || 1500);

    return () => {
      active = false;
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId);
      }
    };
  }, [navigate, qrChallenge]);

  if (session.source === "android" || session.hasToken) {
    return <Navigate replace to="/menu" />;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await loginWithCredentials({ uuid: uuid.trim(), secret: secret.trim() });
      navigate("/menu", { replace: true });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : t("genericLoginFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const fillDemoAccount = () => {
    setUuid(DEMO_UUID);
    setSecret(DEMO_SECRET);
  };

  const handleLocaleChange = (locale: SupportedLocale) => {
    setCurrentLocale(locale);
    setSelectedLocale(locale);
  };

  const handleQrLogin = async () => {
    setQrError(null);
    setQrStatus(null);
    setIsQrStarting(true);

    try {
      const challenge = await createQrLoginChallenge();
      setQrChallenge(challenge);
      setQrStatus(t("qrWaiting"));
    } catch (challengeError) {
      setQrError(challengeError instanceof Error ? challengeError.message : t("qrLoginFailed"));
    } finally {
      setIsQrStarting(false);
    }
  };

  const closeQrDialog = () => {
    if (qrChallenge) {
      void cancelQrLoginChallenge(qrChallenge.challengeId, qrChallenge.browserSecret).catch(() => undefined);
    }

    setQrChallenge(null);
    setQrError(null);
    setQrStatus(null);
  };

  return (
    <main className="relative flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <label className="absolute right-4 top-4 flex items-center gap-2 rounded-full border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm shadow-soft sm:right-6 sm:top-6">
        <span className="sr-only">{t("languageLabel")}</span>
        <span aria-hidden="true" className="font-label font-bold text-on-surface-variant">{t("languageShortLabel")}</span>
        <select
          className="bg-transparent font-label font-bold text-primary-container outline-none"
          onChange={(event) => handleLocaleChange(event.target.value as SupportedLocale)}
          value={selectedLocale}
        >
          {supportedLocales.map((locale) => (
            <option key={locale.code} value={locale.code}>
              {locale.label}
            </option>
          ))}
        </select>
      </label>

      <section className="app-card w-full max-w-md p-4 sm:p-6 lg:p-8">
        <div className="mb-8 flex items-center gap-4">
          <HeaderLogo />
          <div>
            <p className="metric-label">{t("brand")}</p>
            <h1 className="font-headline text-3xl font-extrabold text-primary-container">{t("title")}</h1>
          </div>
        </div>

        <form className="space-y-5" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-2 block font-label text-sm font-bold text-on-surface-variant">{t("uuidLabel")}</span>
            <input
              autoComplete="username"
              className="w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 font-mono text-sm outline-none transition-colors focus:border-primary"
              onChange={(event) => setUuid(event.target.value)}
              required
              type="text"
              value={uuid}
            />
          </label>

          <label className="block">
            <span className="mb-2 block font-label text-sm font-bold text-on-surface-variant">{t("secretLabel")}</span>
            <input
              autoComplete="current-password"
              className="w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 font-mono text-sm outline-none transition-colors focus:border-primary"
              onChange={(event) => setSecret(event.target.value)}
              required
              type="password"
              value={secret}
            />
          </label>

          {error ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
              {t("loginFailed", { error })}
            </div>
          ) : null}

          <button className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSubmitting} type="submit">
            {isSubmitting ? t("loggingIn") : t("login")}
          </button>
        </form>

        <div className="mt-4">
          <button
            className="secondary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60"
            disabled={isQrStarting}
            onClick={handleQrLogin}
            type="button"
          >
            {isQrStarting ? t("qrStarting") : t("loginWithQr")}
          </button>
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-4 text-sm">
          <button className="font-label font-bold text-primary-container underline" onClick={fillDemoAccount} type="button">
            {t("useDemoAccount")}
          </button>
          <Link className="font-label font-bold text-primary-container underline" to="/create-account">
            {t("createAccount")}
          </Link>
        </div>

        <hr className="mt-6 border-outline-variant" />

        <p className="mt-4 text-sm leading-relaxed text-on-surface-variant">
          {common("licenseText")}{" "}
          <a
            className="font-label font-bold text-primary-container underline"
            href="https://github.com/norkator/porssiohjain"
            rel="noreferrer"
            target="_blank"
          >
            {common("docLink")}
          </a>
        </p>
      </section>

      {qrChallenge ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 py-8">
          <section className="app-card w-full max-w-sm p-5 shadow-2xl sm:p-6" role="dialog" aria-modal="true" aria-labelledby="qr-login-title">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <p className="metric-label">{t("qrEyebrow")}</p>
                <h2 id="qr-login-title" className="font-headline text-2xl font-extrabold text-primary-container">
                  {t("qrTitle")}
                </h2>
              </div>
              <button
                aria-label={common("close")}
                className="flex h-10 w-10 items-center justify-center rounded-full border border-outline-variant text-xl font-bold text-on-surface-variant transition-colors hover:border-primary hover:text-primary"
                onClick={closeQrDialog}
                type="button"
              >
                x
              </button>
            </div>

            <div className="rounded-xl border border-outline-variant bg-white p-3">
              {qrCodeDataUrl ? (
                <img alt={t("qrImageAlt")} className="aspect-square w-full" src={qrCodeDataUrl} />
              ) : (
                <div className="flex aspect-square w-full items-center justify-center text-sm text-on-surface-variant">
                  {common("loading")}
                </div>
              )}
            </div>

            <p className="mt-4 text-sm leading-6 text-on-surface-variant">
              {t("qrDescription")}
            </p>

            {qrStatus ? (
              <p className="mt-4 rounded-xl bg-surface-container-high px-4 py-3 text-sm font-semibold text-on-surface-variant">
                {qrStatus}
              </p>
            ) : null}

            {qrError ? (
              <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                {t("loginFailed", { error: qrError })}
              </div>
            ) : null}

            <button className="secondary-action mt-5 w-full justify-center" onClick={closeQrDialog} type="button">
              {common("cancel")}
            </button>
          </section>
        </div>
      ) : null}
    </main>
  );
}

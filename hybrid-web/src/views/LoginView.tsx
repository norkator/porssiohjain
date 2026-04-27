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

import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import HeaderLogo from "@/components/HeaderLogo";
import { loginWithCredentials } from "@/lib/auth";
import { getCurrentLocale, setCurrentLocale, supportedLocales, type SupportedLocale, useI18n } from "@/lib/i18n";
import { getSessionData } from "@/lib/session";

const DEMO_UUID = "78b7823f-d5cc-4376-8910-cd62e7b32400";
const DEMO_SECRET = "103058b63f9245099d0c30d81e1636bc";

export default function LoginView() {
  const navigate = useNavigate();
  const session = getSessionData();
  const { t } = useI18n("login");
  const [uuid, setUuid] = useState("");
  const [secret, setSecret] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [selectedLocale, setSelectedLocale] = useState<SupportedLocale>(getCurrentLocale());

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

      <section className="app-card w-full max-w-md p-8">
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

        <div className="mt-6 flex flex-wrap items-center justify-between gap-4 text-sm">
          <button className="font-label font-bold text-primary-container underline" onClick={fillDemoAccount} type="button">
            {t("useDemoAccount")}
          </button>
          <Link className="font-label font-bold text-primary-container underline" to="/create-account">
            {t("createAccount")}
          </Link>
        </div>
      </section>
    </main>
  );
}

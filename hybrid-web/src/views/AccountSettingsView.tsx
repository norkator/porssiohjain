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
import { fetchMe, updateMe, type AccountTier } from "@/lib/account";
import { setCurrentLocale, supportedLocales, useI18n } from "@/lib/i18n";
import { setDevSessionOverride } from "@/lib/session";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";

function getTierTone(tier: AccountTier) {
  switch (tier) {
    case "FREE":
      return "bg-slate-500";
    case "PRO":
      return "bg-sky-600";
    case "BUSINESS":
      return "bg-emerald-600";
  }
}

export default function AccountSettingsView() {
  const { t, group } = useI18n("accountSettings");
  const common = useI18n("common").t;
  const tierLabels = group("tierLabels");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [tier, setTier] = useState<AccountTier>("FREE");
  const [email, setEmail] = useState("");
  const [locale, setLocaleValue] = useState("en");
  const [accountId, setAccountId] = useState<number | null>(null);

  useEffect(() => {
    let isActive = true;

    async function loadAccount() {
      setIsLoading(true);
      setLoadError(null);

      try {
        const response = await fetchMe();

        if (!isActive) {
          return;
        }

        setAccountId(response.accountId);
        setTier(response.tier);
        setEmail(response.email ?? "");
        setLocaleValue(response.locale || "en");
      } catch (error) {
        if (!isActive) {
          return;
        }

        setLoadError(error instanceof Error ? error.message : t("failedLoad"));
      } finally {
        if (isActive) {
          setIsLoading(false);
        }
      }
    }

    loadAccount();

    return () => {
      isActive = false;
    };
  }, []);

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSaving(true);
    setSaveError(null);
    setMessage(null);

    try {
      const response = await updateMe({
        email: email.trim(),
        locale
      });

      setTier(response.tier);
      setEmail(response.email ?? "");
      setLocaleValue(response.locale || "en");
      setCurrentLocale((response.locale || "en") as "en" | "fi");
      setDevSessionOverride({
        accountId: response.accountId,
        locale: response.locale || "en"
      });
      setMessage(t("saved"));
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : t("failedSave"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} title={t("title")} compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">{t("loading")}</div> : null}
        {loadError ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{loadError}</div> : null}
        {!isLoading && !loadError ? (
          <div className="grid gap-8 lg:grid-cols-12">
            <section className="space-y-6 lg:col-span-4">
              <div>
                <p className="metric-label mb-3">{t("eyebrow")}</p>
                <h1 className="font-headline text-4xl font-extrabold tracking-tight text-primary">{t("headline")}</h1>
                <p className="mt-3 max-w-md text-sm leading-6 text-on-surface-variant">{t("description")}</p>
              </div>

              <article className="app-card overflow-hidden">
                <div className="signature-gradient relative p-6 text-on-primary">
                  <div className="absolute -right-8 -top-8 h-28 w-28 rounded-full bg-secondary-container/20 blur-2xl" />
                  <p className="mb-2 text-xs font-bold uppercase tracking-[0.2em] text-primary-fixed">{t("tierCardEyebrow")}</p>
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <h2 className="font-headline text-3xl font-black">{tierLabels[tier]}</h2>
                      <p className="mt-2 text-sm text-primary-fixed">{t("tierDescription")}</p>
                    </div>
                    <span className={`rounded-full px-3 py-2 text-xs font-bold uppercase tracking-[0.2em] text-white ${getTierTone(tier)}`}>
                      {t("active")}
                    </span>
                  </div>
                  {accountId !== null ? <p className="mt-6 text-xs text-primary-fixed">{t("accountId", { id: accountId })}</p> : null}
                </div>
              </article>
            </section>

            <section className="space-y-6 lg:col-span-8">
              <form className="app-card space-y-8 p-6 sm:p-8" onSubmit={handleSave}>
                <div className="grid gap-6 md:grid-cols-2">
                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="account-email">
                      {t("email")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="account-email"
                      onChange={(event) => setEmail(event.target.value)}
                      placeholder={t("emailPlaceholder")}
                      type="email"
                      value={email}
                    />
                  </div>

                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="account-language">
                      {t("language")}
                    </label>
                    <select
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      id="account-language"
                      onChange={(event) => setLocaleValue(event.target.value)}
                      value={locale}
                    >
                      {supportedLocales.map((option) => (
                        <option key={option.code} value={option.code}>{option.label}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                  <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSaving} type="submit">
                    {isSaving ? t("saving") : t("save")}
                  </button>
                  <Link className="secondary-action justify-center" to="/menu">
                    {t("backToMenu")}
                  </Link>
                </div>

                {message ? <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary">{message}</div> : null}
                {saveError ? <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{saveError}</div> : null}
              </form>
            </section>
          </div>
        ) : null}
      </main>
    </>
  );
}

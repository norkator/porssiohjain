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

import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import HeaderLogo from "@/components/HeaderLogo";
import { createAccount, type CreatedAccount } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { getSessionData } from "@/lib/session";

export default function CreateAccountView() {
  const session = getSessionData();
  const { t } = useI18n("createAccount");
  const [account, setAccount] = useState<CreatedAccount | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (session.source === "android" || session.hasToken) {
    return <Navigate replace to="/menu" />;
  }

  const handleCreateAccount = async () => {
    setError(null);
    setIsSubmitting(true);

    try {
      setAccount(await createAccount());
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : t("genericFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <section className="app-card w-full max-w-2xl p-8">
        <div className="mb-8 flex items-center gap-4">
          <HeaderLogo />
          <div>
            <p className="metric-label">{t("brand")}</p>
            <h1 className="font-headline text-3xl font-extrabold text-primary-container">{t("title")}</h1>
          </div>
        </div>

        <p className="mb-8 whitespace-pre-line text-on-surface-variant">
          {t("description")}
        </p>

        {account ? (
          <div className="space-y-6">
            <div className="rounded-xl border border-primary-fixed bg-primary-fixed/30 p-5">
              <h2 className="mb-4 font-headline text-xl font-bold text-primary-container">{t("successTitle")}</h2>
              <div className="space-y-3 break-all rounded-lg bg-surface-container-lowest p-4 font-mono text-sm">
                <p><span className="font-bold">{t("uuid")}</span> {account.uuid}</p>
                <p><span className="font-bold">{t("secret")}</span> {account.secret}</p>
              </div>
              <p className="mt-4 text-sm font-semibold text-on-error-container">
                {t("saveWarning")}
              </p>
            </div>
            <Link className="primary-action justify-center" to="/login">
              {t("continueToLogin")}
            </Link>
          </div>
        ) : (
          <div className="space-y-5">
            {error ? (
              <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                {t("failed", { error })}
              </div>
            ) : null}

            <button className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSubmitting} onClick={handleCreateAccount} type="button">
              {isSubmitting ? t("creating") : t("create")}
            </button>

            <Link className="secondary-action justify-center" to="/login">
              {t("backToLogin")}
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}

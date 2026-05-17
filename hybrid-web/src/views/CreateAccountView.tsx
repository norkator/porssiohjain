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

import { Fragment, type ReactNode, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import HeaderLogo from "@/components/HeaderLogo";
import { createAccount, fetchTermsOfService, type CreatedAccount, type TermsOfService } from "@/lib/auth";
import { useI18n } from "@/lib/i18n";
import { getSessionData } from "@/lib/session";

function renderTermsMarkdown(markdown: string) {
  const lines = markdown.split(/\r?\n/);
  const nodes: ReactNode[] = [];
  let listItems: string[] = [];
  let paragraphLines: string[] = [];

  const flushParagraph = () => {
    if (paragraphLines.length === 0) {
      return;
    }

    nodes.push(
      <p className="leading-7 text-on-surface" key={`paragraph-${nodes.length}`}>
        {paragraphLines.join(" ")}
      </p>
    );
    paragraphLines = [];
  };

  const flushList = () => {
    if (listItems.length === 0) {
      return;
    }

    nodes.push(
      <ul className="list-disc space-y-2 pl-6 text-on-surface" key={`list-${nodes.length}`}>
        {listItems.map((item, index) => (
          <li key={`list-item-${index}`}>{item}</li>
        ))}
      </ul>
    );
    listItems = [];
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();

    if (!line) {
      flushParagraph();
      flushList();
      continue;
    }

    if (line.startsWith("# ")) {
      flushParagraph();
      flushList();
      nodes.push(<h2 className="font-headline text-2xl font-bold text-primary" key={`h1-${nodes.length}`}>{line.slice(2)}</h2>);
      continue;
    }

    if (line.startsWith("## ")) {
      flushParagraph();
      flushList();
      nodes.push(<h3 className="pt-2 font-headline text-lg font-bold text-primary-container" key={`h2-${nodes.length}`}>{line.slice(3)}</h3>);
      continue;
    }

    if (line.startsWith("- ")) {
      flushParagraph();
      listItems.push(line.slice(2));
      continue;
    }

    paragraphLines.push(line);
  }

  flushParagraph();
  flushList();

  return nodes.map((node, index) => <Fragment key={index}>{node}</Fragment>);
}

export default function CreateAccountView() {
  const session = getSessionData();
  const { locale, t } = useI18n("createAccount");
  const [account, setAccount] = useState<CreatedAccount | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isTermsOpen, setIsTermsOpen] = useState(false);
  const [isTermsLoading, setIsTermsLoading] = useState(false);
  const [termsError, setTermsError] = useState<string | null>(null);
  const [terms, setTerms] = useState<TermsOfService | null>(null);

  if (session.source === "android" || session.hasToken) {
    return <Navigate replace to="/menu" />;
  }

  const openTermsDialog = async () => {
    setError(null);
    setTermsError(null);
    setIsTermsOpen(true);
    setIsTermsLoading(true);

    try {
      setTerms(await fetchTermsOfService(locale));
    } catch (loadError) {
      setTerms(null);
      setTermsError(loadError instanceof Error ? loadError.message : t("termsLoadFailed"));
    } finally {
      setIsTermsLoading(false);
    }
  };

  const handleCreateAccount = async () => {
    setError(null);
    setIsSubmitting(true);

    try {
      setAccount(await createAccount());
      setIsTermsOpen(false);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : t("genericFailed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <section className="app-card w-full max-w-2xl p-4 sm:p-6 lg:p-8">
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

            <button className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSubmitting} onClick={openTermsDialog} type="button">
              {isSubmitting ? t("creating") : t("create")}
            </button>

            <Link className="secondary-action justify-center" to="/login">
              {t("backToLogin")}
            </Link>
          </div>
        )}
      </section>

      {isTermsOpen ? (
        <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
          <div className="w-full max-w-4xl rounded-xl bg-surface-container-lowest p-6 shadow-2xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <p className="metric-label mb-2">{t("termsEyebrow")}</p>
                <h2 className="font-headline text-2xl font-bold text-primary">{t("termsTitle")}</h2>
                <p className="mt-2 text-sm text-on-surface-variant">{t("termsDescription")}</p>
              </div>
              <button className="secondary-action px-3 py-2 text-sm" onClick={() => setIsTermsOpen(false)} type="button">
                {t("close")}
              </button>
            </div>

            {termsError ? (
              <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                {t("termsLoadFailedMessage", { error: termsError })}
              </div>
            ) : null}

            {isTermsLoading ? (
              <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">
                {t("termsLoading")}
              </div>
            ) : null}

            {terms ? (
              <div className="max-h-[60vh] space-y-4 overflow-y-auto rounded-xl bg-surface-container p-5">
                {renderTermsMarkdown(terms.markdown)}
              </div>
            ) : null}

            <div className="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button className="secondary-action justify-center" onClick={() => setIsTermsOpen(false)} type="button">
                {t("cancel")}
              </button>
              <button
                className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isSubmitting || isTermsLoading || !terms}
                onClick={handleCreateAccount}
                type="button"
              >
                {isSubmitting ? t("creating") : t("acceptTermsAndCreate")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}

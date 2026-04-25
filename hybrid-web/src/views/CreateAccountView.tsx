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
import { getSessionData } from "@/lib/session";

export default function CreateAccountView() {
  const session = getSessionData();
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
      setError(createError instanceof Error ? createError.message : "Failed to create account.");
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
            <p className="metric-label">Energy Controller</p>
            <h1 className="font-headline text-3xl font-extrabold text-primary-container">Create Porssiohjain Account</h1>
          </div>
        </div>

        <p className="mb-8 whitespace-pre-line text-on-surface-variant">
          This will create a new account for you automatically.
          {"\n"}A unique UUID and Secret will be generated.
          {"\n"}Save them carefully. They will only be shown once.
        </p>

        {account ? (
          <div className="space-y-6">
            <div className="rounded-xl border border-primary-fixed bg-primary-fixed/30 p-5">
              <h2 className="mb-4 font-headline text-xl font-bold text-primary-container">Account Created Successfully</h2>
              <div className="space-y-3 break-all rounded-lg bg-surface-container-lowest p-4 font-mono text-sm">
                <p><span className="font-bold">UUID:</span> {account.uuid}</p>
                <p><span className="font-bold">Secret:</span> {account.secret}</p>
              </div>
              <p className="mt-4 text-sm font-semibold text-on-error-container">
                Copy your UUID and Secret now. They will not be shown again after you leave this page.
              </p>
            </div>
            <Link className="primary-action justify-center" to="/login">
              Continue to login
            </Link>
          </div>
        ) : (
          <div className="space-y-5">
            {error ? (
              <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                Failed to create account: {error}
              </div>
            ) : null}

            <button className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSubmitting} onClick={handleCreateAccount} type="button">
              {isSubmitting ? "Creating account..." : "Create Account"}
            </button>

            <Link className="secondary-action justify-center" to="/login">
              Back to Login
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}

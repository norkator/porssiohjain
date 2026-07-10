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

import AppDialog from "@/components/AppDialog";
import { fetchMe, updateMe, type MeResponse } from "@/lib/account";
import { useI18n } from "@/lib/i18n";
import { marketOptions } from "@/lib/market-options";
import { type FormEvent, useEffect, useState } from "react";

function normalizeMarket(value?: string | null) {
  return value && marketOptions.some((option) => option.code === value) ? value : "FI";
}

export default function MarketIndexVerificationDialog() {
  const { t } = useI18n("marketIndexVerification");
  const [account, setAccount] = useState<MeResponse | null>(null);
  const [marketIndexName, setMarketIndexName] = useState("FI");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isActive = true;

    async function loadAccount() {
      setIsLoading(true);
      setError(null);

      try {
        const response = await fetchMe();

        if (!isActive) {
          return;
        }

        setAccount(response);
        setMarketIndexName(normalizeMarket(response.marketIndexName));
      } catch (loadError) {
        if (!isActive) {
          return;
        }

        setError(loadError instanceof Error ? loadError.message : t("failedLoad"));
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

  const shouldShow = Boolean(account && !account.demo && !account.marketIndexNameConfirmed);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!account) {
      return;
    }

    setIsSaving(true);
    setError(null);

    try {
      const response = await updateMe({
        email: account.email ?? "",
        locale: account.locale || "en",
        marketIndexName,
        marketIndexNameConfirmed: true,
        notifyPowerLimitExceeded: account.notifyPowerLimitExceeded,
        notifyControlActivated: account.notifyControlActivated,
        notifyDeviceOffline: account.notifyDeviceOffline,
        notifyDeviceOnline: account.notifyDeviceOnline,
        emailNotificationsEnabled: account.emailNotificationsEnabled,
        pushNotificationsEnabled: account.pushNotificationsEnabled
      });

      setAccount(response);
      setMarketIndexName(normalizeMarket(response.marketIndexName));
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading || !shouldShow) {
    return null;
  }

  return (
    <AppDialog
      description={t("description")}
      eyebrow={t("eyebrow")}
      isDismissible={false}
      isOpen
      maxWidthClassName="max-w-lg"
      onClose={() => undefined}
      title={t("title")}
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="market-index-verification">
            {t("market")}
          </label>
          <select
            className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
            disabled={isSaving}
            id="market-index-verification"
            onChange={(event) => setMarketIndexName(event.target.value)}
            value={marketIndexName}
          >
            {marketOptions.map((option) => (
              <option key={option.code} value={option.code}>{option.label} ({option.code})</option>
            ))}
          </select>
          <p className="mt-3 ml-1 text-xs leading-5 text-on-surface-variant">{t("marketDescription")}</p>
        </div>

        {error ? <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{error}</div> : null}

        <button className="primary-action w-full justify-center py-3" disabled={isSaving} type="submit">
          {isSaving ? t("saving") : t("confirm")}
        </button>
      </form>
    </AppDialog>
  );
}

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
import { changePassword, deleteMe, downloadAccountExport, fetchMe, updateMe, type AccountTier } from "@/lib/account";
import { logoutNative } from "@/lib/android-bridge";
import { setCurrentLocale, supportedLocales, useI18n } from "@/lib/i18n";
import { clearBrowserSession, getSessionData, setDevSessionOverride } from "@/lib/session";
import { getThemePreference, setThemePreference, type ThemePreference } from "@/lib/theme";
import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

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
  const navigate = useNavigate();
  const tierLabels = group("tierLabels");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
  const [isDeletingAccount, setIsDeletingAccount] = useState(false);
  const [isDownloadingExport, setIsDownloadingExport] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [tier, setTier] = useState<AccountTier>("FREE");
  const [email, setEmail] = useState("");
  const [locale, setLocaleValue] = useState("en");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [accountId, setAccountId] = useState<number | null>(null);
  const [isDemoAccount, setIsDemoAccount] = useState(false);
  const [themePreference, setThemePreferenceState] = useState<ThemePreference>(() => getThemePreference());
  const [deviceLimit, setDeviceLimit] = useState<number>(0);
  const [controlLimit, setControlLimit] = useState<number | null>(null);
  const [productionSourceLimit, setProductionSourceLimit] = useState<number | null>(null);
  const [weatherControlLimit, setWeatherControlLimit] = useState<number | null>(null);
  const [weeklyEmailNotificationLimit, setWeeklyEmailNotificationLimit] = useState<number>(0);
  const [weeklyPushNotificationLimit, setWeeklyPushNotificationLimit] = useState<number | null>(null);
  const [notifyPowerLimitExceeded, setNotifyPowerLimitExceeded] = useState(false);
  const [notifyControlActivated, setNotifyControlActivated] = useState(false);
  const [notifyDeviceOffline, setNotifyDeviceOffline] = useState(false);
  const [notifyDeviceOnline, setNotifyDeviceOnline] = useState(false);
  const [emailNotificationsEnabled, setEmailNotificationsEnabled] = useState(false);
  const [pushNotificationsEnabled, setPushNotificationsEnabled] = useState(false);

  const formatLimit = (limit: number | null) => (limit === null ? t("unlimited") : String(limit));
  const isValidPassword = (value: string) => value.length >= 8 && /[A-Z]/.test(value) && /[A-Za-z]/.test(value) && /\d/.test(value);
  const themeOptions: Array<{ value: ThemePreference; label: string }> = [
    { value: "light", label: t("themeLight") },
    { value: "dark", label: t("themeDark") }
  ];

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
        setIsDemoAccount(response.demo);
        setTier(response.tier);
        setEmail(response.email ?? "");
        setLocaleValue(response.locale || "en");
        setDeviceLimit(response.deviceLimit);
        setControlLimit(response.controlLimit);
        setProductionSourceLimit(response.productionSourceLimit);
        setWeatherControlLimit(response.weatherControlLimit);
        setWeeklyEmailNotificationLimit(response.weeklyEmailNotificationLimit);
        setWeeklyPushNotificationLimit(response.weeklyPushNotificationLimit);
        setNotifyPowerLimitExceeded(response.notifyPowerLimitExceeded);
        setNotifyControlActivated(response.notifyControlActivated);
        setNotifyDeviceOffline(response.notifyDeviceOffline);
        setNotifyDeviceOnline(response.notifyDeviceOnline);
        setEmailNotificationsEnabled(response.emailNotificationsEnabled);
        setPushNotificationsEnabled(response.pushNotificationsEnabled);
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
        locale,
        notifyPowerLimitExceeded,
        notifyControlActivated,
        notifyDeviceOffline,
        notifyDeviceOnline,
        emailNotificationsEnabled,
        pushNotificationsEnabled
      });

      setTier(response.tier);
      setIsDemoAccount(response.demo);
      setEmail(response.email ?? "");
      setLocaleValue(response.locale || "en");
      setDeviceLimit(response.deviceLimit);
      setControlLimit(response.controlLimit);
      setProductionSourceLimit(response.productionSourceLimit);
      setWeatherControlLimit(response.weatherControlLimit);
      setWeeklyEmailNotificationLimit(response.weeklyEmailNotificationLimit);
      setWeeklyPushNotificationLimit(response.weeklyPushNotificationLimit);
      setNotifyPowerLimitExceeded(response.notifyPowerLimitExceeded);
      setNotifyControlActivated(response.notifyControlActivated);
      setNotifyDeviceOffline(response.notifyDeviceOffline);
      setNotifyDeviceOnline(response.notifyDeviceOnline);
      setEmailNotificationsEnabled(response.emailNotificationsEnabled);
      setPushNotificationsEnabled(response.pushNotificationsEnabled);
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

  const handlePasswordChange = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsChangingPassword(true);
    setPasswordError(null);
    setPasswordMessage(null);

    if (!currentPassword.trim()) {
      setPasswordError(t("passwordCurrentRequired"));
      setIsChangingPassword(false);
      return;
    }

    if (!isValidPassword(newPassword)) {
      setPasswordError(t("passwordInvalid"));
      setIsChangingPassword(false);
      return;
    }

    if (newPassword !== confirmNewPassword) {
      setPasswordError(t("passwordMismatch"));
      setIsChangingPassword(false);
      return;
    }

    try {
      await changePassword({
        currentPassword,
        newPassword
      });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmNewPassword("");
      setPasswordMessage(t("passwordChanged"));
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : t("passwordFailed"));
    } finally {
      setIsChangingPassword(false);
    }
  };

  const handleThemeChange = (theme: ThemePreference) => {
    setThemePreference(theme);
    setThemePreferenceState(theme);
  };

  const handleDeleteAccount = async () => {
    setIsDeletingAccount(true);
    setDeleteError(null);

    try {
      await deleteMe();

      if (getSessionData().source === "android") {
        logoutNative();
        return;
      }

      clearBrowserSession();
      navigate("/login", { replace: true });
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : t("deleteFailed"));
      setIsDeletingAccount(false);
    }
  };

  const handleDownloadExport = async () => {
    setIsDownloadingExport(true);
    setExportError(null);

    try {
      await downloadAccountExport();
    } catch (error) {
      setExportError(error instanceof Error ? error.message : t("exportFailed"));
    } finally {
      setIsDownloadingExport(false);
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} title={t("title")} compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-4 text-sm text-on-surface-variant sm:p-6">{t("loading")}</div> : null}
        {loadError ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container sm:p-6">{loadError}</div> : null}
        {!isLoading && !loadError ? (
          <div className="grid gap-8 lg:grid-cols-12">
            <section className="space-y-6 lg:col-span-4">
              <div>
                <p className="metric-label mb-3">{t("eyebrow")}</p>
                <h1 className="font-headline text-4xl font-extrabold tracking-tight text-primary">{t("headline")}</h1>
                <p className="mt-3 max-w-md text-sm leading-6 text-on-surface-variant">{t("description")}</p>
              </div>

              <article className="app-card overflow-hidden">
                <div className="account-tier-card signature-gradient relative p-4 text-on-primary sm:p-6">
                  <div className="absolute -right-8 -top-8 h-28 w-28 rounded-full bg-secondary-container/20 blur-2xl" />
                  <p className="mb-2 text-xs font-bold uppercase tracking-[0.2em] text-primary-fixed">{t("tierCardEyebrow")}</p>
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <h2 className="font-headline text-3xl font-black text-white">{tierLabels[tier]}</h2>
                      <p className="mt-2 text-sm text-primary-fixed">{t("tierDescription")}</p>
                    </div>
                    <span className={`rounded-full px-3 py-2 text-xs font-bold uppercase tracking-[0.2em] text-white ${getTierTone(tier)}`}>
                      {t("active")}
                    </span>
                  </div>
                  {accountId !== null ? <p className="mt-6 text-xs text-primary-fixed">{t("accountId", { id: accountId })}</p> : null}
                </div>
              </article>

              <article className="app-card p-4 sm:p-6">
                <p className="metric-label mb-3">{t("limitsEyebrow")}</p>
                <h2 className="font-headline text-2xl font-extrabold text-primary">{t("limitsTitle")}</h2>
                <div className="mt-5 space-y-3 text-sm text-on-surface">
                  <div className="flex items-center justify-between gap-4 border-b border-outline-variant/50 pb-3">
                    <span className="text-on-surface-variant">{t("deviceLimit")}</span>
                    <span className="font-bold">{deviceLimit}</span>
                  </div>
                  <div className="flex items-center justify-between gap-4 border-b border-outline-variant/50 pb-3">
                    <span className="text-on-surface-variant">{t("controlLimit")}</span>
                    <span className="font-bold">{formatLimit(controlLimit)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-4 border-b border-outline-variant/50 pb-3">
                    <span className="text-on-surface-variant">{t("productionSourceLimit")}</span>
                    <span className="font-bold">{formatLimit(productionSourceLimit)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-4 border-b border-outline-variant/50 pb-3">
                    <span className="text-on-surface-variant">{t("weatherControlLimit")}</span>
                    <span className="font-bold">{formatLimit(weatherControlLimit)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-on-surface-variant">{t("weeklyEmailNotificationLimit")}</span>
                    <span className="font-bold">{weeklyEmailNotificationLimit}</span>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-on-surface-variant">{t("weeklyPushNotificationLimit")}</span>
                    <span className="font-bold">{formatLimit(weeklyPushNotificationLimit)}</span>
                  </div>
                </div>
              </article>
            </section>

            <section className="space-y-6 lg:col-span-8">
              {isDemoAccount ? (
                <div className="app-card border border-primary/40 bg-primary-fixed p-4 text-sm font-semibold text-primary sm:p-6">
                  {t("demoReadOnly")}
                </div>
              ) : null}

              <form className="app-card space-y-8 p-4 sm:p-6 lg:p-8" onSubmit={handleSave}>
                <div className="grid gap-6 md:grid-cols-2">
                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="account-email">
                      {t("email")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="account-email"
                      disabled={isDemoAccount}
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
                      disabled={isDemoAccount}
                      onChange={(event) => setLocaleValue(event.target.value)}
                      value={locale}
                    >
                      {supportedLocales.map((option) => (
                        <option key={option.code} value={option.code}>{option.label}</option>
                      ))}
                    </select>
                  </div>

                  <div className="md:col-span-2 space-y-3">
                    <div>
                      <p className="mb-1 ml-1 font-headline text-sm font-bold text-on-surface">{t("theme")}</p>
                      <p className="ml-1 text-xs text-on-surface-variant">{t("themeDescription")}</p>
                    </div>
                    <div className="grid gap-3 sm:grid-cols-2">
                      {themeOptions.map((option) => {
                        const selected = themePreference === option.value;

                        return (
                          <button
                            className={`rounded-2xl border px-4 py-4 text-left transition-colors ${
                              selected
                                ? "border-primary bg-primary-container text-on-primary"
                                : "border-outline-variant bg-surface-container-highest text-on-surface hover:border-primary hover:bg-surface-container-high"
                            }`}
                            key={option.value}
                            onClick={() => handleThemeChange(option.value)}
                            type="button"
                          >
                            <span className="block font-headline text-sm font-bold">{option.label}</span>
                            <span className={`mt-1 block text-xs ${selected ? "text-on-primary/75" : "text-on-surface-variant"}`}>
                              {selected ? t("themeSelected") : t("themeTapToUse")}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="md:col-span-2 space-y-3">
                    <p className="mb-1 ml-1 font-headline text-sm font-bold text-on-surface">{t("notificationPreferences")}</p>
                    <label className="flex items-center justify-between gap-4 rounded-2xl bg-surface-container-highest px-4 py-4 text-sm text-on-surface">
                      <span>{t("notifyPowerLimitExceeded")}</span>
                      <input
                        checked={notifyPowerLimitExceeded}
                        disabled={isDemoAccount}
                        onChange={(event) => setNotifyPowerLimitExceeded(event.target.checked)}
                        type="checkbox"
                      />
                    </label>
                    <div className="rounded-2xl bg-surface-container-highest px-4 py-4 text-sm text-on-surface">
                      <label className="flex items-center justify-between gap-4">
                        <span>{t("notifyControlActivated")}</span>
                        <input
                          checked={notifyControlActivated}
                          disabled={isDemoAccount}
                          onChange={(event) => setNotifyControlActivated(event.target.checked)}
                          type="checkbox"
                        />
                      </label>
                      <p className="mt-2 text-xs leading-5 text-on-surface-variant">{t("notifyControlActivatedHelp")}</p>
                    </div>
                    <div className="rounded-2xl bg-surface-container-highest px-4 py-4 text-sm text-on-surface">
                      <label className="flex items-center justify-between gap-4">
                        <span>{t("notifyDeviceOffline")}</span>
                        <input
                          checked={notifyDeviceOffline}
                          disabled={isDemoAccount}
                          onChange={(event) => setNotifyDeviceOffline(event.target.checked)}
                          type="checkbox"
                        />
                      </label>
                      <p className="mt-2 text-xs leading-5 text-on-surface-variant">{t("notifyDeviceOfflineHelp")}</p>
                    </div>
                    <div className="rounded-2xl bg-surface-container-highest px-4 py-4 text-sm text-on-surface">
                      <label className="flex items-center justify-between gap-4">
                        <span>{t("notifyDeviceOnline")}</span>
                        <input
                          checked={notifyDeviceOnline}
                          disabled={isDemoAccount}
                          onChange={(event) => setNotifyDeviceOnline(event.target.checked)}
                          type="checkbox"
                        />
                      </label>
                      <p className="mt-2 text-xs leading-5 text-on-surface-variant">{t("notifyDeviceOnlineHelp")}</p>
                    </div>
                  </div>

                  <div className="md:col-span-2 space-y-3">
                    <p className="mb-1 ml-1 font-headline text-sm font-bold text-on-surface">{t("notificationChannels")}</p>
                    <label className="flex items-center justify-between gap-4 rounded-2xl bg-surface-container-highest px-4 py-4 text-sm text-on-surface">
                      <span>{t("emailNotificationsEnabled")}</span>
                      <input
                        checked={emailNotificationsEnabled}
                        disabled={isDemoAccount}
                        onChange={(event) => setEmailNotificationsEnabled(event.target.checked)}
                        type="checkbox"
                      />
                    </label>
                    <label className="flex items-center justify-between gap-4 rounded-2xl bg-surface-container-highest px-4 py-4 text-sm text-on-surface">
                      <span>{t("pushNotificationsEnabled")}</span>
                      <input
                        checked={pushNotificationsEnabled}
                        disabled={isDemoAccount}
                        onChange={(event) => setPushNotificationsEnabled(event.target.checked)}
                        type="checkbox"
                      />
                    </label>
                  </div>
                </div>

                <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                  <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSaving || isDemoAccount} type="submit">
                    {isSaving ? t("saving") : t("save")}
                  </button>
                  <Link className="secondary-action justify-center" to="/menu">
                    {t("backToMenu")}
                  </Link>
                </div>

                {message ? <div className="rounded-xl bg-primary-container p-4 text-sm font-semibold text-on-primary">{message}</div> : null}
                {saveError ? <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{saveError}</div> : null}
              </form>

              <form className="app-card space-y-8 p-4 sm:p-6 lg:p-8" onSubmit={handlePasswordChange}>
                <div>
                  <p className="metric-label mb-3">{t("passwordEyebrow")}</p>
                  <h2 className="font-headline text-2xl font-extrabold text-primary">{t("passwordTitle")}</h2>
                  <p className="mt-3 max-w-2xl text-sm leading-6 text-on-surface-variant">{t("passwordDescription")}</p>
                </div>

                <div className="grid gap-6 md:grid-cols-2">
                  <div className="md:col-span-2">
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="account-current-password">
                      {t("passwordCurrent")}
                    </label>
                    <input
                      autoComplete="current-password"
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      disabled={isDemoAccount}
                      id="account-current-password"
                      onChange={(event) => setCurrentPassword(event.target.value)}
                      type="password"
                      value={currentPassword}
                    />
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="account-new-password">
                      {t("passwordNew")}
                    </label>
                    <input
                      autoComplete="new-password"
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      disabled={isDemoAccount}
                      id="account-new-password"
                      onChange={(event) => setNewPassword(event.target.value)}
                      type="password"
                      value={newPassword}
                    />
                    <p className="mt-3 ml-1 text-xs text-on-surface-variant">{t("passwordRequirements")}</p>
                  </div>

                  <div>
                    <label className="mb-3 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="account-confirm-password">
                      {t("passwordConfirm")}
                    </label>
                    <input
                      autoComplete="new-password"
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none transition-all focus:border-primary"
                      disabled={isDemoAccount}
                      id="account-confirm-password"
                      onChange={(event) => setConfirmNewPassword(event.target.value)}
                      type="password"
                      value={confirmNewPassword}
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                  <button className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isChangingPassword || isDemoAccount} type="submit">
                    {isChangingPassword ? t("passwordChanging") : t("passwordChange")}
                  </button>
                </div>

                {passwordMessage ? <div className="rounded-xl bg-primary-container p-4 text-sm font-semibold text-on-primary">{passwordMessage}</div> : null}
                {passwordError ? <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{passwordError}</div> : null}
              </form>

              <section className="app-card p-4 sm:p-6 lg:p-8">
                <div>
                  <p className="metric-label mb-3">{t("exportEyebrow")}</p>
                  <h2 className="font-headline text-2xl font-extrabold text-primary">{t("exportTitle")}</h2>
                  <p className="mt-3 max-w-2xl text-sm leading-6 text-on-surface-variant">{t("exportDescription")}</p>
                </div>

                <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center">
                  <button
                    className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={isDownloadingExport}
                    onClick={handleDownloadExport}
                    type="button"
                  >
                    {isDownloadingExport ? t("exportDownloading") : t("exportButton")}
                  </button>
                </div>

                {exportError ? <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{exportError}</div> : null}
              </section>

              <section className="app-card border border-error-container/70 bg-error-container/20 p-4 sm:p-6 lg:p-8">
                <div>
                  <p className="metric-label mb-3 text-on-error-container">{t("deleteEyebrow")}</p>
                  <h2 className="font-headline text-2xl font-extrabold text-on-error-container">{t("deleteTitle")}</h2>
                  <p className="mt-3 max-w-2xl text-sm leading-6 text-on-error-container">{t("deleteDescription")}</p>
                </div>

                <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center">
                  <button
                    className="inline-flex items-center justify-center rounded-xl bg-on-error-container px-6 py-4 font-headline text-lg font-bold text-error-container transition-transform active:scale-95 disabled:cursor-not-allowed disabled:opacity-60"
                    onClick={() => {
                      setDeleteError(null);
                      setIsDeleteDialogOpen(true);
                    }}
                    disabled={isDemoAccount}
                    type="button"
                  >
                    {t("deleteButton")}
                  </button>
                </div>

                {deleteError ? <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{deleteError}</div> : null}
              </section>
            </section>
          </div>
        ) : null}
      </main>

      {isDeleteDialogOpen ? (
        <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
          <div className="w-full max-w-2xl rounded-xl bg-surface-container-lowest p-6 shadow-2xl">
            <div className="mb-5">
              <p className="metric-label mb-2 text-on-error-container">{t("deleteDialogEyebrow")}</p>
              <h2 className="font-headline text-2xl font-extrabold text-on-error-container">{t("deleteDialogTitle")}</h2>
              <p className="mt-3 text-sm leading-6 text-on-surface-variant">{t("deleteDialogDescription")}</p>
            </div>

            {deleteError ? <div className="mb-5 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">{deleteError}</div> : null}

            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                className="secondary-action justify-center"
                disabled={isDeletingAccount}
                onClick={() => setIsDeleteDialogOpen(false)}
                type="button"
              >
                {common("cancel")}
              </button>
              <button
                className="inline-flex items-center justify-center rounded-xl bg-on-error-container px-6 py-4 font-headline text-lg font-bold text-error-container transition-transform active:scale-95 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isDeletingAccount}
                onClick={handleDeleteAccount}
                type="button"
              >
                {isDeletingAccount ? t("deletingAccount") : t("confirmDeleteButton")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

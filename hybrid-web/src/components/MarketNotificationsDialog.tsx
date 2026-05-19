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

import { FormEvent, useEffect, useState } from "react";
import { useI18n } from "@/lib/i18n";
import {
  createMarketNotification,
  deleteMarketNotification,
  fetchMarketNotifications,
  formatNordpoolPrice,
  type MarketNotification,
  type MarketNotificationComparison,
  type MarketNotificationMetric,
  type MarketNotificationPayload,
  updateMarketNotification
} from "@/lib/nordpool";

type Props = {
  isOpen: boolean;
  onClose: () => void;
  timezone: string;
};

type FormState = {
  activeFrom: string;
  activeTo: string;
  comparisonType: MarketNotificationComparison;
  description: string;
  enabled: boolean;
  metric: MarketNotificationMetric;
  name: string;
  thresholdPrice: string;
};

const DEFAULT_FORM: FormState = {
  activeFrom: "00:00",
  activeTo: "23:59",
  comparisonType: "LESS_THAN",
  description: "",
  enabled: true,
  metric: "CURRENT_PRICE",
  name: "",
  thresholdPrice: "10"
};

function toInputTime(value: string | null | undefined) {
  return value ? value.slice(0, 5) : "00:00";
}

function formatDate(value: string | null, timezone: string) {
  if (!value) {
    return "-";
  }

  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    timeZone: timezone
  }).format(new Date(value));
}

function toForm(notification: MarketNotification): FormState {
  return {
    activeFrom: toInputTime(notification.activeFrom),
    activeTo: toInputTime(notification.activeTo),
    comparisonType: notification.comparisonType,
    description: notification.description ?? "",
    enabled: notification.enabled,
    metric: notification.metric,
    name: notification.name,
    thresholdPrice: String(notification.thresholdPrice)
  };
}

function toPayload(form: FormState, timezone: string): MarketNotificationPayload {
  return {
    activeFrom: form.activeFrom,
    activeTo: form.activeTo,
    comparisonType: form.comparisonType,
    description: form.description.trim() || null,
    enabled: form.enabled,
    metric: form.metric,
    name: form.name.trim(),
    thresholdPrice: Number(form.thresholdPrice),
    timezone
  };
}

export default function MarketNotificationsDialog({ isOpen, onClose, timezone }: Props) {
  const { t } = useI18n("marketNotifications");
  const common = useI18n("common").t;
  const [notifications, setNotifications] = useState<MarketNotification[]>([]);
  const [form, setForm] = useState<FormState>(DEFAULT_FORM);
  const [editing, setEditing] = useState<MarketNotification | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    let active = true;
    setIsLoading(true);
    setError(null);

    fetchMarketNotifications()
      .then((response) => {
        if (!active) return;
        setNotifications(response);
      })
      .catch((loadError) => {
        if (!active) return;
        setError(loadError instanceof Error ? loadError.message : t("failedLoad"));
      })
      .finally(() => {
        if (!active) return;
        setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload = toPayload(form, timezone);

    if (!payload.name) {
      setError(t("nameRequired"));
      return;
    }
    if (!Number.isFinite(payload.thresholdPrice)) {
      setError(t("thresholdRequired"));
      return;
    }

    setIsSaving(true);
    setError(null);

    try {
      const saved = editing
        ? await updateMarketNotification(editing.id, payload)
        : await createMarketNotification(payload);
      setNotifications((current) => editing
        ? current.map((notification) => (notification.id === saved.id ? saved : notification))
        : [...current, saved]);
      setEditing(null);
      setForm(DEFAULT_FORM);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (notificationId: number) => {
    setDeletingId(notificationId);
    setError(null);

    try {
      await deleteMarketNotification(notificationId);
      setNotifications((current) => current.filter((notification) => notification.id !== notificationId));
      if (editing?.id === notificationId) {
        setEditing(null);
        setForm(DEFAULT_FORM);
      }
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedDelete"));
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
      <div className="max-h-[92vh] w-full max-w-5xl overflow-y-auto rounded-xl bg-surface-container-lowest p-5 shadow-2xl sm:p-6">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="metric-label mb-2">{t("eyebrow")}</p>
            <h3 className="font-headline text-2xl font-black text-on-surface">{t("title")}</h3>
            <p className="mt-1 max-w-2xl text-sm text-on-surface-variant">{t("description")}</p>
          </div>
          <button className="secondary-action px-3 py-2 text-sm" onClick={onClose} type="button">
            {common("close")}
          </button>
        </div>

        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="space-y-3">
            {isLoading ? <p className="text-sm text-on-surface-variant">{t("loading")}</p> : null}
            {!isLoading && notifications.length === 0 ? (
              <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">{t("empty")}</div>
            ) : null}

            {notifications.map((notification) => (
              <article className="rounded-xl bg-surface-container p-4" key={notification.id}>
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h4 className="font-headline text-lg font-bold text-on-surface">{notification.name}</h4>
                      <span className={`chip ${notification.enabled ? "bg-primary-fixed text-primary" : "bg-surface-container-highest text-on-surface-variant"}`}>
                        {notification.enabled ? common("enabled") : common("disabled")}
                      </span>
                      {notification.lastSentAt ? <span className="chip bg-secondary-container text-on-primary">{t("sent")}</span> : null}
                    </div>
                    <p className="text-sm text-on-surface-variant">
                      {t("condition", {
                        comparison: t(notification.comparisonType),
                        metric: t(notification.metric),
                        price: formatNordpoolPrice(notification.thresholdPrice)
                      })}
                    </p>
                    <div className="flex flex-wrap gap-2 text-xs text-on-surface-variant">
                      <span className="rounded-full bg-surface-container-highest px-3 py-1">{toInputTime(notification.activeFrom)} - {toInputTime(notification.activeTo)}</span>
                      <span className="rounded-full bg-surface-container-highest px-3 py-1">{notification.timezone}</span>
                      <span className="rounded-full bg-surface-container-highest px-3 py-1">{t("lastSent")}: {formatDate(notification.lastSentAt, notification.timezone)}</span>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      className="secondary-action px-3 py-2 text-xs"
                      onClick={() => {
                        setEditing(notification);
                        setForm(toForm(notification));
                      }}
                      type="button"
                    >
                      {common("edit")}
                    </button>
                    <button
                      className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:opacity-60"
                      disabled={deletingId === notification.id}
                      onClick={() => handleDelete(notification.id)}
                      type="button"
                    >
                      {deletingId === notification.id ? common("removing") : common("remove")}
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>

          <form className="rounded-xl bg-surface-container p-4" onSubmit={handleSubmit}>
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <p className="metric-label mb-1">{editing ? t("editEyebrow") : t("createEyebrow")}</p>
                <h4 className="font-headline text-xl font-bold text-on-surface">{editing ? editing.name : t("addRule")}</h4>
              </div>
              {editing ? (
                <button
                  className="secondary-action px-3 py-2 text-xs"
                  onClick={() => {
                    setEditing(null);
                    setForm(DEFAULT_FORM);
                  }}
                  type="button"
                >
                  {common("cancel")}
                </button>
              ) : null}
            </div>

            <div className="space-y-4">
              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("name")}</span>
                <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:border-primary" onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} type="text" value={form.name} />
              </label>

              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("descriptionLabel")}</span>
                <textarea className="min-h-20 w-full rounded-xl border-none bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:ring-2 focus:ring-primary/40" onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} value={form.description} />
              </label>

              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("metric")}</span>
                  <select className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 text-on-surface outline-none focus:border-primary" onChange={(event) => setForm((current) => ({ ...current, metric: event.target.value as MarketNotificationMetric }))} value={form.metric}>
                    <option value="CURRENT_PRICE">{t("CURRENT_PRICE")}</option>
                    <option value="DAILY_AVERAGE">{t("DAILY_AVERAGE")}</option>
                  </select>
                </label>

                <label className="block">
                  <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("comparison")}</span>
                  <select className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-3 text-on-surface outline-none focus:border-primary" onChange={(event) => setForm((current) => ({ ...current, comparisonType: event.target.value as MarketNotificationComparison }))} value={form.comparisonType}>
                    <option value="LESS_THAN">{t("LESS_THAN")}</option>
                    <option value="GREATER_THAN">{t("GREATER_THAN")}</option>
                  </select>
                </label>
              </div>

              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("thresholdPrice")}</span>
                <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:border-primary" onChange={(event) => setForm((current) => ({ ...current, thresholdPrice: event.target.value }))} step="0.01" type="number" value={form.thresholdPrice} />
              </label>

              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("activeFrom")}</span>
                  <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:border-primary" onChange={(event) => setForm((current) => ({ ...current, activeFrom: event.target.value }))} type="time" value={form.activeFrom} />
                </label>
                <label className="block">
                  <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{t("activeTo")}</span>
                  <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none focus:border-primary" onChange={(event) => setForm((current) => ({ ...current, activeTo: event.target.value }))} type="time" value={form.activeTo} />
                </label>
              </div>

              <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container-highest p-4">
                <span className="font-headline text-sm font-bold text-on-surface">{common("enabled")}</span>
                <input checked={form.enabled} onChange={(event) => setForm((current) => ({ ...current, enabled: event.target.checked }))} type="checkbox" />
              </label>
            </div>

            <button className="primary-action mt-5 w-full justify-center disabled:cursor-not-allowed disabled:opacity-60" disabled={isSaving} type="submit">
              {isSaving ? t("saving") : editing ? t("save") : t("add")}
            </button>
          </form>
        </div>

        {error ? (
          <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
            {error}
          </div>
        ) : null}
      </div>
    </div>
  );
}

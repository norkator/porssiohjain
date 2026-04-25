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
import {
  createControlNotification,
  deleteControlNotification,
  fetchControlNotifications,
  formatControlDate,
  type ControlNotification,
  type ControlNotificationPayload,
  updateControlNotification
} from "@/lib/controls";

type ControlNotificationsCardProps = {
  controlId: number;
  isReadOnly: boolean;
  timezone: string;
};

type NotificationFormState = {
  activeFrom: string;
  activeTo: string;
  cheapestHours: string;
  description: string;
  enabled: boolean;
  name: string;
  sendEarlierMinutes: string;
};

const DEFAULT_FORM: NotificationFormState = {
  activeFrom: "00:00",
  activeTo: "23:59",
  cheapestHours: "0",
  description: "",
  enabled: true,
  name: "",
  sendEarlierMinutes: "0"
};

function toInputTime(value: string | null | undefined) {
  if (!value) {
    return "00:00";
  }

  return value.slice(0, 5);
}

function toInputNumber(value: number | null | undefined, fallback: string) {
  return value === null || value === undefined ? fallback : String(value);
}

function toNumber(value: string, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function toPayload(form: NotificationFormState): ControlNotificationPayload {
  return {
    activeFrom: form.activeFrom,
    activeTo: form.activeTo,
    cheapestHours: Math.max(0, toNumber(form.cheapestHours)),
    description: form.description.trim(),
    enabled: form.enabled,
    name: form.name.trim(),
    sendEarlierMinutes: Math.max(0, Math.round(toNumber(form.sendEarlierMinutes)))
  };
}

function toEditForm(notification: ControlNotification): NotificationFormState {
  return {
    activeFrom: toInputTime(notification.activeFrom),
    activeTo: toInputTime(notification.activeTo),
    cheapestHours: toInputNumber(notification.cheapestHours, "0"),
    description: notification.description ?? "",
    enabled: notification.enabled,
    name: notification.name ?? "",
    sendEarlierMinutes: toInputNumber(notification.sendEarlierMinutes, "0")
  };
}

function formatTimeRange(notification: ControlNotification) {
  return `${toInputTime(notification.activeFrom)} - ${toInputTime(notification.activeTo)}`;
}

function formatCheapestHours(value: number | null) {
  if (value === null || value <= 0) {
    return "Any active hour";
  }

  return `${value} h cheapest window`;
}

export default function ControlNotificationsCard({
  controlId,
  isReadOnly,
  timezone
}: ControlNotificationsCardProps) {
  const [notifications, setNotifications] = useState<ControlNotification[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [createForm, setCreateForm] = useState<NotificationFormState>(DEFAULT_FORM);
  const [isCreating, setIsCreating] = useState(false);
  const [editingNotification, setEditingNotification] = useState<ControlNotification | null>(null);
  const [editForm, setEditForm] = useState<NotificationFormState>(DEFAULT_FORM);
  const [isSavingEdit, setIsSavingEdit] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const [isDeletingId, setIsDeletingId] = useState<number | null>(null);

  useEffect(() => {
    let isActive = true;

    setIsLoading(true);
    setError(null);

    fetchControlNotifications(controlId)
      .then((response) => {
        if (!isActive) {
          return;
        }

        setNotifications(response);
        setIsLoading(false);
      })
      .catch((loadError: unknown) => {
        if (!isActive) {
          return;
        }

        setError(loadError instanceof Error ? loadError.message : "Failed to load notifications");
        setIsLoading(false);
      });

    return () => {
      isActive = false;
    };
  }, [controlId]);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const payload = toPayload(createForm);
    if (!payload.name) {
      setError("Notification name is required.");
      return;
    }

    setIsCreating(true);
    setError(null);
    setMessage(null);

    try {
      const created = await createControlNotification(controlId, payload);
      setNotifications((current) => [...current, created]);
      setCreateForm(DEFAULT_FORM);
      setMessage("Notification added.");
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Failed to create notification");
    } finally {
      setIsCreating(false);
    }
  };

  const handleSaveEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!editingNotification) {
      return;
    }

    const payload = toPayload(editForm);
    if (!payload.name) {
      setError("Notification name is required.");
      return;
    }

    setIsSavingEdit(true);
    setError(null);
    setMessage(null);

    try {
      const updated = await updateControlNotification(controlId, editingNotification.id, payload);
      setNotifications((current) => current.map((notification) => (notification.id === updated.id ? updated : notification)));
      setEditingNotification(null);
      setMessage("Notification updated.");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Failed to update notification");
    } finally {
      setIsSavingEdit(false);
    }
  };

  const handleDelete = async (notificationId: number) => {
    setError(null);
    setMessage(null);
    setIsDeletingId(notificationId);

    try {
      await deleteControlNotification(controlId, notificationId);
      setNotifications((current) => current.filter((notification) => notification.id !== notificationId));
      setDeleteConfirmId((current) => (current === notificationId ? null : current));
      setMessage("Notification removed.");
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Failed to delete notification");
    } finally {
      setIsDeletingId((current) => (current === notificationId ? null : current));
    }
  };

  return (
    <>
      <section className="app-card overflow-hidden">
        <div className="border-b border-outline-variant/50 bg-[linear-gradient(135deg,rgba(0,103,125,0.08),rgba(255,179,67,0.12))] px-6 py-6 sm:px-8">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="metric-label mb-2">Automation Alerts</p>
              <h3 className="font-headline text-3xl font-black tracking-tight text-on-surface">Control notifications</h3>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-on-surface-variant">
                Send notification emails when this control is active within the selected time window.
              </p>
            </div>
            <span className="chip bg-surface-container-highest text-primary-container">{notifications.length}</span>
          </div>
        </div>

        <div className="space-y-6 p-6 sm:p-8">
          {isLoading ? <p className="text-sm text-on-surface-variant">Loading notifications...</p> : null}

          {!isLoading && notifications.length === 0 ? (
            <div className="rounded-2xl bg-surface-container p-5 text-sm text-on-surface-variant">
              No control notifications configured yet.
            </div>
          ) : null}

          <div className="space-y-4">
            {notifications.map((notification) => (
              <article className="rounded-2xl bg-surface-container p-5" key={notification.id}>
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-3">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h4 className="font-headline text-xl font-bold text-on-surface">{notification.name}</h4>
                        <span className={`chip ${notification.enabled ? "bg-primary-fixed text-primary" : "bg-surface-container-highest text-on-surface-variant"}`}>
                          {notification.enabled ? "Enabled" : "Disabled"}
                        </span>
                      </div>
                      <p className="mt-1 text-sm text-on-surface-variant">
                        {notification.description?.trim() ? notification.description : "No description"}
                      </p>
                    </div>

                    <div className="grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4">
                      <div>
                        <p className="metric-label mb-1">Active Time</p>
                        <p className="font-semibold text-on-surface">{formatTimeRange(notification)}</p>
                      </div>
                      <div>
                        <p className="metric-label mb-1">Cheapest Hours</p>
                        <p className="font-semibold text-on-surface">{formatCheapestHours(notification.cheapestHours)}</p>
                      </div>
                      <div>
                        <p className="metric-label mb-1">Send Earlier</p>
                        <p className="font-semibold text-on-surface">{notification.sendEarlierMinutes ?? 0} min</p>
                      </div>
                      <div>
                        <p className="metric-label mb-1">Next Send</p>
                        <p className="font-semibold text-on-surface">{formatControlDate(notification.nextSendAt, timezone)}</p>
                      </div>
                    </div>

                    <div className="grid gap-3 text-sm sm:grid-cols-2">
                      <div>
                        <p className="metric-label mb-1">Last Sent</p>
                        <p className="font-semibold text-on-surface">{formatControlDate(notification.lastSentAt, timezone)}</p>
                      </div>
                      <div>
                        <p className="metric-label mb-1">Created</p>
                        <p className="font-semibold text-on-surface">{formatControlDate(notification.createdAt, timezone)}</p>
                      </div>
                    </div>
                  </div>

                  {!isReadOnly ? (
                    deleteConfirmId === notification.id ? (
                      <div className="min-w-[13rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                        <div>
                          <p className="font-headline text-sm font-bold text-on-error-container">Confirm removal</p>
                          <p className="text-xs text-on-error-container">This deletes the notification rule.</p>
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                          <button
                            className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                            disabled={isDeletingId === notification.id}
                            onClick={() => handleDelete(notification.id)}
                            type="button"
                          >
                            {isDeletingId === notification.id ? "Removing..." : "Confirm"}
                          </button>
                          <button
                            className="secondary-action justify-center px-3 py-2 text-xs"
                            disabled={isDeletingId === notification.id}
                            onClick={() => setDeleteConfirmId(null)}
                            type="button"
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        <button
                          className="secondary-action justify-center px-4 py-2 text-sm"
                          onClick={() => {
                            setEditingNotification(notification);
                            setEditForm(toEditForm(notification));
                          }}
                          type="button"
                        >
                          Edit
                        </button>
                        <button
                          className="rounded-lg bg-error-container px-4 py-2 text-sm font-bold text-on-error-container"
                          onClick={() => setDeleteConfirmId(notification.id)}
                          type="button"
                        >
                          Delete
                        </button>
                      </div>
                    )
                  ) : null}
                </div>
              </article>
            ))}
          </div>

          {!isReadOnly ? (
            <form className="rounded-3xl bg-surface-container-low p-5 sm:p-6" onSubmit={handleCreate}>
              <div className="mb-5">
                <p className="metric-label mb-2">Create Notification</p>
                <h4 className="font-headline text-2xl font-bold text-on-surface">Add rule</h4>
              </div>

              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                <div className="md:col-span-2 xl:col-span-1">
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="notification-name">
                    Name
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="notification-name"
                    onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))}
                    type="text"
                    value={createForm.name}
                  />
                </div>

                <div className="md:col-span-2 xl:col-span-2">
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="notification-description">
                    Description
                  </label>
                  <textarea
                    className="min-h-24 w-full rounded-2xl border-none bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all placeholder:text-on-surface-variant/40 focus:ring-2 focus:ring-primary/40"
                    id="notification-description"
                    onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))}
                    value={createForm.description}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="notification-active-from">
                    Active From
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="notification-active-from"
                    onChange={(event) => setCreateForm((current) => ({ ...current, activeFrom: event.target.value }))}
                    type="time"
                    value={createForm.activeFrom}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="notification-active-to">
                    Active To
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="notification-active-to"
                    onChange={(event) => setCreateForm((current) => ({ ...current, activeTo: event.target.value }))}
                    type="time"
                    value={createForm.activeTo}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="notification-cheapest-hours">
                    Cheapest Hours
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="notification-cheapest-hours"
                    min="0"
                    onChange={(event) => setCreateForm((current) => ({ ...current, cheapestHours: event.target.value }))}
                    step="0.25"
                    type="number"
                    value={createForm.cheapestHours}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="notification-send-earlier">
                    Send Earlier Minutes
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="notification-send-earlier"
                    min="0"
                    onChange={(event) => setCreateForm((current) => ({ ...current, sendEarlierMinutes: event.target.value }))}
                    step="1"
                    type="number"
                    value={createForm.sendEarlierMinutes}
                  />
                </div>

                <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container-highest p-4">
                  <span className="font-headline text-sm font-bold text-on-surface">Enabled</span>
                  <input
                    checked={createForm.enabled}
                    onChange={(event) => setCreateForm((current) => ({ ...current, enabled: event.target.checked }))}
                    type="checkbox"
                  />
                </label>
              </div>

              <div className="mt-5 flex flex-col gap-4 sm:flex-row sm:items-center">
                <button
                  className="secondary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={isCreating}
                  type="submit"
                >
                  {isCreating ? "Adding..." : "Add Notification"}
                </button>
                <p className="text-sm text-on-surface-variant">Use `0` cheapest hours to match any active hour.</p>
              </div>
            </form>
          ) : null}

          {message ? (
            <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary">{message}</div>
          ) : null}

          {error ? (
            <div className="rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
              {error}
            </div>
          ) : null}
        </div>
      </section>

      {editingNotification ? (
        <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
          <div className="w-full max-w-3xl rounded-xl bg-surface-container-lowest p-6 shadow-2xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <p className="metric-label mb-2">Edit Notification</p>
                <h3 className="font-headline text-2xl font-bold text-primary">{editingNotification.name}</h3>
              </div>
              <button className="secondary-action px-3 py-2 text-sm" onClick={() => setEditingNotification(null)} type="button">
                Close
              </button>
            </div>

            <form className="space-y-5" onSubmit={handleSaveEdit}>
              <div className="grid gap-4 md:grid-cols-2">
                <div className="md:col-span-2">
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="edit-notification-name">
                    Name
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="edit-notification-name"
                    onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))}
                    type="text"
                    value={editForm.name}
                  />
                </div>

                <div className="md:col-span-2">
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="edit-notification-description">
                    Description
                  </label>
                  <textarea
                    className="min-h-28 w-full rounded-2xl border-none bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:ring-2 focus:ring-primary/40"
                    id="edit-notification-description"
                    onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
                    value={editForm.description}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="edit-notification-active-from">
                    Active From
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="edit-notification-active-from"
                    onChange={(event) => setEditForm((current) => ({ ...current, activeFrom: event.target.value }))}
                    type="time"
                    value={editForm.activeFrom}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="edit-notification-active-to">
                    Active To
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="edit-notification-active-to"
                    onChange={(event) => setEditForm((current) => ({ ...current, activeTo: event.target.value }))}
                    type="time"
                    value={editForm.activeTo}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="edit-notification-cheapest-hours">
                    Cheapest Hours
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="edit-notification-cheapest-hours"
                    min="0"
                    onChange={(event) => setEditForm((current) => ({ ...current, cheapestHours: event.target.value }))}
                    step="0.25"
                    type="number"
                    value={editForm.cheapestHours}
                  />
                </div>

                <div>
                  <label className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface" htmlFor="edit-notification-send-earlier">
                    Send Earlier Minutes
                  </label>
                  <input
                    className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary"
                    id="edit-notification-send-earlier"
                    min="0"
                    onChange={(event) => setEditForm((current) => ({ ...current, sendEarlierMinutes: event.target.value }))}
                    step="1"
                    type="number"
                    value={editForm.sendEarlierMinutes}
                  />
                </div>

                <label className="flex items-center justify-between gap-4 rounded-xl bg-surface-container p-4">
                  <span className="font-headline text-sm font-bold text-on-surface">Enabled</span>
                  <input
                    checked={editForm.enabled}
                    onChange={(event) => setEditForm((current) => ({ ...current, enabled: event.target.checked }))}
                    type="checkbox"
                  />
                </label>
              </div>

              <div className="flex flex-col gap-3 sm:flex-row">
                <button
                  className="primary-action justify-center disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={isSavingEdit}
                  type="submit"
                >
                  {isSavingEdit ? "Saving..." : "Save Notification"}
                </button>
                <button className="secondary-action justify-center" onClick={() => setEditingNotification(null)} type="button">
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </>
  );
}

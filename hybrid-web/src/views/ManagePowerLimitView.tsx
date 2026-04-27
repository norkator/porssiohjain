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
import PowerLimitHistoryChartCard from "@/components/PowerLimitHistoryChartCard";
import {
  addPowerLimitDeviceLink,
  deletePowerLimit,
  deletePowerLimitDeviceLink,
  fetchPowerLimit,
  fetchPowerLimitDeviceLinks,
  fetchSites,
  formatDate,
  formatKw,
  updatePowerLimit,
  type ApiPowerLimit,
  type ApiSite,
  type PowerLimitDeviceLink
} from "@/lib/automation-resources";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import { fetchDevices, type ApiDevice } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

export default function ManagePowerLimitView() {
  const navigate = useNavigate();
  const { t } = useI18n("powerLimits");
  const common = useI18n("common").t;
  const params = useParams();
  const powerLimitId = Number(params.powerLimitId);
  const timezones = useMemo(() => getAvailableTimezones(), []);
  const [limit, setLimit] = useState<ApiPowerLimit | null>(null);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [devices, setDevices] = useState<ApiDevice[]>([]);
  const [links, setLinks] = useState<PowerLimitDeviceLink[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [name, setName] = useState("");
  const [limitKw, setLimitKw] = useState("5");
  const [limitIntervalMinutes, setLimitIntervalMinutes] = useState("15");
  const [timezone, setTimezone] = useState("UTC");
  const [siteId, setSiteId] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [notifyEnabled, setNotifyEnabled] = useState(false);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [deviceChannel, setDeviceChannel] = useState("1");
  const [deleteLinkConfirmId, setDeleteLinkConfirmId] = useState<number | null>(null);
  const [isDeletingLinkId, setIsDeletingLinkId] = useState<number | null>(null);

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [limitResponse, siteResponse, linkResponse, deviceResponse] = await Promise.all([
        fetchPowerLimit(powerLimitId),
        fetchSites(),
        fetchPowerLimitDeviceLinks(powerLimitId),
        fetchDevices()
      ]);
      setLimit(limitResponse);
      setName(limitResponse.name);
      setLimitKw(String(limitResponse.limitKw ?? 0));
      setLimitIntervalMinutes(String(limitResponse.limitIntervalMinutes ?? 15));
      setTimezone(limitResponse.timezone || "UTC");
      setSiteId(limitResponse.siteId ? String(limitResponse.siteId) : "");
      setEnabled(limitResponse.enabled);
      setNotifyEnabled(limitResponse.notifyEnabled);
      setSites(siteResponse);
      setLinks(linkResponse);
      setDevices(deviceResponse.filter((device) => device.deviceType === "STANDARD" && !device.shared));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("failedOne"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    if (Number.isFinite(powerLimitId)) loadData();
  }, [powerLimitId]);

  if (!Number.isFinite(powerLimitId)) return <Navigate replace to="/power-limits" />;

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedLimit = Number(limitKw);
    const parsedInterval = Number(limitIntervalMinutes);
    setError(null);
    setMessage(null);
    try {
      const response = await updatePowerLimit(powerLimitId, {
        enabled,
        limitIntervalMinutes: Number.isFinite(parsedInterval) ? parsedInterval : 15,
        limitKw: Number.isFinite(parsedLimit) ? parsedLimit : 0,
        name: name.trim(),
        notifyEnabled,
        siteId: siteId ? Number(siteId) : null,
        timezone
      });
      setLimit(response);
      setMessage(t("saved"));
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    }
  };

  const handleAddLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const deviceId = Number(selectedDeviceId);
    const channel = Number(deviceChannel);
    if (!Number.isFinite(deviceId) || !Number.isInteger(channel)) return;
    setError(null);
    try {
      await addPowerLimitDeviceLink(powerLimitId, { deviceChannel: channel, deviceId });
      setLinks(await fetchPowerLimitDeviceLinks(powerLimitId));
      setSelectedDeviceId("");
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : t("failedLink"));
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setError(null);
    try {
      await deletePowerLimit(powerLimitId);
      navigate("/power-limits");
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedDelete"));
      setIsDeleting(false);
    }
  };

  const handleDeleteLink = async (linkId: number) => {
    setError(null);
    setIsDeletingLinkId(linkId);

    try {
      await deletePowerLimitDeviceLink(linkId);
      setLinks((current) => current.filter((item) => item.id !== linkId));
      setDeleteLinkConfirmId((current) => (current === linkId ? null : current));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedRemoveLink"));
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
  };

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} title={t("manageTitle")} compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingOne")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}
        {!isLoading && limit ? (
          <div className="space-y-8">
            <div className="grid gap-10 lg:grid-cols-12">
              <section className="space-y-8 lg:col-span-8">
                <div><p className="metric-label mb-3">{t("label", { id: powerLimitId })}</p><h1 className="mb-4 font-headline text-4xl font-extrabold text-primary">{limit.name}</h1></div>
                <form className="app-card grid gap-4 p-6 md:grid-cols-2" onSubmit={handleSave}>
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} value={name} />
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" min="0" onChange={(event) => setLimitKw(event.target.value)} step="0.1" type="number" value={limitKw} />
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setLimitIntervalMinutes(event.target.value)} value={limitIntervalMinutes}><option value="15">15 min</option><option value="60">60 min</option></select>
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setTimezone(event.target.value)} value={timezone}>{timezones.map((item) => <option key={item} value={item}>{item}</option>)}</select>
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setSiteId(event.target.value)} value={siteId}><option value="">{common("noSite")}</option>{sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select>
                  <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">{common("enabled")}</span><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" /></label>
                  <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">{common("notifications")}</span><input checked={notifyEnabled} onChange={(event) => setNotifyEnabled(event.target.checked)} type="checkbox" /></label>
                  <button className="primary-action justify-center md:col-span-2" type="submit">{t("save")}</button>
                  {message ? <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary md:col-span-2">{message}</div> : null}
                </form>

                <section className="app-card p-6">
                  <div className="mb-5 flex items-center justify-between"><h2 className="font-headline text-xl font-bold">{common("linkedDevices")}</h2><span className="chip bg-surface-container-highest text-primary-container">{links.length}</span></div>
                  <div className="space-y-3">
                    {links.map((link) => (
                      <div className="rounded-xl bg-surface-container p-4" key={link.id}>
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-headline font-bold">{link.device.deviceName}</p>
                            <p className="font-mono text-xs text-outline">UUID: {link.device.uuid}</p>
                          </div>
                          {deleteLinkConfirmId === link.id ? (
                            <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                              <div>
                                <p className="font-headline text-sm font-bold text-on-error-container">{common("confirmRemoval")}</p>
                                <p className="text-xs text-on-error-container">{t("unlinkDescription")}</p>
                              </div>
                              <div className="grid grid-cols-2 gap-2">
                                <button
                                  className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                                  disabled={isDeletingLinkId === link.id}
                                  onClick={() => handleDeleteLink(link.id)}
                                  type="button"
                                >
                                  {isDeletingLinkId === link.id ? common("removing") : common("confirm")}
                                </button>
                                <button
                                  className="secondary-action justify-center px-3 py-2 text-xs"
                                  disabled={isDeletingLinkId === link.id}
                                  onClick={() => setDeleteLinkConfirmId(null)}
                                  type="button"
                                >
                                  {common("cancel")}
                                </button>
                              </div>
                            </div>
                          ) : (
                            <button
                              className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container"
                              onClick={() => setDeleteLinkConfirmId(link.id)}
                              type="button"
                            >
                              {common("remove")}
                            </button>
                          )}
                        </div>
                        <div className="mt-3 text-sm"><span className="metric-label">{common("channel")}</span><p className="font-semibold">{link.deviceChannel}</p></div>
                      </div>
                    ))}
                  </div>
                  <form className="mt-6 grid gap-4 border-t border-outline-variant/50 pt-6 md:grid-cols-3" onSubmit={handleAddLink}>
                    <select className="rounded-t-lg bg-surface-container-highest px-4 py-3 md:col-span-2" onChange={(event) => setSelectedDeviceId(event.target.value)} value={selectedDeviceId}><option value="">{common("selectStandardDevice")}</option>{devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}</select>
                    <input className="rounded-t-lg bg-surface-container-highest px-4 py-3" min="0" onChange={(event) => setDeviceChannel(event.target.value)} type="number" value={deviceChannel} />
                    <button className="secondary-action justify-center disabled:opacity-60 md:col-span-3" disabled={!selectedDeviceId} type="submit">{common("linkStandardDevice")}</button>
                  </form>
                </section>
              </section>
              <aside className="space-y-6 lg:col-span-4">
                <div className="app-card p-6"><p className="metric-label mb-2">{common("currentKw")}</p><p className="font-headline text-3xl font-bold">{formatKw(limit.currentKw)}</p></div>
                <div className="app-card p-6"><p className="metric-label mb-2">{common("peakKw")}</p><p className="font-headline text-3xl font-bold">{formatKw(limit.peakKw)}</p></div>
                <div className="app-card p-6"><p className="metric-label mb-2">{common("updated")}</p><p className="font-semibold">{formatDate(limit.updatedAt, limit.timezone)}</p></div>
                <Link className="secondary-action justify-center" to="/power-limits">{common("back")}</Link>
              </aside>
            </div>

            <PowerLimitHistoryChartCard
              limitKw={limit.limitKw}
              powerLimitId={powerLimitId}
              timezone={limit.timezone}
            />

            <section className="app-card border-error-container bg-error-container/40 p-6">
              {!deleteConfirmOpen ? (
                <button
                  className="w-full rounded-xl bg-error-container px-5 py-4 font-headline font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={isDeleting}
                  onClick={() => setDeleteConfirmOpen(true)}
                  type="button"
                >
                  {t("delete")}
                </button>
              ) : (
                <div className="space-y-4">
                  <div>
                    <p className="font-headline text-lg font-bold text-on-error-container">{common("confirmDeletion")}</p>
                    <p className="text-sm text-on-error-container">{t("deleteDescription")}</p>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      className="rounded-xl bg-error-container px-4 py-3 font-headline font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                      disabled={isDeleting}
                      onClick={handleDelete}
                      type="button"
                    >
                      {isDeleting ? common("deleting") : common("confirm")}
                    </button>
                    <button
                      className="secondary-action justify-center"
                      disabled={isDeleting}
                      onClick={() => setDeleteConfirmOpen(false)}
                      type="button"
                    >
                      {common("cancel")}
                    </button>
                  </div>
                </div>
              )}
            </section>
          </div>
        ) : null}
      </main>
    </>
  );
}

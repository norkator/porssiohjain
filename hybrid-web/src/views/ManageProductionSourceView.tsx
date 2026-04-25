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
import {
  addProductionSourceDeviceLink,
  COMPARISONS,
  CONTROL_ACTIONS,
  deleteProductionSource,
  deleteProductionSourceDeviceLink,
  fetchProductionSource,
  fetchProductionSourceDeviceLinks,
  fetchSites,
  formatDate,
  formatKw,
  PRODUCTION_API_TYPES,
  updateProductionSource,
  type ApiProductionSource,
  type ApiSite,
  type ComparisonType,
  type ControlAction,
  type ProductionApiType,
  type ProductionSourceDeviceLink
} from "@/lib/automation-resources";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import { fetchDevices, type ApiDevice } from "@/lib/devices";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

const label = (value: string) => value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char: string) => char.toUpperCase());

export default function ManageProductionSourceView() {
  const navigate = useNavigate();
  const params = useParams();
  const sourceId = Number(params.sourceId);
  const timezones = useMemo(() => getAvailableTimezones(), []);
  const [source, setSource] = useState<ApiProductionSource | null>(null);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [devices, setDevices] = useState<ApiDevice[]>([]);
  const [links, setLinks] = useState<ProductionSourceDeviceLink[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [name, setName] = useState("");
  const [apiType, setApiType] = useState<ProductionApiType>("SHELLY");
  const [timezone, setTimezone] = useState("UTC");
  const [siteId, setSiteId] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [appId, setAppId] = useState("");
  const [appSecret, setAppSecret] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [stationId, setStationId] = useState("");
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [deviceChannel, setDeviceChannel] = useState("1");
  const [triggerKw, setTriggerKw] = useState("0");
  const [comparisonType, setComparisonType] = useState<ComparisonType>("GREATER_THAN");
  const [action, setAction] = useState<ControlAction>("TURN_ON");
  const [deleteLinkConfirmId, setDeleteLinkConfirmId] = useState<number | null>(null);
  const [isDeletingLinkId, setIsDeletingLinkId] = useState<number | null>(null);

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [sourceResponse, siteResponse, linkResponse, deviceResponse] = await Promise.all([
        fetchProductionSource(sourceId),
        fetchSites(),
        fetchProductionSourceDeviceLinks(sourceId),
        fetchDevices()
      ]);
      setSource(sourceResponse);
      setName(sourceResponse.name);
      setApiType(sourceResponse.apiType);
      setTimezone(sourceResponse.timezone || "UTC");
      setSiteId(sourceResponse.siteId ? String(sourceResponse.siteId) : "");
      setEnabled(sourceResponse.enabled);
      setAppId(sourceResponse.appId ?? "");
      setAppSecret("");
      setEmail(sourceResponse.email ?? "");
      setPassword("");
      setStationId(sourceResponse.stationId ?? "");
      setSites(siteResponse);
      setLinks(linkResponse);
      setDevices(deviceResponse.filter((device) => device.deviceType === "STANDARD" && !device.shared));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load production source");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    if (Number.isFinite(sourceId)) loadData();
  }, [sourceId]);

  if (!Number.isFinite(sourceId)) return <Navigate replace to="/production-sources" />;

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setMessage(null);
    try {
      const response = await updateProductionSource(sourceId, {
        apiType,
        appId: appId.trim() || null,
        appSecret: appSecret.trim() || null,
        email: email.trim() || null,
        enabled,
        name: name.trim(),
        password: password.trim() || null,
        siteId: siteId ? Number(siteId) : null,
        stationId: stationId.trim() || null,
        timezone
      });
      setSource(response);
      setMessage("Production source saved.");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Failed to save production source");
    }
  };

  const handleAddLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const deviceId = Number(selectedDeviceId);
    const channel = Number(deviceChannel);
    const trigger = Number(triggerKw);
    if (!Number.isFinite(deviceId) || !Number.isInteger(channel) || !Number.isFinite(trigger)) return;
    setError(null);
    try {
      await addProductionSourceDeviceLink(sourceId, { action, comparisonType, deviceChannel: channel, deviceId, triggerKw: trigger });
      setLinks(await fetchProductionSourceDeviceLinks(sourceId));
      setSelectedDeviceId("");
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : "Failed to link device");
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setError(null);
    try {
      await deleteProductionSource(sourceId);
      navigate("/production-sources");
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Failed to delete production source");
      setIsDeleting(false);
    }
  };

  const handleDeleteLink = async (linkId: number) => {
    setError(null);
    setIsDeletingLinkId(linkId);

    try {
      await deleteProductionSourceDeviceLink(sourceId, linkId);
      setLinks((current) => current.filter((item) => item.id !== linkId));
      setDeleteLinkConfirmId((current) => (current === linkId ? null : current));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Failed to remove device rule");
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
  };

  return (
    <>
      <PageHeader title="Manage Production" compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">Loading production source...</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}
        {!isLoading && source ? (
          <div className="grid gap-10 lg:grid-cols-12">
            <section className="space-y-8 lg:col-span-8">
              <div><p className="metric-label mb-3">Production Source #{sourceId}</p><h1 className="mb-4 font-headline text-4xl font-extrabold text-primary">{source.name}</h1></div>
              <form className="app-card grid gap-4 p-6 md:grid-cols-2" onSubmit={handleSave}>
                <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} value={name} />
                <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setApiType(event.target.value as ProductionApiType)} value={apiType}>{PRODUCTION_API_TYPES.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setTimezone(event.target.value)} value={timezone}>{timezones.map((item) => <option key={item} value={item}>{item}</option>)}</select>
                <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setSiteId(event.target.value)} value={siteId}><option value="">No site</option>{sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select>
                <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setAppId(event.target.value)} placeholder="App ID" value={appId} />
                <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setAppSecret(event.target.value)} placeholder="New app secret" type="password" value={appSecret} />
                <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setEmail(event.target.value)} placeholder="Email" value={email} />
                <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setPassword(event.target.value)} placeholder="New password" type="password" value={password} />
                <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setStationId(event.target.value)} placeholder="Station ID" value={stationId} />
                <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">Enabled</span><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" /></label>
                <button className="primary-action justify-center md:col-span-2" type="submit">Save Production Source</button>
                {message ? <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary md:col-span-2">{message}</div> : null}
              </form>

              <section className="app-card p-6">
                <div className="mb-5 flex items-center justify-between"><h2 className="font-headline text-xl font-bold">Device Rules</h2><span className="chip bg-surface-container-highest text-primary-container">{links.length}</span></div>
                <div className="space-y-3">
                  {links.map((link) => (
                    <div className="rounded-xl bg-surface-container p-4" key={link.id}>
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-headline font-bold">{link.device.deviceName}</p>
                          <p className="text-sm text-on-surface-variant">{label(link.comparisonType)} {link.triggerKw} kW</p>
                        </div>
                        {deleteLinkConfirmId === link.id ? (
                          <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                            <div>
                              <p className="font-headline text-sm font-bold text-on-error-container">Confirm removal</p>
                              <p className="text-xs text-on-error-container">This removes the device rule from this production source.</p>
                            </div>
                            <div className="grid grid-cols-2 gap-2">
                              <button
                                className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                                disabled={isDeletingLinkId === link.id}
                                onClick={() => handleDeleteLink(link.id)}
                                type="button"
                              >
                                {isDeletingLinkId === link.id ? "Removing..." : "Confirm"}
                              </button>
                              <button
                                className="secondary-action justify-center px-3 py-2 text-xs"
                                disabled={isDeletingLinkId === link.id}
                                onClick={() => setDeleteLinkConfirmId(null)}
                                type="button"
                              >
                                Cancel
                              </button>
                            </div>
                          </div>
                        ) : (
                          <button
                            className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container"
                            onClick={() => setDeleteLinkConfirmId(link.id)}
                            type="button"
                          >
                            Remove
                          </button>
                        )}
                      </div>
                      <div className="mt-3 grid grid-cols-2 gap-2 text-sm"><div><span className="metric-label">Channel</span><p className="font-semibold">{link.deviceChannel}</p></div><div><span className="metric-label">Action</span><p className="font-semibold">{label(link.action)}</p></div></div>
                    </div>
                  ))}
                </div>
                <form className="mt-6 grid gap-4 border-t border-outline-variant/50 pt-6 md:grid-cols-2" onSubmit={handleAddLink}>
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setSelectedDeviceId(event.target.value)} value={selectedDeviceId}><option value="">Select standard device</option>{devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}</select>
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-3" min="0" onChange={(event) => setDeviceChannel(event.target.value)} type="number" value={deviceChannel} />
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setTriggerKw(event.target.value)} step="0.1" type="number" value={triggerKw} />
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setComparisonType(event.target.value as ComparisonType)} value={comparisonType}>{COMPARISONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setAction(event.target.value as ControlAction)} value={action}>{CONTROL_ACTIONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                  <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedDeviceId} type="submit">Add Device Rule</button>
                </form>
              </section>
            </section>
            <aside className="space-y-6 lg:col-span-4">
              <div className="app-card p-6"><p className="metric-label mb-2">Current kW</p><p className="font-headline text-3xl font-bold">{formatKw(source.currentKw)}</p></div>
              <div className="app-card p-6"><p className="metric-label mb-2">Peak kW</p><p className="font-headline text-3xl font-bold">{formatKw(source.peakKw)}</p></div>
              <div className="app-card p-6"><p className="metric-label mb-2">Updated</p><p className="font-semibold">{formatDate(source.updatedAt, source.timezone)}</p></div>
              <Link className="secondary-action justify-center" to="/production-sources">Back</Link>
              {!source.shared ? (
                <div className="app-card border-error-container bg-error-container/40 p-6">
                  {!deleteConfirmOpen ? (
                    <button
                      className="w-full rounded-xl bg-error-container px-5 py-4 font-headline font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                      disabled={isDeleting}
                      onClick={() => setDeleteConfirmOpen(true)}
                      type="button"
                    >
                      Delete Production Source
                    </button>
                  ) : (
                    <div className="space-y-4">
                      <div>
                        <p className="font-headline text-lg font-bold text-on-error-container">Confirm deletion</p>
                        <p className="text-sm text-on-error-container">This removes the production source and its linked device rules.</p>
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <button
                          className="rounded-xl bg-error-container px-4 py-3 font-headline font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                          disabled={isDeleting}
                          onClick={handleDelete}
                          type="button"
                        >
                          {isDeleting ? "Deleting..." : "Confirm"}
                        </button>
                        <button
                          className="secondary-action justify-center"
                          disabled={isDeleting}
                          onClick={() => setDeleteConfirmOpen(false)}
                          type="button"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ) : null}
            </aside>
          </div>
        ) : null}
      </main>
    </>
  );
}

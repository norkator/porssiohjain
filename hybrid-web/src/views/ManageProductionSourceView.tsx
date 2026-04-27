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
import HeatPumpStateDialog from "@/components/HeatPumpStateDialog";
import ProductionHistoryChartCard from "@/components/ProductionHistoryChartCard";
import {
  addProductionSourceDeviceLink,
  addProductionSourceHeatPumpLink,
  COMPARISONS,
  CONTROL_ACTIONS,
  deleteProductionSource,
  deleteProductionSourceDeviceLink,
  deleteProductionSourceHeatPumpLink,
  fetchProductionSource,
  fetchProductionSourceDeviceLinks,
  fetchProductionSourceHeatPumpLinks,
  fetchSites,
  formatDate,
  formatKw,
  PRODUCTION_API_TYPES,
  updateProductionSource,
  updateProductionSourceHeatPumpLink,
  type ApiProductionSource,
  type ApiSite,
  type ComparisonType,
  type ControlAction,
  type ProductionApiType,
  type ProductionSourceDeviceLink,
  type ProductionSourceHeatPumpLink
} from "@/lib/automation-resources";
import { getAvailableTimezones } from "@/lib/add-device-flow";
import { fetchDevices, fetchHeatPumpState, type AcType, type ApiDevice } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";

const label = (value: string) => value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char: string) => char.toUpperCase());
type RuleTab = "STANDARD" | "HEAT_PUMP";

export default function ManageProductionSourceView() {
  const navigate = useNavigate();
  const { t } = useI18n("productionSources");
  const common = useI18n("common").t;
  const params = useParams();
  const sourceId = Number(params.sourceId);
  const timezones = useMemo(() => getAvailableTimezones(), []);
  const [source, setSource] = useState<ApiProductionSource | null>(null);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [devices, setDevices] = useState<ApiDevice[]>([]);
  const [standardLinks, setStandardLinks] = useState<ProductionSourceDeviceLink[]>([]);
  const [heatPumpLinks, setHeatPumpLinks] = useState<ProductionSourceHeatPumpLink[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [activeRuleTab, setActiveRuleTab] = useState<RuleTab>("STANDARD");
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
  const [selectedHeatPumpDeviceId, setSelectedHeatPumpDeviceId] = useState("");
  const [heatPumpStateHex, setHeatPumpStateHex] = useState("");
  const [heatPumpTriggerKw, setHeatPumpTriggerKw] = useState("0");
  const [heatPumpComparisonType, setHeatPumpComparisonType] = useState<ComparisonType>("GREATER_THAN");
  const [heatPumpAction, setHeatPumpAction] = useState<ControlAction>("TURN_ON");
  const [editingHeatPumpLinkId, setEditingHeatPumpLinkId] = useState<number | null>(null);
  const [deleteHeatPumpConfirmId, setDeleteHeatPumpConfirmId] = useState<number | null>(null);
  const [isDeletingHeatPumpId, setIsDeletingHeatPumpId] = useState<number | null>(null);
  const [isHeatPumpStateDialogOpen, setIsHeatPumpStateDialogOpen] = useState(false);
  const [isLoadingHeatPumpState, setIsLoadingHeatPumpState] = useState(false);
  const [heatPumpStateDialogValue, setHeatPumpStateDialogValue] = useState("");
  const [heatPumpCurrentState, setHeatPumpCurrentState] = useState<string | null>(null);
  const [heatPumpLastPolledState, setHeatPumpLastPolledState] = useState<string | null>(null);
  const [heatPumpDialogAcType, setHeatPumpDialogAcType] = useState<AcType>("NONE");

  const standardDevices = devices.filter((device) => device.deviceType === "STANDARD" && !device.shared);
  const heatPumpDevices = devices.filter((device) => device.deviceType === "HEAT_PUMP" && !device.shared);

  const resetHeatPumpForm = () => {
    setEditingHeatPumpLinkId(null);
    setSelectedHeatPumpDeviceId("");
    setHeatPumpStateHex("");
    setHeatPumpTriggerKw("0");
    setHeatPumpComparisonType("GREATER_THAN");
    setHeatPumpAction("TURN_ON");
  };

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [sourceResponse, siteResponse, standardLinkResponse, heatPumpLinkResponse, deviceResponse] = await Promise.all([
        fetchProductionSource(sourceId),
        fetchSites(),
        fetchProductionSourceDeviceLinks(sourceId),
        fetchProductionSourceHeatPumpLinks(sourceId),
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
      setStandardLinks(standardLinkResponse);
      setHeatPumpLinks(heatPumpLinkResponse);
      setDevices(deviceResponse);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("failedOne"));
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
      setMessage(t("saved"));
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
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
      setStandardLinks(await fetchProductionSourceDeviceLinks(sourceId));
      setSelectedDeviceId("");
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : t("failedLink"));
    }
  };

  const handleAddHeatPumpLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const deviceId = Number(selectedHeatPumpDeviceId);
    const trigger = Number(heatPumpTriggerKw);
    if (!Number.isFinite(deviceId) || !Number.isFinite(trigger) || !heatPumpStateHex.trim()) return;
    setError(null);
    try {
      const payload = {
        comparisonType: heatPumpComparisonType,
        controlAction: heatPumpAction,
        deviceId,
        stateHex: heatPumpStateHex.trim(),
        triggerKw: trigger
      };
      if (editingHeatPumpLinkId === null) {
        await addProductionSourceHeatPumpLink(sourceId, payload);
      } else {
        await updateProductionSourceHeatPumpLink(editingHeatPumpLinkId, { sourceId, ...payload });
      }
      setHeatPumpLinks(await fetchProductionSourceHeatPumpLinks(sourceId));
      resetHeatPumpForm();
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : editingHeatPumpLinkId === null ? t("failedLinkHeatPump") : t("failedUpdateHeatPumpRule"));
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setError(null);
    try {
      await deleteProductionSource(sourceId);
      navigate("/production-sources");
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedDelete"));
      setIsDeleting(false);
    }
  };

  const handleDeleteLink = async (linkId: number) => {
    setError(null);
    setIsDeletingLinkId(linkId);

    try {
      await deleteProductionSourceDeviceLink(sourceId, linkId);
      setStandardLinks((current) => current.filter((item) => item.id !== linkId));
      setDeleteLinkConfirmId((current) => (current === linkId ? null : current));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedRemoveRule"));
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
  };

  const handleEditHeatPumpLink = (link: ProductionSourceHeatPumpLink) => {
    setActiveRuleTab("HEAT_PUMP");
    setDeleteHeatPumpConfirmId((current) => (current === link.id ? null : current));
    setEditingHeatPumpLinkId(link.id);
    setSelectedHeatPumpDeviceId(String(link.deviceId));
    setHeatPumpStateHex(link.stateHex ?? "");
    setHeatPumpTriggerKw(String(link.triggerKw));
    setHeatPumpComparisonType(link.comparisonType);
    setHeatPumpAction(link.controlAction);
  };

  const handleDeleteHeatPumpLink = async (linkId: number) => {
    setError(null);
    setIsDeletingHeatPumpId(linkId);
    try {
      await deleteProductionSourceHeatPumpLink(sourceId, linkId);
      setHeatPumpLinks((current) => current.filter((link) => link.id !== linkId));
      setDeleteHeatPumpConfirmId((current) => (current === linkId ? null : current));
      if (editingHeatPumpLinkId === linkId) resetHeatPumpForm();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedRemoveHeatPumpRule"));
    } finally {
      setIsDeletingHeatPumpId((current) => (current === linkId ? null : current));
    }
  };

  const loadHeatPumpStateForDialog = async (deviceId: number) => {
    setIsLoadingHeatPumpState(true);
    setError(null);
    try {
      const response = await fetchHeatPumpState(deviceId);
      const bestState = response.currentState || response.lastPolledState || "";
      setHeatPumpDialogAcType(response.acType);
      setHeatPumpCurrentState(response.currentState);
      setHeatPumpLastPolledState(response.lastPolledState);
      setHeatPumpStateDialogValue(bestState || heatPumpStateHex);
    } catch (stateError) {
      setError(stateError instanceof Error ? stateError.message : t("failedLoadHeatPumpState"));
      setHeatPumpCurrentState(null);
      setHeatPumpLastPolledState(null);
      setHeatPumpDialogAcType("NONE");
      setHeatPumpStateDialogValue(heatPumpStateHex);
    } finally {
      setIsLoadingHeatPumpState(false);
    }
  };

  const handleOpenHeatPumpStateDialog = async () => {
    const deviceId = Number(selectedHeatPumpDeviceId);
    if (!Number.isFinite(deviceId)) return;
    setIsHeatPumpStateDialogOpen(true);
    setHeatPumpStateDialogValue(heatPumpStateHex);
    await loadHeatPumpStateForDialog(deviceId);
  };

  const renderStandardRuleList = () => (
    <div className="space-y-3">
      {standardLinks.map((link) => (
        <div className="rounded-xl bg-surface-container p-4" key={link.id}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-headline font-bold">{link.device.deviceName}</p>
              <p className="text-sm text-on-surface-variant">{label(link.comparisonType)} {link.triggerKw} kW</p>
            </div>
            {deleteLinkConfirmId === link.id ? (
              <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                <div>
                  <p className="font-headline text-sm font-bold text-on-error-container">{common("confirmRemoval")}</p>
                  <p className="text-xs text-on-error-container">{t("removeRuleDescription")}</p>
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
          <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
            <div><span className="metric-label">{common("channel")}</span><p className="font-semibold">{link.deviceChannel}</p></div>
            <div><span className="metric-label">{common("action")}</span><p className="font-semibold">{label(link.action)}</p></div>
          </div>
        </div>
      ))}
      {standardLinks.length === 0 ? <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">{t("noStandardRules")}</div> : null}
    </div>
  );

  const renderHeatPumpRuleList = () => (
    <div className="space-y-3">
      {heatPumpLinks.map((link) => (
        <div className="rounded-xl bg-surface-container p-4" key={link.id}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-headline font-bold">{link.device.deviceName}</p>
              <p className="text-sm text-on-surface-variant">{label(link.comparisonType)} {link.triggerKw} kW</p>
            </div>
            {deleteHeatPumpConfirmId === link.id ? (
              <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                <div>
                  <p className="font-headline text-sm font-bold text-on-error-container">{common("confirmRemoval")}</p>
                  <p className="text-xs text-on-error-container">{t("removeHeatPumpRuleDescription")}</p>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={isDeletingHeatPumpId === link.id}
                    onClick={() => handleDeleteHeatPumpLink(link.id)}
                    type="button"
                  >
                    {isDeletingHeatPumpId === link.id ? common("removing") : common("confirm")}
                  </button>
                  <button
                    className="secondary-action justify-center px-3 py-2 text-xs"
                    disabled={isDeletingHeatPumpId === link.id}
                    onClick={() => setDeleteHeatPumpConfirmId(null)}
                    type="button"
                  >
                    {common("cancel")}
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <button className="secondary-action justify-center px-3 py-2 text-xs" onClick={() => handleEditHeatPumpLink(link)} type="button">{common("edit")}</button>
                <button className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container" onClick={() => setDeleteHeatPumpConfirmId(link.id)} type="button">{common("remove")}</button>
              </div>
            )}
          </div>
          <div className="mt-3 grid gap-2 text-sm md:grid-cols-3">
            <div><span className="metric-label">{common("action")}</span><p className="font-semibold">{label(link.controlAction)}</p></div>
            <div><span className="metric-label">{t("deviceType")}</span><p className="font-semibold">{label(link.device.deviceType)}</p></div>
            <div><span className="metric-label">{t("triggerKw")}</span><p className="font-semibold">{formatKw(link.triggerKw)} kW</p></div>
            <div className="md:col-span-3"><span className="metric-label">{t("stateHex")}</span><p className="mt-1 whitespace-pre-wrap break-all rounded-lg bg-surface-container-highest p-3 font-mono text-xs">{link.stateHex}</p></div>
          </div>
        </div>
      ))}
      {heatPumpLinks.length === 0 ? <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">{t("noHeatPumpRules")}</div> : null}
    </div>
  );

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} title={t("manageTitle")} compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingOne")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}
        {!isLoading && source ? (
          <div className="space-y-8">
            <div className="grid gap-10 lg:grid-cols-12">
              <section className="space-y-8 lg:col-span-8">
                <div><p className="metric-label mb-3">{t("label", { id: sourceId })}</p><h1 className="mb-4 font-headline text-4xl font-extrabold text-primary">{source.name}</h1></div>
                <form className="app-card grid gap-4 p-6 md:grid-cols-2" onSubmit={handleSave}>
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setName(event.target.value)} value={name} />
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setApiType(event.target.value as ProductionApiType)} value={apiType}>{PRODUCTION_API_TYPES.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setTimezone(event.target.value)} value={timezone}>{timezones.map((item) => <option key={item} value={item}>{item}</option>)}</select>
                  <select className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setSiteId(event.target.value)} value={siteId}><option value="">{common("noSite")}</option>{sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select>
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setAppId(event.target.value)} placeholder={t("appId")} value={appId} />
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setAppSecret(event.target.value)} placeholder={t("newAppSecret")} type="password" value={appSecret} />
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setEmail(event.target.value)} placeholder={t("email")} value={email} />
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setPassword(event.target.value)} placeholder={t("newPassword")} type="password" value={password} />
                  <input className="rounded-t-lg bg-surface-container-highest px-4 py-4 outline-none" onChange={(event) => setStationId(event.target.value)} placeholder={t("stationId")} value={stationId} />
                  <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">{common("enabled")}</span><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" /></label>
                  <button className="primary-action justify-center md:col-span-2" type="submit">{t("save")}</button>
                  {message ? <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary md:col-span-2">{message}</div> : null}
                </form>

                <section className="app-card p-6">
                  <div className="mb-5 flex items-center justify-between"><h2 className="font-headline text-xl font-bold">{common("deviceRules")}</h2><span className="chip bg-surface-container-highest text-primary-container">{standardLinks.length + heatPumpLinks.length}</span></div>
                  <div className="mb-6 flex flex-wrap gap-2 rounded-2xl bg-surface-container p-2">
                    <button
                      className={activeRuleTab === "STANDARD" ? "primary-action px-4 py-3 text-sm" : "secondary-action px-4 py-3 text-sm"}
                      onClick={() => setActiveRuleTab("STANDARD")}
                      type="button"
                    >
                      {t("standard")}
                    </button>
                    <button
                      className={activeRuleTab === "HEAT_PUMP" ? "primary-action px-4 py-3 text-sm" : "secondary-action px-4 py-3 text-sm"}
                      onClick={() => setActiveRuleTab("HEAT_PUMP")}
                      type="button"
                    >
                      {t("heatPump")}
                    </button>
                  </div>

                  {activeRuleTab === "STANDARD" ? (
                    <>
                      {renderStandardRuleList()}
                      <form className="mt-6 grid gap-4 border-t border-outline-variant/50 pt-6 md:grid-cols-2" onSubmit={handleAddLink}>
                        <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setSelectedDeviceId(event.target.value)} value={selectedDeviceId}>
                          <option value="">{common("selectStandardDevice")}</option>
                          {standardDevices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                        </select>
                        <input className="rounded-t-lg bg-surface-container-highest px-4 py-3" min="0" onChange={(event) => setDeviceChannel(event.target.value)} type="number" value={deviceChannel} />
                        <input className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setTriggerKw(event.target.value)} step="0.1" type="number" value={triggerKw} />
                        <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setComparisonType(event.target.value as ComparisonType)} value={comparisonType}>{COMPARISONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                        <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setAction(event.target.value as ControlAction)} value={action}>{CONTROL_ACTIONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                        <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedDeviceId} type="submit">{common("addDeviceRule")}</button>
                      </form>
                    </>
                  ) : (
                    <>
                      {renderHeatPumpRuleList()}
                      <form className="mt-6 grid gap-4 border-t border-outline-variant/50 pt-6 md:grid-cols-2" onSubmit={handleAddHeatPumpLink}>
                        <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setSelectedHeatPumpDeviceId(event.target.value)} value={selectedHeatPumpDeviceId}>
                          <option value="">{t("selectHeatPumpDevice")}</option>
                          {heatPumpDevices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                        </select>
                        <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedHeatPumpDeviceId} onClick={handleOpenHeatPumpStateDialog} type="button">{t("queryEditState")}</button>
                        <textarea
                          className="min-h-40 rounded-xl bg-surface-container-highest px-4 py-3 font-mono text-xs outline-none md:col-span-2"
                          onChange={(event) => setHeatPumpStateHex(event.target.value)}
                          placeholder={t("statePlaceholder")}
                          value={heatPumpStateHex}
                        />
                        <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setHeatPumpComparisonType(event.target.value as ComparisonType)} value={heatPumpComparisonType}>{COMPARISONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                        <select className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setHeatPumpAction(event.target.value as ControlAction)} value={heatPumpAction}>{CONTROL_ACTIONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                        <input className="rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setHeatPumpTriggerKw(event.target.value)} step="0.1" type="number" value={heatPumpTriggerKw} />
                        <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">{t("heatPumpRuleHelp")}</div>
                        {editingHeatPumpLinkId === null ? (
                          <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedHeatPumpDeviceId || !heatPumpStateHex.trim()} type="submit">{t("addHeatPumpRule")}</button>
                        ) : (
                          <div className="grid grid-cols-2 gap-3">
                            <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedHeatPumpDeviceId || !heatPumpStateHex.trim()} type="submit">{t("saveHeatPumpRule")}</button>
                            <button className="secondary-action justify-center" onClick={resetHeatPumpForm} type="button">{common("cancel")}</button>
                          </div>
                        )}
                      </form>
                    </>
                  )}
                </section>
              </section>
              <aside className="space-y-6 lg:col-span-4">
                <div className="app-card p-6"><p className="metric-label mb-2">{common("currentKw")}</p><p className="font-headline text-3xl font-bold">{formatKw(source.currentKw)}</p></div>
                <div className="app-card p-6"><p className="metric-label mb-2">{common("peakKw")}</p><p className="font-headline text-3xl font-bold">{formatKw(source.peakKw)}</p></div>
                <div className="app-card p-6"><p className="metric-label mb-2">{common("updated")}</p><p className="font-semibold">{formatDate(source.updatedAt, source.timezone)}</p></div>
                <Link className="secondary-action justify-center" to="/production-sources">{common("back")}</Link>
              </aside>
            </div>

            <ProductionHistoryChartCard
              sourceId={sourceId}
              timezone={source.timezone}
            />

            {!source.shared ? (
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
            ) : null}
          </div>
        ) : null}
      </main>

      <HeatPumpStateDialog
        acType={heatPumpDialogAcType}
        currentState={heatPumpCurrentState}
        formatAcType={label}
        isLoading={isLoadingHeatPumpState}
        isOpen={isHeatPumpStateDialogOpen}
        labels={{
          acType: t("acType"),
          auto: label("AUTO"),
          cancel: common("cancel"),
          close: common("close"),
          cool: label("COOL"),
          dry: label("DRY"),
          fanOnly: label("FAN_ONLY"),
          fanSpeed: t("fanSpeed"),
          heat: label("HEAT"),
          heatPumpState: t("heatPumpState"),
          heatPumpStateHelp: t("heatPumpStateHelp"),
          invalidState: t("invalidMitsubishiState"),
          loading: common("loading"),
          mitsubishiEditorHelp: t("mitsubishiEditorHelp"),
          mode: t("workingMode"),
          off: t("off"),
          on: t("on"),
          power: t("powerMode"),
          rawState: t("rawState"),
          refreshCurrentState: t("refreshCurrentState"),
          saveState: t("saveState"),
          selectCommandState: t("selectCommandState"),
          targetTemperature: t("targetTemperature"),
          useCurrent: t("useCurrent"),
          useLastPolled: t("useLastPolled")
        }}
        lastPolledState={heatPumpLastPolledState}
        onClose={() => setIsHeatPumpStateDialogOpen(false)}
        onRefresh={() => loadHeatPumpStateForDialog(Number(selectedHeatPumpDeviceId))}
        onSave={(value) => {
          setHeatPumpStateHex(value);
          setIsHeatPumpStateDialogOpen(false);
        }}
        onStateChange={setHeatPumpStateDialogValue}
        stateValue={heatPumpStateDialogValue}
      />
    </>
  );
}

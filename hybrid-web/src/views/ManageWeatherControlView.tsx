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
import {
  addWeatherControlDeviceLink,
  addWeatherControlHeatPumpLink,
  type ApiSite,
  type ApiSiteWeatherForecast,
  type ApiWeatherControl,
  COMPARISONS,
  CONTROL_ACTIONS,
  deleteWeatherControlDeviceLink,
  deleteWeatherControlHeatPumpLink,
  fetchSites,
  fetchWeatherControl,
  fetchWeatherControlDeviceLinks,
  fetchWeatherControlHeatPumpLinks,
  fetchWeatherControlWeather,
  formatDate,
  updateWeatherControl,
  updateWeatherControlDeviceLink,
  updateWeatherControlHeatPumpLink,
  WEATHER_METRICS,
  type ComparisonType,
  type ControlAction,
  type WeatherControlDeviceLink,
  type WeatherControlHeatPumpLink,
  type WeatherMetricType
} from "@/lib/automation-resources";
import { fetchDevices, fetchHeatPumpState, type AcType, type ApiDevice } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

const label = (value: string) => value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char: string) => char.toUpperCase());

type RuleTab = "STANDARD" | "HEAT_PUMP";

export default function ManageWeatherControlView() {
  const { t, group } = useI18n("manageWeatherControl");
  const common = useI18n("common").t;
  const metricLabels: Record<string, string> = group("metrics");
  const comparisonLabels: Record<string, string> = group("comparisons");
  const actionLabels: Record<string, string> = group("actions");
  const deviceTypeLabels: Record<string, string> = group("deviceTypes");
  const translatedLabel = (value: string) => metricLabels[value] ?? comparisonLabels[value] ?? actionLabels[value] ?? deviceTypeLabels[value] ?? label(value);
  const params = useParams();
  const weatherControlId = Number(params.weatherControlId);
  const [control, setControl] = useState<ApiWeatherControl | null>(null);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [devices, setDevices] = useState<ApiDevice[]>([]);
  const [standardLinks, setStandardLinks] = useState<WeatherControlDeviceLink[]>([]);
  const [heatPumpLinks, setHeatPumpLinks] = useState<WeatherControlHeatPumpLink[]>([]);
  const [weather, setWeather] = useState<ApiSiteWeatherForecast | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [activeRuleTab, setActiveRuleTab] = useState<RuleTab>("STANDARD");
  const [name, setName] = useState("");
  const [siteId, setSiteId] = useState("");

  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [deviceChannel, setDeviceChannel] = useState("1");
  const [weatherMetric, setWeatherMetric] = useState<WeatherMetricType>("TEMPERATURE");
  const [comparisonType, setComparisonType] = useState<ComparisonType>("GREATER_THAN");
  const [thresholdValue, setThresholdValue] = useState("0");
  const [controlAction, setControlAction] = useState<ControlAction>("TURN_ON");
  const [priorityRule, setPriorityRule] = useState(false);
  const [editingLinkId, setEditingLinkId] = useState<number | null>(null);
  const [deleteLinkConfirmId, setDeleteLinkConfirmId] = useState<number | null>(null);
  const [isDeletingLinkId, setIsDeletingLinkId] = useState<number | null>(null);

  const [selectedHeatPumpDeviceId, setSelectedHeatPumpDeviceId] = useState("");
  const [heatPumpStateHex, setHeatPumpStateHex] = useState("");
  const [heatPumpWeatherMetric, setHeatPumpWeatherMetric] = useState<WeatherMetricType>("TEMPERATURE");
  const [heatPumpComparisonType, setHeatPumpComparisonType] = useState<ComparisonType>("GREATER_THAN");
  const [heatPumpThresholdValue, setHeatPumpThresholdValue] = useState("0");
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

  const resetLinkForm = () => {
    setEditingLinkId(null);
    setSelectedDeviceId("");
    setDeviceChannel("1");
    setWeatherMetric("TEMPERATURE");
    setComparisonType("GREATER_THAN");
    setThresholdValue("0");
    setControlAction("TURN_ON");
    setPriorityRule(false);
  };

  const resetHeatPumpForm = () => {
    setEditingHeatPumpLinkId(null);
    setSelectedHeatPumpDeviceId("");
    setHeatPumpStateHex("");
    setHeatPumpWeatherMetric("TEMPERATURE");
    setHeatPumpComparisonType("GREATER_THAN");
    setHeatPumpThresholdValue("0");
  };

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [controlResponse, siteResponse, deviceLinkResponse, heatPumpLinkResponse, deviceResponse] = await Promise.all([
        fetchWeatherControl(weatherControlId),
        fetchSites(),
        fetchWeatherControlDeviceLinks(weatherControlId),
        fetchWeatherControlHeatPumpLinks(weatherControlId),
        fetchDevices()
      ]);
      setControl(controlResponse);
      setName(controlResponse.name);
      setSiteId(String(controlResponse.siteId));
      setSites(siteResponse);
      setStandardLinks(deviceLinkResponse);
      setHeatPumpLinks(heatPumpLinkResponse);
      setDevices(deviceResponse);
      try {
        setWeather(await fetchWeatherControlWeather(weatherControlId));
      } catch {
        setWeather(null);
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("failedLoad"));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    if (Number.isFinite(weatherControlId)) loadData();
  }, [weatherControlId]);

  if (!Number.isFinite(weatherControlId)) return <Navigate replace to="/weather-controls" />;

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedSiteId = Number(siteId);
    if (!name.trim() || !Number.isFinite(parsedSiteId)) return;
    setError(null);
    setMessage(null);
    try {
      const response = await updateWeatherControl(weatherControlId, { name: name.trim(), siteId: parsedSiteId });
      setControl(response);
      try {
        setWeather(await fetchWeatherControlWeather(weatherControlId));
      } catch {
        setWeather(null);
      }
      setMessage(t("saved"));
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("failedSave"));
    }
  };

  const handleAddLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const deviceId = Number(selectedDeviceId);
    const channel = Number(deviceChannel);
    const threshold = Number(thresholdValue);
    if (!Number.isFinite(deviceId) || !Number.isInteger(channel) || !Number.isFinite(threshold)) return;
    setError(null);
    try {
      const payload = {
        comparisonType,
        controlAction,
        deviceChannel: channel,
        deviceId,
        priorityRule,
        thresholdValue: threshold,
        weatherMetric
      };
      if (editingLinkId === null) {
        await addWeatherControlDeviceLink(weatherControlId, payload);
      } else {
        await updateWeatherControlDeviceLink(editingLinkId, payload);
      }
      setStandardLinks(await fetchWeatherControlDeviceLinks(weatherControlId));
      resetLinkForm();
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : editingLinkId === null ? t("failedLinkDevice") : t("failedUpdateDeviceRule"));
    }
  };

  const handleAddHeatPumpLink = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const deviceId = Number(selectedHeatPumpDeviceId);
    const threshold = Number(heatPumpThresholdValue);
    if (!Number.isFinite(deviceId) || !Number.isFinite(threshold) || !heatPumpStateHex.trim()) return;
    setError(null);
    try {
      const payload = {
        comparisonType: heatPumpComparisonType,
        deviceId,
        stateHex: heatPumpStateHex.trim(),
        thresholdValue: threshold,
        weatherMetric: heatPumpWeatherMetric
      };
      if (editingHeatPumpLinkId === null) {
        await addWeatherControlHeatPumpLink(weatherControlId, payload);
      } else {
        await updateWeatherControlHeatPumpLink(editingHeatPumpLinkId, payload);
      }
      setHeatPumpLinks(await fetchWeatherControlHeatPumpLinks(weatherControlId));
      resetHeatPumpForm();
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : editingHeatPumpLinkId === null ? t("failedLinkHeatPump") : t("failedUpdateHeatPumpRule"));
    }
  };

  const handleEditLink = (link: WeatherControlDeviceLink) => {
    setActiveRuleTab("STANDARD");
    setDeleteLinkConfirmId((current) => (current === link.id ? null : current));
    setEditingLinkId(link.id);
    setSelectedDeviceId(String(link.deviceId));
    setDeviceChannel(String(link.deviceChannel));
    setWeatherMetric(link.weatherMetric);
    setComparisonType(link.comparisonType);
    setThresholdValue(String(link.thresholdValue));
    setControlAction(link.controlAction);
    setPriorityRule(link.priorityRule);
  };

  const handleEditHeatPumpLink = (link: WeatherControlHeatPumpLink) => {
    setActiveRuleTab("HEAT_PUMP");
    setDeleteHeatPumpConfirmId((current) => (current === link.id ? null : current));
    setEditingHeatPumpLinkId(link.id);
    setSelectedHeatPumpDeviceId(String(link.deviceId));
    setHeatPumpStateHex(link.stateHex ?? "");
    setHeatPumpWeatherMetric(link.weatherMetric);
    setHeatPumpComparisonType(link.comparisonType);
    setHeatPumpThresholdValue(String(link.thresholdValue));
  };

  const handleDeleteLink = async (linkId: number) => {
    setError(null);
    setIsDeletingLinkId(linkId);
    try {
      await deleteWeatherControlDeviceLink(linkId);
      setStandardLinks((current) => current.filter((link) => link.id !== linkId));
      setDeleteLinkConfirmId((current) => (current === linkId ? null : current));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("failedRemoveDeviceRule"));
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
  };

  const handleDeleteHeatPumpLink = async (linkId: number) => {
    setError(null);
    setIsDeletingHeatPumpId(linkId);
    try {
      await deleteWeatherControlHeatPumpLink(linkId);
      setHeatPumpLinks((current) => current.filter((link) => link.id !== linkId));
      setDeleteHeatPumpConfirmId((current) => (current === linkId ? null : current));
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

  const currentWeatherPoint = weather?.points?.length
    ? weather.points.reduce((closest, point) => {
        const currentDistance = Math.abs(new Date(point.time).getTime() - Date.now());
        const closestDistance = Math.abs(new Date(closest.time).getTime() - Date.now());

        return currentDistance < closestDistance ? point : closest;
      })
    : null;

  const weatherTimezone = weather?.timezone || control?.siteTimezone || undefined;
  const formatMetric = (value: number | null | undefined, unit: string) =>
    typeof value === "number" && Number.isFinite(value) ? `${value.toFixed(1)} ${unit}` : "-";

  const renderStandardRuleList = () => (
    <div className="space-y-3">
      {standardLinks.map((link) => (
        <div className="rounded-xl bg-surface-container p-4" key={link.id}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-headline font-bold">{link.device.deviceName}</p>
              <p className="text-sm text-on-surface-variant">{translatedLabel(link.weatherMetric)} {translatedLabel(link.comparisonType)} {link.thresholdValue}</p>
            </div>
            {deleteLinkConfirmId === link.id ? (
              <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                <div>
                  <p className="font-headline text-sm font-bold text-on-error-container">{common("confirmRemoval")}</p>
                  <p className="text-xs text-on-error-container">{t("removeDeviceRuleDescription")}</p>
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
              <div className="flex items-center gap-2">
                <button className="secondary-action justify-center px-3 py-2 text-xs" onClick={() => handleEditLink(link)} type="button">{common("edit")}</button>
                <button className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container" onClick={() => setDeleteLinkConfirmId(link.id)} type="button">{common("remove")}</button>
              </div>
            )}
          </div>
          <div className="mt-3 grid grid-cols-3 gap-2 text-sm">
            <div><span className="metric-label">{common("channel")}</span><p className="font-semibold">{link.deviceChannel}</p></div>
            <div><span className="metric-label">{common("action")}</span><p className="font-semibold">{translatedLabel(link.controlAction)}</p></div>
            <div><span className="metric-label">{t("priority")}</span><p className="font-semibold">{link.priorityRule ? common("yes") : common("no")}</p></div>
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
              <p className="text-sm text-on-surface-variant">{translatedLabel(link.weatherMetric)} {translatedLabel(link.comparisonType)} {link.thresholdValue}</p>
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
          <div className="mt-3 grid gap-2 text-sm md:grid-cols-2">
            <div><span className="metric-label">{t("state")}</span><p className="mt-1 whitespace-pre-wrap break-all rounded-lg bg-surface-container-highest p-3 font-mono text-xs">{link.stateHex}</p></div>
            <div><span className="metric-label">{t("type")}</span><p className="font-semibold">{translatedLabel(link.device.deviceType)}</p></div>
          </div>
        </div>
      ))}
      {heatPumpLinks.length === 0 ? <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">{t("noHeatPumpRules")}</div> : null}
    </div>
  );

  return (
    <>
      <PageHeader rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>} title={t("title")} compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">{t("loading")}</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}
        {!isLoading && control ? (
          <div className="grid gap-10 lg:grid-cols-12">
            <section className="space-y-8 lg:col-span-8">
              <div>
                <p className="metric-label mb-3">{t("label", { id: weatherControlId })}</p>
                <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary">{control.name}</h1>
                <p className="text-lg text-on-surface-variant">{t("description")}</p>
              </div>

              <form className="app-card grid gap-6 p-6 md:grid-cols-2" onSubmit={handleSave}>
                <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary" onChange={(event) => setName(event.target.value)} value={name} />
                <select className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary" onChange={(event) => setSiteId(event.target.value)} value={siteId}>
                  {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
                </select>
                <button className="primary-action justify-center md:col-span-2" type="submit">{t("save")}</button>
                {message ? <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary md:col-span-2">{message}</div> : null}
              </form>

              <section className="app-card p-6">
                <div className="mb-5 flex items-center justify-between">
                  <h2 className="font-headline text-xl font-bold">{common("deviceRules")}</h2>
                  <span className="chip bg-surface-container-highest text-primary-container">{standardLinks.length + heatPumpLinks.length}</span>
                </div>

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
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setSelectedDeviceId(event.target.value)} value={selectedDeviceId}>
                        <option value="">{common("selectStandardDevice")}</option>
                        {standardDevices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                      </select>
                      <input className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" min="0" onChange={(event) => setDeviceChannel(event.target.value)} type="number" value={deviceChannel} />
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setWeatherMetric(event.target.value as WeatherMetricType)} value={weatherMetric}>{WEATHER_METRICS.map((item) => <option key={item} value={item}>{translatedLabel(item)}</option>)}</select>
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setComparisonType(event.target.value as ComparisonType)} value={comparisonType}>{COMPARISONS.map((item) => <option key={item} value={item}>{translatedLabel(item)}</option>)}</select>
                      <input className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setThresholdValue(event.target.value)} step="0.1" type="number" value={thresholdValue} />
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setControlAction(event.target.value as ControlAction)} value={controlAction}>{CONTROL_ACTIONS.map((item) => <option key={item} value={item}>{translatedLabel(item)}</option>)}</select>
                      <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">{t("priorityRule")}</span><input checked={priorityRule} onChange={(event) => setPriorityRule(event.target.checked)} type="checkbox" /></label>
                      {editingLinkId === null ? (
                        <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedDeviceId} type="submit">{common("addDeviceRule")}</button>
                      ) : (
                        <div className="grid grid-cols-2 gap-3">
                          <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedDeviceId} type="submit">{t("saveDeviceRule")}</button>
                          <button className="secondary-action justify-center" onClick={resetLinkForm} type="button">{common("cancel")}</button>
                        </div>
                      )}
                    </form>
                  </>
                ) : (
                  <>
                    {renderHeatPumpRuleList()}
                    <form className="mt-6 grid gap-4 border-t border-outline-variant/50 pt-6 md:grid-cols-2" onSubmit={handleAddHeatPumpLink}>
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setSelectedHeatPumpDeviceId(event.target.value)} value={selectedHeatPumpDeviceId}>
                        <option value="">{t("selectHeatPumpDevice")}</option>
                        {heatPumpDevices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                      </select>
                      <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedHeatPumpDeviceId} onClick={handleOpenHeatPumpStateDialog} type="button">{t("queryEditState")}</button>
                      <textarea
                        className="min-h-40 w-full rounded-xl bg-surface-container-highest px-4 py-3 font-mono text-xs outline-none md:col-span-2"
                        onChange={(event) => setHeatPumpStateHex(event.target.value)}
                        placeholder={t("statePlaceholder")}
                        value={heatPumpStateHex}
                      />
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setHeatPumpWeatherMetric(event.target.value as WeatherMetricType)} value={heatPumpWeatherMetric}>{WEATHER_METRICS.map((item) => <option key={item} value={item}>{translatedLabel(item)}</option>)}</select>
                      <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setHeatPumpComparisonType(event.target.value as ComparisonType)} value={heatPumpComparisonType}>{COMPARISONS.map((item) => <option key={item} value={item}>{translatedLabel(item)}</option>)}</select>
                      <input className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setHeatPumpThresholdValue(event.target.value)} step="0.1" type="number" value={heatPumpThresholdValue} />
                      <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">
                        {t("heatPumpRuleHelp")}
                      </div>
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
              <div className="app-card p-6"><p className="metric-label mb-2">{common("origin")}</p><p className="font-headline text-2xl font-bold">{control.shared ? common("shared") : common("mine")}</p></div>
              <div className="app-card p-6">
                <div className="mb-4 flex items-start justify-between gap-3">
                  <div>
                    <p className="metric-label mb-2">{t("currentWeather")}</p>
                    <p className="font-headline text-2xl font-bold">{weather?.weatherPlace || control.siteName || t("weather")}</p>
                  </div>
                  <span className="chip bg-surface-container-highest text-primary-container">{t("live")}</span>
                </div>

                {currentWeatherPoint ? (
                  <div className="space-y-4">
                    <div className="rounded-2xl bg-surface-container p-4">
                      <span className="metric-label">{t("observationTime")}</span>
                      <p className="mt-1 font-semibold text-on-surface">{formatDate(currentWeatherPoint.time, weatherTimezone)}</p>
                    </div>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 lg:grid-cols-1">
                      <div className="rounded-2xl bg-surface-container-low p-4">
                        <span className="metric-label">{t("temperature")}</span>
                        <p className="mt-1 font-headline text-xl font-black text-on-surface">{formatMetric(currentWeatherPoint.temperature, "°C")}</p>
                      </div>
                      <div className="rounded-2xl bg-surface-container-low p-4">
                        <span className="metric-label">{t("windSpeed")}</span>
                        <p className="mt-1 font-headline text-xl font-black text-on-surface">{formatMetric(currentWeatherPoint.windSpeedMs, "m/s")}</p>
                      </div>
                      <div className="rounded-2xl bg-surface-container-low p-4">
                        <span className="metric-label">{t("humidity")}</span>
                        <p className="mt-1 font-headline text-xl font-black text-on-surface">{formatMetric(currentWeatherPoint.humidity, "%")}</p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-2xl bg-surface-container p-4 text-sm text-on-surface-variant">
                    {t("weatherUnavailable")}
                  </div>
                )}
              </div>
              <div className="app-card p-6"><p className="metric-label mb-2">{common("created")}</p><p className="font-semibold">{formatDate(control.createdAt, control.siteTimezone)}</p></div>
              <div className="app-card p-6"><p className="metric-label mb-2">{common("updated")}</p><p className="font-semibold">{formatDate(control.updatedAt, control.siteTimezone)}</p></div>
              <Link className="secondary-action justify-center" to="/weather-controls">{common("back")}</Link>
            </aside>
          </div>
        ) : null}
      </main>

      <HeatPumpStateDialog
        acType={heatPumpDialogAcType}
        currentState={heatPumpCurrentState}
        formatAcType={translatedLabel}
        isLoading={isLoadingHeatPumpState}
        isOpen={isHeatPumpStateDialogOpen}
        labels={{
          acType: t("acType"),
          auto: translatedLabel("AUTO"),
          cancel: common("cancel"),
          close: common("close"),
          cool: translatedLabel("COOL"),
          dry: translatedLabel("DRY"),
          fanOnly: translatedLabel("FAN_ONLY"),
          fanSpeed: t("fanSpeed"),
          heat: translatedLabel("HEAT"),
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

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
  addWeatherControlDeviceLink,
  type ApiSiteWeatherForecast,
  COMPARISONS,
  CONTROL_ACTIONS,
  deleteWeatherControlDeviceLink,
  fetchSites,
  fetchWeatherControl,
  fetchWeatherControlWeather,
  fetchWeatherControlDeviceLinks,
  formatDate,
  updateWeatherControl,
  WEATHER_METRICS,
  type ApiSite,
  type ApiWeatherControl,
  type ComparisonType,
  type ControlAction,
  type WeatherControlDeviceLink,
  type WeatherMetricType
} from "@/lib/automation-resources";
import { fetchDevices, type ApiDevice } from "@/lib/devices";
import { FormEvent, useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

const label = (value: string) => value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (char: string) => char.toUpperCase());

export default function ManageWeatherControlView() {
  const params = useParams();
  const weatherControlId = Number(params.weatherControlId);
  const [control, setControl] = useState<ApiWeatherControl | null>(null);
  const [sites, setSites] = useState<ApiSite[]>([]);
  const [devices, setDevices] = useState<ApiDevice[]>([]);
  const [links, setLinks] = useState<WeatherControlDeviceLink[]>([]);
  const [weather, setWeather] = useState<ApiSiteWeatherForecast | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [siteId, setSiteId] = useState("");
  const [selectedDeviceId, setSelectedDeviceId] = useState("");
  const [deviceChannel, setDeviceChannel] = useState("1");
  const [weatherMetric, setWeatherMetric] = useState<WeatherMetricType>("TEMPERATURE");
  const [comparisonType, setComparisonType] = useState<ComparisonType>("GREATER_THAN");
  const [thresholdValue, setThresholdValue] = useState("0");
  const [controlAction, setControlAction] = useState<ControlAction>("TURN_ON");
  const [priorityRule, setPriorityRule] = useState(false);
  const [deleteLinkConfirmId, setDeleteLinkConfirmId] = useState<number | null>(null);
  const [isDeletingLinkId, setIsDeletingLinkId] = useState<number | null>(null);

  async function loadData() {
    setIsLoading(true);
    setError(null);
    try {
      const [controlResponse, siteResponse, linkResponse, deviceResponse] = await Promise.all([
        fetchWeatherControl(weatherControlId),
        fetchSites(),
        fetchWeatherControlDeviceLinks(weatherControlId),
        fetchDevices()
      ]);
      setControl(controlResponse);
      setName(controlResponse.name);
      setSiteId(String(controlResponse.siteId));
      setSites(siteResponse);
      setLinks(linkResponse);
      setDevices(deviceResponse.filter((device) => device.deviceType === "STANDARD" && !device.shared));
      try {
        setWeather(await fetchWeatherControlWeather(weatherControlId));
      } catch {
        setWeather(null);
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load weather control");
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
      setMessage("Weather control saved.");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Failed to save weather control");
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
      await addWeatherControlDeviceLink(weatherControlId, {
        comparisonType,
        controlAction,
        deviceChannel: channel,
        deviceId,
        priorityRule,
        thresholdValue: threshold,
        weatherMetric
      });
      setLinks(await fetchWeatherControlDeviceLinks(weatherControlId));
      setSelectedDeviceId("");
      setThresholdValue("0");
      setPriorityRule(false);
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : "Failed to link device");
    }
  };

  const handleDeleteLink = async (linkId: number) => {
    setError(null);
    setIsDeletingLinkId(linkId);
    try {
      await deleteWeatherControlDeviceLink(linkId);
      setLinks((current) => current.filter((link) => link.id !== linkId));
      setDeleteLinkConfirmId((current) => (current === linkId ? null : current));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Failed to remove device rule");
    } finally {
      setIsDeletingLinkId((current) => (current === linkId ? null : current));
    }
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

  return (
    <>
      <PageHeader title="Manage Weather Control" compact />
      <main className="app-page pb-8 pt-4 sm:py-8">
        {isLoading ? <div className="app-card p-6 text-sm text-on-surface-variant">Loading weather control...</div> : null}
        {error ? <div className="app-card mb-6 border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">{error}</div> : null}
        {!isLoading && control ? (
          <div className="grid gap-10 lg:grid-cols-12">
            <section className="space-y-8 lg:col-span-8">
              <div>
                <p className="metric-label mb-3">Weather Control #{weatherControlId}</p>
                <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary">{control.name}</h1>
                <p className="text-lg text-on-surface-variant">Edit the site and weather threshold device rules.</p>
              </div>

              <form className="app-card grid gap-6 p-6 md:grid-cols-2" onSubmit={handleSave}>
                <input className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary" onChange={(event) => setName(event.target.value)} value={name} />
                <select className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 outline-none focus:border-primary" onChange={(event) => setSiteId(event.target.value)} value={siteId}>
                  {sites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}
                </select>
                <button className="primary-action justify-center md:col-span-2" type="submit">Save Weather Control</button>
                {message ? <div className="rounded-xl bg-primary-fixed p-4 text-sm font-semibold text-primary md:col-span-2">{message}</div> : null}
              </form>

              <section className="app-card p-6">
                <div className="mb-5 flex items-center justify-between">
                  <h2 className="font-headline text-xl font-bold">Device Rules</h2>
                  <span className="chip bg-surface-container-highest text-primary-container">{links.length}</span>
                </div>
                <div className="space-y-3">
                  {links.map((link) => (
                    <div className="rounded-xl bg-surface-container p-4" key={link.id}>
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-headline font-bold">{link.device.deviceName}</p>
                          <p className="text-sm text-on-surface-variant">{label(link.weatherMetric)} {label(link.comparisonType)} {link.thresholdValue}</p>
                        </div>
                        {deleteLinkConfirmId === link.id ? (
                          <div className="min-w-[10rem] space-y-3 rounded-xl bg-error-container/70 p-3">
                            <div>
                              <p className="font-headline text-sm font-bold text-on-error-container">Confirm removal</p>
                              <p className="text-xs text-on-error-container">This removes the device rule from this weather control.</p>
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
                      <div className="mt-3 grid grid-cols-3 gap-2 text-sm">
                        <div><span className="metric-label">Channel</span><p className="font-semibold">{link.deviceChannel}</p></div>
                        <div><span className="metric-label">Action</span><p className="font-semibold">{label(link.controlAction)}</p></div>
                        <div><span className="metric-label">Priority</span><p className="font-semibold">{link.priorityRule ? "Yes" : "No"}</p></div>
                      </div>
                    </div>
                  ))}
                </div>

                <form className="mt-6 grid gap-4 border-t border-outline-variant/50 pt-6 md:grid-cols-2" onSubmit={handleAddLink}>
                  <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setSelectedDeviceId(event.target.value)} value={selectedDeviceId}>
                    <option value="">Select standard device</option>
                    {devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                  </select>
                  <input className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" min="0" onChange={(event) => setDeviceChannel(event.target.value)} type="number" value={deviceChannel} />
                  <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setWeatherMetric(event.target.value as WeatherMetricType)} value={weatherMetric}>{WEATHER_METRICS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                  <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setComparisonType(event.target.value as ComparisonType)} value={comparisonType}>{COMPARISONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                  <input className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setThresholdValue(event.target.value)} step="0.1" type="number" value={thresholdValue} />
                  <select className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" onChange={(event) => setControlAction(event.target.value as ControlAction)} value={controlAction}>{CONTROL_ACTIONS.map((item) => <option key={item} value={item}>{label(item)}</option>)}</select>
                  <label className="flex items-center justify-between rounded-xl bg-surface-container p-4"><span className="font-headline text-sm font-bold">Priority Rule</span><input checked={priorityRule} onChange={(event) => setPriorityRule(event.target.checked)} type="checkbox" /></label>
                  <button className="secondary-action justify-center disabled:opacity-60" disabled={!selectedDeviceId} type="submit">Add Device Rule</button>
                </form>
              </section>
            </section>
            <aside className="space-y-6 lg:col-span-4">
              <div className="app-card p-6"><p className="metric-label mb-2">Origin</p><p className="font-headline text-2xl font-bold">{control.shared ? "Shared" : "Mine"}</p></div>
              <div className="app-card p-6">
                <div className="mb-4 flex items-start justify-between gap-3">
                  <div>
                    <p className="metric-label mb-2">Current Weather</p>
                    <p className="font-headline text-2xl font-bold">{weather?.weatherPlace || control.siteName || "Weather"}</p>
                  </div>
                  <span className="chip bg-surface-container-highest text-primary-container">Live</span>
                </div>

                {currentWeatherPoint ? (
                  <div className="space-y-4">
                    <div className="rounded-2xl bg-surface-container p-4">
                      <span className="metric-label">Observation Time</span>
                      <p className="mt-1 font-semibold text-on-surface">{formatDate(currentWeatherPoint.time, weatherTimezone)}</p>
                    </div>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 lg:grid-cols-1">
                      <div className="rounded-2xl bg-surface-container-low p-4">
                        <span className="metric-label">Temperature</span>
                        <p className="mt-1 font-headline text-xl font-black text-on-surface">{formatMetric(currentWeatherPoint.temperature, "°C")}</p>
                      </div>
                      <div className="rounded-2xl bg-surface-container-low p-4">
                        <span className="metric-label">Wind Speed</span>
                        <p className="mt-1 font-headline text-xl font-black text-on-surface">{formatMetric(currentWeatherPoint.windSpeedMs, "m/s")}</p>
                      </div>
                      <div className="rounded-2xl bg-surface-container-low p-4">
                        <span className="metric-label">Humidity</span>
                        <p className="mt-1 font-headline text-xl font-black text-on-surface">{formatMetric(currentWeatherPoint.humidity, "%")}</p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-2xl bg-surface-container p-4 text-sm text-on-surface-variant">
                    Weather data is not available for this site yet.
                  </div>
                )}
              </div>
              <div className="app-card p-6"><p className="metric-label mb-2">Created</p><p className="font-semibold">{formatDate(control.createdAt, control.siteTimezone)}</p></div>
              <div className="app-card p-6"><p className="metric-label mb-2">Updated</p><p className="font-semibold">{formatDate(control.updatedAt, control.siteTimezone)}</p></div>
              <Link className="secondary-action justify-center" to="/weather-controls">Back</Link>
            </aside>
          </div>
        ) : null}
      </main>
    </>
  );
}

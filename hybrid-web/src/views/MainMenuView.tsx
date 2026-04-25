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
import NordpoolTodayChartCard from "@/components/NordpoolTodayChartCard";
import { Link } from "react-router-dom";
import { formatKw } from "@/lib/account-stats";
import { useAccountStats } from "@/hooks/useAccountStats";
import { useControls } from "@/hooks/useControls";
import { useDevices } from "@/hooks/useDevices";
import { logoutNative } from "@/lib/android-bridge";
import { clearBrowserSession, getSessionData } from "@/lib/session";

export default function MainMenuView() {
  const session = getSessionData();
  const { error, isLoading, onlineCount, totalCount } = useDevices();
  const {
    error: controlsError,
    isLoading: isControlsLoading,
    totalCount: controlsCount
  } = useControls();
  const {
    error: statsError,
    isLoading: isStatsLoading,
    totalConsumptionKw,
    totalProductionKw,
    totalProductionPeakKw
  } = useAccountStats();
  const deviceTileDetail = isLoading
    ? "Loading devices..."
    : error
      ? "Device sync unavailable"
      : `${totalCount} Active • ${onlineCount} Online`;
  const ownProductionDetail = isStatsLoading
    ? "Loading production..."
    : statsError
      ? "Production sync unavailable"
      : `Generating ${formatKw(totalProductionKw)} kW`;
  const powerLimitsDetail = isStatsLoading
    ? "Loading power limits..."
    : statsError
      ? "Power limit sync unavailable"
      : `${formatKw(totalConsumptionKw)} kW active`;
  const controlsDetail = isControlsLoading
    ? "Loading controls..."
    : controlsError
      ? "Control sync unavailable"
      : `${controlsCount} Controls configured`;
  const tiles = [
    { title: "Devices", detail: deviceTileDetail, to: "/devices", icon: "D" },
    { title: "Controls", detail: controlsDetail, to: "/controls", icon: "C" },
    { title: "Weather Controls", detail: "Weather threshold automation", to: "/weather-controls", icon: "W" },
    { title: "Own Production", detail: ownProductionDetail, to: "/production-sources", icon: "P" },
    { title: "Power Limits", detail: powerLimitsDetail, to: "/power-limits", icon: "L" }
  ];
  const productionRatio = totalProductionPeakKw > 0 ? Math.min(totalProductionKw / totalProductionPeakKw, 1) : 0;
  const productionBarWidth = `${Math.max(productionRatio * 100, 6)}%`;
  const consumptionLabel = isStatsLoading ? "--" : formatKw(totalConsumptionKw);
  const productionLabel = isStatsLoading ? "--" : formatKw(totalProductionKw, true);
  const netPowerKw = totalProductionKw - totalConsumptionKw;
  const summaryText = (isLoading && isStatsLoading)
    ? "Loading your current device status and live power data."
    : (error && statsError)
      ? "Live dashboard data is currently unavailable. Check the local API connection and token configuration."
      : statsError
        ? `Device inventory shows ${onlineCount} of ${totalCount} devices online. Live power stats are currently unavailable.`
        : error
          ? `${formatKw(totalConsumptionKw)} kW is currently managed across your power limits, with ${formatKw(totalProductionKw)} kW of production available right now.`
          : netPowerKw >= 0
            ? `${onlineCount} of ${totalCount} devices are online. Solar production is ahead by ${formatKw(netPowerKw)} kW against ${formatKw(totalConsumptionKw)} kW of current consumption.`
            : `${onlineCount} of ${totalCount} devices are online. Current consumption is ${formatKw(totalConsumptionKw)} kW, which is ${formatKw(Math.abs(netPowerKw))} kW above solar production.`;
  const handleLogout = () => {
    if (session.source === "android") {
      logoutNative();
      return;
    }

    clearBrowserSession();
    window.location.hash = "#/login";
  };

  return (
    <>
      <PageHeader
        brand="Energy Controller"
        translucent
        rightSlot={(
          <button
            className="secondary-action px-4 py-2 text-sm"
            onClick={handleLogout}
            type="button"
          >
            Log Out
          </button>
        )}
      />

      <main className="mx-auto max-w-7xl px-4 pb-36 pt-4 sm:px-6">
        <section className="mb-12">
          <div className="grid grid-cols-1 items-end gap-8 lg:grid-cols-12">
            <div className="lg:col-span-7">
              <span className="mb-3 block text-xs font-bold uppercase tracking-widest text-primary">Live Dashboard</span>
              <h1 className="mb-6 font-headline text-4xl font-extrabold leading-none tracking-tight text-on-surface sm:text-5xl md:text-7xl">
                System <br />
                <span className="text-primary-container">Optimized.</span>
              </h1>
              <p className="max-w-md text-lg leading-relaxed text-on-surface-variant">
                {summaryText}
              </p>
            </div>

            <div className="lg:col-span-5">
              <div className="signature-gradient group relative overflow-hidden rounded-xl p-8 text-on-primary shadow-2xl transition-transform duration-300 hover:-translate-y-1 hover:shadow-[0_24px_48px_rgba(0,67,66,0.24)]">
                <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-secondary-container opacity-10 blur-3xl transition-transform duration-500 group-hover:scale-125" />
                <div className="mb-10 flex items-start justify-between">
                  <div>
                    <p className="mb-1 text-sm font-semibold uppercase tracking-wider text-primary-fixed">Total Consumption</p>
                    <div className="flex items-baseline gap-2">
                      <span className="font-headline text-5xl font-extrabold">{consumptionLabel}</span>
                      <span className="text-xl font-medium opacity-80">kW</span>
                    </div>
                  </div>
                  <span className="font-headline text-4xl font-black text-secondary-container">⚡</span>
                </div>
                <div className="space-y-4">
                  <div className="flex items-center justify-between text-sm">
                    <span className="opacity-70">Solar Production</span>
                    <span className="font-bold text-secondary-container">{productionLabel} kW</span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-primary/30">
                    <div
                      className="h-full origin-left bg-secondary-container transition-[width,transform] duration-700 group-hover:scale-x-105"
                      style={{ width: productionBarWidth }}
                    />
                  </div>
                  {statsError ? <p className="text-xs text-primary-fixed">Stats sync unavailable.</p> : null}
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="mb-12">
          <h2 className="mb-8 flex items-center gap-3 text-2xl font-bold">
            <span className="h-1 w-8 rounded-full bg-primary" />
            Control Center
          </h2>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-5">
            {tiles.map((tile) => (
              <Link
                key={tile.title}
                className="group relative overflow-hidden rounded-xl bg-surface-container-low p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft active:scale-[0.98]"
                to={tile.to}
              >
                <div className="absolute inset-x-0 top-0 h-1 origin-left scale-x-0 bg-primary transition-transform duration-300 group-hover:scale-x-100" />
                <div className="flex h-full flex-col justify-between gap-12">
                  <div className="flex items-start justify-between">
                    <div className="rounded-lg bg-white p-3 shadow-sm transition-all duration-300 group-hover:-translate-y-0.5 group-hover:scale-110 group-hover:shadow-md">
                      <span className="font-headline text-xl font-black text-primary">{tile.icon}</span>
                    </div>
                    <span className="translate-y-1 text-lg text-outline-variant opacity-0 transition-all duration-300 group-hover:translate-y-0 group-hover:text-primary group-hover:opacity-100">↗</span>
                  </div>
                  <div>
                    <h3 className="text-xl font-bold transition-colors duration-300 group-hover:text-primary">{tile.title}</h3>
                    <p
                      className={`text-sm ${
                        (tile.title === "Devices" && error) || ((tile.title === "Own Production" || tile.title === "Power Limits") && statsError) || (tile.title === "Controls" && controlsError)
                          ? "text-on-error-container"
                          : "text-on-surface-variant"
                      }`}
                    >
                      {tile.detail}
                    </p>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </section>

        <section className="mb-12">
          <h2 className="mb-8 flex items-center gap-3 text-2xl font-bold">
            <span className="h-1 w-8 rounded-full bg-primary" />
            Market Watch
          </h2>
          <NordpoolTodayChartCard />
        </section>
      </main>
    </>
  );
}

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
import { fetchSites } from "@/lib/automation-resources";
import { fetchElectricityContracts } from "@/lib/electricity-contracts";
import { Link } from "react-router-dom";
import { formatKw } from "@/lib/account-stats";
import { useAccountStats } from "@/hooks/useAccountStats";
import { useControls } from "@/hooks/useControls";
import { useDevices } from "@/hooks/useDevices";
import { logoutNative } from "@/lib/android-bridge";
import { useI18n } from "@/lib/i18n";
import { clearBrowserSession, getSessionData } from "@/lib/session";
import { useEffect, useState } from "react";

export default function MainMenuView() {
  const session = getSessionData();
  const { group, t } = useI18n("mainMenu");
  const tileTitles = group("tileTitles");
  const [sitesCount, setSitesCount] = useState<number | null>(null);
  const [contractsCount, setContractsCount] = useState<number | null>(null);
  const [sitesError, setSitesError] = useState(false);
  const [contractsError, setContractsError] = useState(false);
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
    ? t("loadingDevices")
    : error
      ? t("deviceSyncUnavailable")
      : t("deviceCounts", { totalCount, onlineCount });
  const ownProductionDetail = isStatsLoading
    ? t("loadingProduction")
    : statsError
      ? t("productionSyncUnavailable")
      : t("generatingPower", { kw: formatKw(totalProductionKw) });
  const powerLimitsDetail = isStatsLoading
    ? t("loadingPowerLimits")
    : statsError
      ? t("powerLimitSyncUnavailable")
      : t("activePower", { kw: formatKw(totalConsumptionKw) });
  const controlsDetail = isControlsLoading
    ? t("loadingControls")
    : controlsError
      ? t("controlSyncUnavailable")
      : t("controlsConfigured", { count: controlsCount });
  const sitesDetail = sitesError
    ? t("siteSyncUnavailable")
    : sitesCount === null
      ? t("loadingSites")
      : t("sitesConfigured", { count: sitesCount });
  const contractsDetail = contractsError
    ? t("contractSyncUnavailable")
    : contractsCount === null
      ? t("loadingContracts")
      : t("contractsConfigured", { count: contractsCount });

  useEffect(() => {
    let active = true;

    fetchSites()
      .then((sites) => {
        if (!active) return;
        setSitesCount(sites.length);
        setSitesError(false);
      })
      .catch(() => {
        if (!active) return;
        setSitesError(true);
      });

    fetchElectricityContracts()
      .then((contracts) => {
        if (!active) return;
        setContractsCount(contracts.length);
        setContractsError(false);
      })
      .catch(() => {
        if (!active) return;
        setContractsError(true);
      });

    return () => {
      active = false;
    };
  }, []);

  const tiles = [
    { key: "devices", title: tileTitles.devices, detail: deviceTileDetail, to: "/devices", icon: "D", hasError: Boolean(error) },
    { key: "controls", title: tileTitles.controls, detail: controlsDetail, to: "/controls", icon: "C", hasError: Boolean(controlsError) },
    { key: "weatherControls", title: tileTitles.weatherControls, detail: t("weatherThresholdAutomation"), to: "/weather-controls", icon: "W", hasError: false },
    { key: "ownProduction", title: tileTitles.ownProduction, detail: ownProductionDetail, to: "/production-sources", icon: "P", hasError: Boolean(statsError) },
    { key: "powerLimits", title: tileTitles.powerLimits, detail: powerLimitsDetail, to: "/power-limits", icon: "L", hasError: Boolean(statsError) }
  ];
  const siteOwnTiles = [
    {
      key: "accountSettings",
      title: t("accountSettingsTitle"),
      detail: t("accountSettingsDescription"),
      to: "/account/settings",
      icon: "A",
      hasError: false
    },
    {
      key: "sites",
      title: tileTitles.sites,
      detail: sitesDetail,
      to: "/sites",
      icon: "S",
      hasError: sitesError
    },
    {
      key: "electricityContracts",
      title: tileTitles.electricityContracts,
      detail: contractsDetail,
      to: "/electricity-contracts",
      icon: "E",
      hasError: contractsError
    }
  ];
  const productionRatio = totalProductionPeakKw > 0 ? Math.min(totalProductionKw / totalProductionPeakKw, 1) : 0;
  const productionBarWidth = `${Math.max(productionRatio * 100, 6)}%`;
  const consumptionLabel = isStatsLoading ? "--" : formatKw(totalConsumptionKw);
  const productionLabel = isStatsLoading ? "--" : formatKw(totalProductionKw, true);
  const netPowerKw = totalProductionKw - totalConsumptionKw;
  const summaryText = (isLoading && isStatsLoading)
    ? t("summaryLoading")
    : (error && statsError)
      ? t("summaryUnavailable")
      : statsError
        ? t("summaryStatsUnavailable", { onlineCount, totalCount })
        : error
          ? t("summaryDevicesUnavailable", { consumptionKw: formatKw(totalConsumptionKw), productionKw: formatKw(totalProductionKw) })
          : netPowerKw >= 0
            ? t("summaryProductionAhead", { onlineCount, totalCount, netKw: formatKw(netPowerKw), consumptionKw: formatKw(totalConsumptionKw) })
            : t("summaryConsumptionAhead", { onlineCount, totalCount, consumptionKw: formatKw(totalConsumptionKw), netKw: formatKw(Math.abs(netPowerKw)) });
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
        brand={t("brand")}
        translucent
        rightSlot={(
          <button
            className="secondary-action px-4 py-2 text-sm"
            onClick={handleLogout}
            type="button"
          >
            {t("logout")}
          </button>
        )}
      />

      <main className="mx-auto max-w-7xl px-4 pb-36 pt-4 sm:px-6">
        <section className="mb-12">
          <div className="grid grid-cols-1 items-end gap-8 lg:grid-cols-12">
            <div className="lg:col-span-7">
              <span className="mb-3 block text-xs font-bold uppercase tracking-widest text-primary">{t("liveDashboard")}</span>
              <h1 className="mb-6 font-headline text-4xl font-extrabold leading-none tracking-tight text-on-surface sm:text-5xl md:text-7xl">
                {t("headlineTop")} <br />
                <span className="text-primary-container">{t("headlineHighlight")}</span>
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
                    <p className="mb-1 text-sm font-semibold uppercase tracking-wider text-primary-fixed">{t("totalConsumption")}</p>
                    <div className="flex items-baseline gap-2">
                      <span className="font-headline text-5xl font-extrabold">{consumptionLabel}</span>
                      <span className="text-xl font-medium opacity-80">kW</span>
                    </div>
                  </div>
                  <span className="font-headline text-4xl font-black text-secondary-container">⚡</span>
                </div>
                <div className="space-y-4">
                  <div className="flex items-center justify-between text-sm">
                    <span className="opacity-70">{t("solarProduction")}</span>
                    <span className="font-bold text-secondary-container">{productionLabel} kW</span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-primary/30">
                    <div
                      className="h-full origin-left bg-secondary-container transition-[width,transform] duration-700 group-hover:scale-x-105"
                      style={{ width: productionBarWidth }}
                    />
                  </div>
                  {statsError ? <p className="text-xs text-primary-fixed">{t("statsSyncUnavailable")}</p> : null}
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="mb-12">
          <h2 className="mb-8 flex items-center gap-3 text-2xl font-bold">
            <span className="h-1 w-8 rounded-full bg-primary" />
            {t("controlCenter")}
          </h2>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-5">
            {tiles.map((tile) => (
              <Link
                key={tile.key}
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
                        tile.hasError
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
            {t("marketWatch")}
          </h2>
          <NordpoolTodayChartCard />
        </section>

        <section className="mb-12">
          <h2 className="mb-8 flex items-center gap-3 text-2xl font-bold">
            <span className="h-1 w-8 rounded-full bg-primary" />
            {t("siteOwn")}
          </h2>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
            {siteOwnTiles.map((tile) => (
              <Link
                key={tile.key}
                className="group relative overflow-hidden rounded-xl bg-surface-container-low p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft active:scale-[0.98]"
                to={tile.to}
              >
                <div className="absolute inset-x-0 top-0 h-1 origin-left scale-x-0 bg-primary transition-transform duration-300 group-hover:scale-x-100" />
                <div className="absolute -right-12 top-0 h-28 w-28 rounded-full bg-primary/10 blur-2xl transition-transform duration-300 group-hover:scale-125" />
                <div className="relative flex h-full flex-col justify-between gap-12">
                  <div className="flex items-start justify-between">
                    <div className="rounded-lg bg-white p-3 shadow-sm transition-all duration-300 group-hover:-translate-y-0.5 group-hover:scale-110 group-hover:shadow-md w-fit">
                      <span className="font-headline text-xl font-black text-primary">{tile.icon}</span>
                    </div>
                    <span className="translate-y-1 text-lg text-outline-variant opacity-0 transition-all duration-300 group-hover:translate-y-0 group-hover:text-primary group-hover:opacity-100">↗</span>
                  </div>
                  <div>
                    <h3 className="text-xl font-bold transition-colors duration-300 group-hover:text-primary">{tile.title}</h3>
                    <p className={`mt-2 text-sm leading-6 ${tile.hasError ? "text-on-error-container" : "text-on-surface-variant"}`}>
                      {tile.detail}
                    </p>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      </main>
    </>
  );
}

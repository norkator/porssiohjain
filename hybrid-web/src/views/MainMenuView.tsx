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
import { fetchMe } from "@/lib/account";
import { fetchControlSavings, type ControlSavings } from "@/lib/dashboard";
import { fetchElectricityContracts } from "@/lib/electricity-contracts";
import { Link, useNavigate } from "react-router-dom";
import { formatKw } from "@/lib/account-stats";
import { useAccountStats } from "@/hooks/useAccountStats";
import { useControls } from "@/hooks/useControls";
import { useDevices } from "@/hooks/useDevices";
import { logoutNative, openNativeQrLoginScanner, showNativeToast } from "@/lib/android-bridge";
import {
  getMockDiscoveredBrandedDevices,
  mockClaimBrandedDevice,
  scanWifiAccessPoints,
  type MockDiscoveredBrandedDevice,
  type MockWifiAccessPoint
} from "@/lib/mock-branded-device-provisioning";
import { useI18n } from "@/lib/i18n";
import { clearBrowserSession, getSessionData } from "@/lib/session";
import { getThemePreference, setThemePreference, type ThemePreference } from "@/lib/theme";
import { useEffect, useState } from "react";

const SAVINGS_CHART_WIDTH = 720;
const SAVINGS_CHART_HEIGHT = 170;
const SAVINGS_CHART_PADDING_LEFT = 48;
const SAVINGS_CHART_PADDING_RIGHT = 18;
const SAVINGS_CHART_PADDING_TOP = 22;
const SAVINGS_CHART_PADDING_BOTTOM = 38;
const SAVINGS_CHART_Y_AXIS_STEPS = 4;
const SAVINGS_CHART_LABEL_SIZE = 10;

function formatSavingsAmount(value: number) {
  return new Intl.NumberFormat(undefined, {
    maximumFractionDigits: 0,
    minimumFractionDigits: 0,
    style: "currency",
    currency: "EUR"
  }).format(value);
}

function getSavingsChartPoint(value: number, index: number, pointCount: number, maxValue: number) {
  const innerWidth = SAVINGS_CHART_WIDTH - SAVINGS_CHART_PADDING_LEFT - SAVINGS_CHART_PADDING_RIGHT;
  const innerHeight = SAVINGS_CHART_HEIGHT - SAVINGS_CHART_PADDING_TOP - SAVINGS_CHART_PADDING_BOTTOM;
  const x = SAVINGS_CHART_PADDING_LEFT + (innerWidth * index) / Math.max(pointCount - 1, 1);
  const y = SAVINGS_CHART_PADDING_TOP + innerHeight - (value / Math.max(maxValue, 1)) * innerHeight;

  return { x, y };
}

function buildSavingsLinePath(values: number[], maxValue: number) {
  return values
    .map((value, index) => {
      const point = getSavingsChartPoint(value, index, values.length, maxValue);
      return `${index === 0 ? "M" : "L"} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`;
    })
    .join(" ");
}

function buildSavingsAreaPath(values: number[], maxValue: number) {
  if (values.length === 0) {
    return "";
  }

  const linePath = buildSavingsLinePath(values, maxValue);
  const startX = SAVINGS_CHART_PADDING_LEFT;
  const endX = SAVINGS_CHART_WIDTH - SAVINGS_CHART_PADDING_RIGHT;
  const baselineY = SAVINGS_CHART_HEIGHT - SAVINGS_CHART_PADDING_BOTTOM;

  return `${linePath} L ${endX.toFixed(2)} ${baselineY.toFixed(2)} L ${startX.toFixed(2)} ${baselineY.toFixed(2)} Z`;
}

export default function MainMenuView() {
  const navigate = useNavigate();
  const session = getSessionData();
  const { group, locale, t } = useI18n("mainMenu");
  const common = useI18n("common").t;
  const tileTitles = group("tileTitles");
  const [themePreference, setThemePreferenceState] = useState<ThemePreference>(() => getThemePreference());
  const [discoveredDevices, setDiscoveredDevices] = useState<MockDiscoveredBrandedDevice[]>(() => getMockDiscoveredBrandedDevices());
  const [isDemoAccount, setIsDemoAccount] = useState(false);
  const [isMockDiscoveryDialogOpen, setIsMockDiscoveryDialogOpen] = useState(false);
  const [selectedDiscoveredDevice, setSelectedDiscoveredDevice] = useState<MockDiscoveredBrandedDevice | null>(null);
  const [wifiAccessPoints, setWifiAccessPoints] = useState<MockWifiAccessPoint[]>([]);
  const [selectedWifiSsid, setSelectedWifiSsid] = useState("");
  const [wifiPassword, setWifiPassword] = useState("");
  const [mockDeviceName, setMockDeviceName] = useState("");
  const [mockProvisioningError, setMockProvisioningError] = useState<string | null>(null);
  const [wifiScanError, setWifiScanError] = useState<string | null>(null);
  const [isWifiScanning, setIsWifiScanning] = useState(false);
  const [isMockProvisioning, setIsMockProvisioning] = useState(false);
  const [sitesCount, setSitesCount] = useState<number | null>(null);
  const [contractsCount, setContractsCount] = useState<number | null>(null);
  const [monthlySavings, setMonthlySavings] = useState<ControlSavings[]>([]);
  const [savingsError, setSavingsError] = useState(false);
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
    totalProductionPeakKw,
    totalProductionKw
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

    fetchMe()
      .then((account) => {
        if (!active) return;
        setIsDemoAccount(account.demo);
      })
      .catch(() => {
        if (!active) return;
        setIsDemoAccount(false);
      });

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

  useEffect(() => {
    let active = true;
    const now = new Date();
    const monthRanges = Array.from({ length: 6 }, (_, index) => {
      const monthStart = new Date(now.getFullYear(), now.getMonth() - (5 - index), 1);
      const monthEnd = index === 5
        ? now
        : new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 1);

      return {
        from: monthStart.toISOString(),
        to: monthEnd.toISOString()
      };
    });

    Promise.all(monthRanges.map((range) => fetchControlSavings({ from: range.from, to: range.to })))
      .then((savings) => {
        if (!active) return;
        setMonthlySavings(savings);
        setSavingsError(false);
      })
      .catch(() => {
        if (!active) return;
        setSavingsError(true);
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
  const productionUtilizationRatio = totalProductionPeakKw > 0
    ? Math.min(totalProductionKw / totalProductionPeakKw, 1)
    : totalProductionKw > 0
      ? 1
      : 0;
  const productionBarWidth = `${productionUtilizationRatio * 100}%`;
  const consumptionLabel = isStatsLoading ? "--" : formatKw(totalConsumptionKw);
  const productionLabel = isStatsLoading ? "--" : formatKw(totalProductionKw, true);
  const productionPeakLabel = isStatsLoading ? "--" : formatKw(totalProductionPeakKw);
  const netPowerKw = totalProductionKw - totalConsumptionKw;
  const showAndroidAppLink = session.source !== "android";
  const currentMonthSavings = monthlySavings.length > 0 ? monthlySavings[monthlySavings.length - 1] : undefined;
  const hasSavingsData = monthlySavings.some((saving) => saving.scheduleEntryCount > 0 && saving.controlsWithEstimatedPowerCount > 0 && saving.estimatedUsageKwh > 0);
  const showSavingsEmptyState = !hasSavingsData;
  const savingsSummaryText = currentMonthSavings && currentMonthSavings.scheduleEntryCount > 0 && currentMonthSavings.controlsWithEstimatedPowerCount > 0
    ? t("summarySavings", { savings: currentMonthSavings.estimatedSavingsEur.toFixed(2) })
    : t("summarySavingsUnavailable");
  const monthlySavingsValues = monthlySavings.map((saving) => Math.max(saving.estimatedSavingsEur, 0));
  const maxMonthlySavings = Math.max(...monthlySavingsValues, 0);
  const savingsChartMaxValue = maxMonthlySavings > 0 ? maxMonthlySavings * 1.15 : 1;
  const savingsLinePath = buildSavingsLinePath(monthlySavingsValues, savingsChartMaxValue);
  const savingsAreaPath = buildSavingsAreaPath(monthlySavingsValues, savingsChartMaxValue);
  const savingsYAxisValues = Array.from({ length: SAVINGS_CHART_Y_AXIS_STEPS + 1 }, (_, index) =>
    savingsChartMaxValue - (savingsChartMaxValue * index) / SAVINGS_CHART_Y_AXIS_STEPS
  );
  const summaryText = (isLoading && isStatsLoading)
    ? t("summaryLoading")
    : (error && statsError)
      ? t("summaryUnavailable")
      : statsError
        ? t("summaryStatsUnavailable", { onlineCount, totalCount })
        : error
          ? t("summaryDevicesUnavailable", { consumptionKw: formatKw(totalConsumptionKw), productionKw: formatKw(totalProductionKw) })
          : netPowerKw >= 0
            ? t("summaryProductionAhead", { onlineCount, totalCount, netKw: formatKw(netPowerKw), consumptionKw: formatKw(totalConsumptionKw), savingsText: savingsSummaryText })
            : t("summaryConsumptionAhead", { onlineCount, totalCount, consumptionKw: formatKw(totalConsumptionKw), netKw: formatKw(Math.abs(netPowerKw)), savingsText: savingsSummaryText });
  const handleLogout = () => {
    if (session.source === "android") {
      logoutNative();
      return;
    }

    clearBrowserSession();
    window.location.hash = "#/login";
  };

  const handleThemeToggle = () => {
    const nextTheme: ThemePreference = themePreference === "dark" ? "light" : "dark";

    setThemePreference(nextTheme);
    setThemePreferenceState(nextTheme);
  };
  const handleSelectMockDevice = async (device: MockDiscoveredBrandedDevice) => {
    setSelectedDiscoveredDevice(device);
    setMockDeviceName(device.productModel);
    setSelectedWifiSsid("");
    setWifiAccessPoints([]);
    setWifiPassword("");
    setMockProvisioningError(null);
    setWifiScanError(null);
    setIsWifiScanning(true);

    try {
      const networks = await scanWifiAccessPoints(device.discoveryId);

      setWifiAccessPoints(networks);
      setSelectedWifiSsid(networks[0]?.ssid ?? "");
    } catch (error) {
      setWifiScanError(error instanceof Error ? error.message : t("mockWifiScanFailed"));
    } finally {
      setIsWifiScanning(false);
    }
  };
  const handleMockProvision = () => {
    if (!selectedDiscoveredDevice || isMockProvisioning) {
      return;
    }

    const trimmedDeviceName = mockDeviceName.trim();

    if (!trimmedDeviceName) {
      setMockProvisioningError(t("mockDeviceNameRequired"));
      return;
    }

    if (!selectedWifiSsid) {
      setMockProvisioningError(t("mockWifiRequired"));
      return;
    }

    if (wifiPassword.trim().length < 8) {
      setMockProvisioningError(t("mockWifiPasswordRequired"));
      return;
    }

    setIsMockProvisioning(true);
    setMockProvisioningError(null);

    window.setTimeout(() => {
      mockClaimBrandedDevice({
        device: selectedDiscoveredDevice,
        deviceName: trimmedDeviceName,
        ssid: selectedWifiSsid,
        wifiPassword
      })
        .then(() => {
          setDiscoveredDevices(getMockDiscoveredBrandedDevices());
          setSelectedDiscoveredDevice(null);
          setIsMockDiscoveryDialogOpen(false);
          showNativeToast(t("mockAddedToast", { deviceName: trimmedDeviceName }));
          navigate("/devices");
        })
        .catch((error) => {
          setMockProvisioningError(error instanceof Error ? error.message : t("mockProvisioningFailed"));
        })
        .finally(() => {
          setIsMockProvisioning(false);
        });
    }, 700);
  };
  const handleCloseMockDiscoveryDialog = () => {
    setIsMockDiscoveryDialogOpen(false);
    setSelectedDiscoveredDevice(null);
    setMockProvisioningError(null);
    setWifiScanError(null);
  };

  return (
    <>
      <PageHeader
        brand={t("brand")}
        translucent
        rightSlot={(
          <div className="flex flex-wrap items-center justify-end gap-2">
            <button
              className="secondary-action px-4 py-2 text-sm"
              onClick={handleLogout}
              type="button"
            >
              {t("logout")}
            </button>
          </div>
        )}
      />

      <main className="mx-auto max-w-7xl px-4 pb-16 pt-4 sm:px-6">
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
              <div className="dashboard-hero-card group relative overflow-hidden rounded-xl p-4 text-on-primary shadow-2xl transition-transform duration-300 hover:-translate-y-1 hover:shadow-[0_24px_48px_rgba(0,67,66,0.24)] sm:p-6 lg:p-8">
                <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-secondary-container opacity-10 blur-3xl transition-transform duration-500 group-hover:scale-125" />
                <div className="mb-10 flex items-start justify-between">
                  <div>
                    <p className="dashboard-hero-kicker mb-1 text-sm font-semibold uppercase tracking-wider">{t("totalConsumption")}</p>
                    <div className="flex items-baseline gap-2">
                      <span className="font-headline text-5xl font-extrabold">{consumptionLabel}</span>
                      <span className="dashboard-hero-unit text-xl font-medium">kW</span>
                    </div>
                  </div>
                  <span className="dashboard-hero-accent font-headline text-4xl font-black">⚡</span>
                </div>
                <div className="space-y-4">
                  <div className="flex items-center justify-between text-sm">
                    <span className="dashboard-hero-support">{t("solarProduction")}</span>
                    <span className="dashboard-hero-accent font-bold">{productionLabel} kW/{productionPeakLabel} kW</span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-primary/30">
                    <div
                      className="h-full origin-left bg-secondary-container transition-[width,transform] duration-700 group-hover:scale-x-105"
                      style={{ width: productionBarWidth }}
                    />
                  </div>
                  {statsError ? <p className="dashboard-hero-note text-xs">{t("statsSyncUnavailable")}</p> : null}
                </div>
              </div>
            </div>
          </div>
        </section>

        {isDemoAccount && discoveredDevices.length > 0 ? (
          <button
            aria-label={t("mockDiscoveryTitle")}
            className="fixed right-4 top-20 z-40 flex items-center gap-3 rounded-full border border-primary/40 bg-white px-4 py-3 text-primary shadow-2xl transition-transform hover:-translate-y-0.5 hover:border-primary active:scale-95 sm:right-6 sm:top-24"
            onClick={() => setIsMockDiscoveryDialogOpen(true)}
            type="button"
          >
            <span className="text-sm font-black">{t("mockDevicesFound")}</span>
            <span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-primary px-1.5 text-xs font-black text-on-primary">
              {discoveredDevices.length}
            </span>
          </button>
        ) : null}

        {isDemoAccount && discoveredDevices.length > 0 && isMockDiscoveryDialogOpen ? (
          <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 px-3 py-4 sm:items-center sm:px-6">
            <section className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-surface p-4 shadow-2xl sm:p-6">
              <div className="mb-6 flex items-start justify-between gap-4">
                <div>
                  <p className="metric-label mb-2">
                    {selectedDiscoveredDevice ? t("mockSetupEyebrow") : t("mockDiscoveryEyebrow")}
                  </p>
                  <h2 className="font-headline text-2xl font-black text-on-surface">
                    {selectedDiscoveredDevice ? selectedDiscoveredDevice.productModel : t("mockDiscoveryTitle")}
                  </h2>
                  <p className="mt-1 font-mono text-xs text-outline">
                    {selectedDiscoveredDevice
                      ? selectedDiscoveredDevice.serialNumber
                      : t("mockDiscoveryCount", { count: discoveredDevices.length })}
                  </p>
                </div>
                <button
                  className="secondary-action rounded-lg px-3 py-2 text-sm"
                  onClick={handleCloseMockDiscoveryDialog}
                  type="button"
                >
                  {common("close")}
                </button>
              </div>

              {!selectedDiscoveredDevice ? (
                <div className="grid grid-cols-1 gap-3">
                  {discoveredDevices.map((device) => (
                    <button
                      className="group rounded-lg border border-outline-variant/50 bg-surface-container-high p-4 text-left transition-all hover:-translate-y-0.5 hover:border-primary hover:bg-surface-container-highest"
                      key={device.discoveryId}
                      onClick={() => handleSelectMockDevice(device)}
                      type="button"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <h3 className="font-headline text-lg font-bold text-on-surface group-hover:text-primary">{device.productModel}</h3>
                          <p className="mt-1 font-mono text-xs text-outline">{device.serialNumber}</p>
                          <p className="mt-3 text-xs text-on-surface-variant">
                            {t("mockDiscoveryDetail", { channels: device.relayChannels })}
                          </p>
                        </div>
                        <span className="shrink-0 rounded bg-surface-container-lowest px-2 py-1 text-[10px] font-bold text-on-surface-variant">
                          {device.rssi} dBm
                        </span>
                      </div>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="space-y-5">
                  <div>
                    <label className="mb-2 block text-sm font-bold text-on-surface" htmlFor="mock-wifi-ssid">
                      {t("mockWifiNetwork")}
                    </label>
                    {isWifiScanning ? (
                      <p className="rounded-lg bg-surface-container-highest px-4 py-4 text-sm text-on-surface-variant">
                        {t("mockWifiScanning")}
                      </p>
                    ) : (
                      <select
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none focus:border-primary"
                        id="mock-wifi-ssid"
                        onChange={(event) => setSelectedWifiSsid(event.target.value)}
                        value={selectedWifiSsid}
                      >
                        {wifiAccessPoints.map((network) => (
                          <option key={`${network.mock ? "mock" : "real"}-${network.ssid}`} value={network.ssid}>
                            {network.ssid} ({network.security.toUpperCase()}, {network.rssi} dBm{network.mock ? `, ${t("mockWifiDevLabel")}` : ""})
                          </option>
                        ))}
                      </select>
                    )}
                    {wifiScanError ? (
                      <p className="mt-2 text-sm text-on-error-container">
                        {wifiScanError}
                      </p>
                    ) : null}
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-bold text-on-surface" htmlFor="mock-wifi-password">
                      {t("mockWifiPassword")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="mock-wifi-password"
                      onChange={(event) => setWifiPassword(event.target.value)}
                      placeholder={t("mockWifiPasswordPlaceholder")}
                      type="password"
                      value={wifiPassword}
                    />
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-bold text-on-surface" htmlFor="mock-device-name">
                      {t("mockDeviceName")}
                    </label>
                    <input
                      className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-4 py-4 text-on-surface outline-none placeholder:text-on-surface-variant/40 focus:border-primary"
                      id="mock-device-name"
                      onChange={(event) => setMockDeviceName(event.target.value)}
                      placeholder={t("mockDeviceNamePlaceholder")}
                      type="text"
                      value={mockDeviceName}
                    />
                  </div>

                  <div className="rounded-lg bg-surface-container-low p-4 text-sm text-on-surface-variant">
                    {t("mockClaimNotice", { claimCode: selectedDiscoveredDevice.claimCode })}
                  </div>

                  {mockProvisioningError ? (
                    <p className="rounded-lg border border-error-container bg-error-container/50 p-3 text-sm text-on-error-container">
                      {mockProvisioningError}
                    </p>
                  ) : null}

                  <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
                    <button
                      className="secondary-action justify-center px-4 py-3"
                      onClick={() => setSelectedDiscoveredDevice(null)}
                      type="button"
                    >
                      {common("back")}
                    </button>
                    <button
                      className="primary-action justify-center px-4 py-3 disabled:cursor-not-allowed disabled:opacity-60"
                      disabled={isMockProvisioning}
                      onClick={handleMockProvision}
                      type="button"
                    >
                      {isMockProvisioning ? t("mockAddingDevice") : t("mockAddDevice")}
                    </button>
                  </div>
                </div>
              )}
            </section>
          </div>
        ) : null}

        <section className="mb-12">
          <h2 className="mb-8 flex items-center gap-3 text-2xl font-bold">
            <span className="h-1 w-8 rounded-full bg-primary" />
            {t("controlCenter")}
          </h2>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-5">
            {tiles.map((tile) => (
              <Link
                key={tile.key}
                className="group relative overflow-hidden rounded-xl bg-surface-container-low p-4 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft active:scale-[0.98] sm:p-6"
                to={tile.to}
              >
                <div className="absolute inset-x-0 top-0 h-1 origin-left scale-x-0 bg-primary transition-transform duration-300 group-hover:scale-x-100" />
                <div className="flex h-full flex-col justify-between gap-12">
                  <div className="flex items-start justify-between">
                    <div className="rounded-lg bg-surface-container-lowest p-3 shadow-sm transition-all duration-300 group-hover:-translate-y-0.5 group-hover:scale-110 group-hover:shadow-md">
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

        {!savingsError ? (
          <section className="mb-12">
            <div className="app-card p-4 sm:p-6">
              <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-primary">{t("savingsEyebrow")}</p>
                  <h2 className="font-headline text-3xl font-black tracking-tight text-on-surface">{t("savingsTitle")}</h2>
                </div>
                {currentMonthSavings ? (
                  <div className="text-left sm:text-right">
                    <p className="text-3xl font-black text-on-surface">{currentMonthSavings.estimatedSavingsEur.toFixed(2)} €</p>
                    <p className="text-xs text-on-surface-variant">{t("savingsThisMonth")}</p>
                  </div>
                ) : null}
              </div>

              {showSavingsEmptyState ? (
                <p className="rounded-lg bg-surface-container-high px-4 py-3 text-sm text-on-surface-variant">
                  {t("savingsEmpty")}
                </p>
              ) : (
                <div
                  className="relative rounded-2xl p-2 sm:p-3"
                  style={{ background: "linear-gradient(180deg, rgb(var(--chart-panel-start)), rgb(var(--chart-panel-end)))" }}
                >
                  <div className="-mx-1 overflow-x-auto px-1 pb-2 sm:mx-0 sm:px-0">
                    <svg
                      aria-label={t("savingsTitle")}
                      className="h-auto min-w-[34rem] aspect-[72/17] w-[34rem] sm:min-w-0 sm:w-full"
                      role="img"
                      viewBox={`0 0 ${SAVINGS_CHART_WIDTH} ${SAVINGS_CHART_HEIGHT}`}
                    >
                      <defs>
                        <linearGradient id="monthly-savings-fill" x1="0" x2="0" y1="0" y2="1">
                          <stop offset="0%" stopColor="rgb(var(--color-secondary) / 0.3)" />
                          <stop offset="100%" stopColor="rgb(var(--color-secondary) / 0.04)" />
                        </linearGradient>
                      </defs>

                      <rect
                        fill="rgb(var(--chart-plot-background))"
                        height={SAVINGS_CHART_HEIGHT - SAVINGS_CHART_PADDING_TOP - SAVINGS_CHART_PADDING_BOTTOM}
                        rx="18"
                        width={SAVINGS_CHART_WIDTH - SAVINGS_CHART_PADDING_LEFT - SAVINGS_CHART_PADDING_RIGHT}
                        x={SAVINGS_CHART_PADDING_LEFT}
                        y={SAVINGS_CHART_PADDING_TOP}
                      />

                      {savingsYAxisValues.map((value, index) => {
                        const y = SAVINGS_CHART_PADDING_TOP
                          + ((SAVINGS_CHART_HEIGHT - SAVINGS_CHART_PADDING_TOP - SAVINGS_CHART_PADDING_BOTTOM) * index) / SAVINGS_CHART_Y_AXIS_STEPS;

                        return (
                          <g key={value}>
                            <line
                              stroke="rgb(var(--color-outline-variant) / 0.6)"
                              strokeDasharray="6 8"
                              strokeWidth="1"
                              x1={SAVINGS_CHART_PADDING_LEFT}
                              x2={SAVINGS_CHART_WIDTH - SAVINGS_CHART_PADDING_RIGHT}
                              y1={y}
                              y2={y}
                            />
                            <text fill="rgb(var(--color-on-surface-variant))" fontSize={SAVINGS_CHART_LABEL_SIZE} textAnchor="end" x={SAVINGS_CHART_PADDING_LEFT - 8} y={y + 3}>
                              {formatSavingsAmount(value)}
                            </text>
                          </g>
                        );
                      })}

                      {monthlySavings.map((saving, index) => {
                        const point = getSavingsChartPoint(monthlySavingsValues[index], index, monthlySavingsValues.length, savingsChartMaxValue);
                        const month = new Date(saving.from).toLocaleDateString(locale, { month: "short" });

                        return (
                          <text
                            fill="rgb(var(--color-on-surface-variant))"
                            fontSize={SAVINGS_CHART_LABEL_SIZE}
                            key={saving.from}
                            textAnchor={index === monthlySavings.length - 1 ? "end" : index === 0 ? "start" : "middle"}
                            x={point.x}
                            y={SAVINGS_CHART_HEIGHT - 14}
                          >
                            {month}
                          </text>
                        );
                      })}

                      <path d={savingsAreaPath} fill="url(#monthly-savings-fill)" />
                      <path d={savingsLinePath} fill="none" stroke="rgb(var(--color-primary))" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" />

                      {monthlySavings.map((saving, index) => {
                        const positiveSavings = monthlySavingsValues[index];
                        const point = getSavingsChartPoint(positiveSavings, index, monthlySavingsValues.length, savingsChartMaxValue);

                        return (
                          <g key={`${saving.from}-point`}>
                            <circle
                              cx={point.x}
                              cy={point.y}
                              fill="rgb(var(--color-secondary-container))"
                              r="4"
                              stroke="rgb(var(--color-primary))"
                              strokeWidth="2"
                            />
                            <title>{`${positiveSavings.toFixed(2)} €`}</title>
                          </g>
                        );
                      })}
                    </svg>
                  </div>
                </div>
              )}

              <p className="mt-5 text-xs leading-5 text-on-surface-variant">
                {t("savingsBasis", {
                  controls: currentMonthSavings?.controlsWithEstimatedPowerCount ?? 0,
                  total: currentMonthSavings?.controlCount ?? 0
                })}
              </p>
            </div>
          </section>
        ) : null}

        <section className="mb-12">
          <h2 className="mb-8 flex items-center gap-3 text-2xl font-bold">
            <span className="h-1 w-8 rounded-full bg-primary" />
            {t("siteOwn")}
          </h2>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
            {siteOwnTiles.map((tile) => (
              <Link
                key={tile.key}
                className="group relative overflow-hidden rounded-xl bg-surface-container-low p-4 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft active:scale-[0.98] sm:p-6"
                to={tile.to}
              >
                <div className="absolute inset-x-0 top-0 h-1 origin-left scale-x-0 bg-primary transition-transform duration-300 group-hover:scale-x-100" />
                <div className="absolute -right-12 top-0 h-28 w-28 rounded-full bg-primary/10 blur-2xl transition-transform duration-300 group-hover:scale-125" />
                <div className="relative flex h-full flex-col justify-between gap-12">
                  <div className="flex items-start justify-between">
                    <div className="w-fit rounded-lg bg-surface-container-lowest p-3 shadow-sm transition-all duration-300 group-hover:-translate-y-0.5 group-hover:scale-110 group-hover:shadow-md">
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

        <section className="flex justify-center pb-4 pt-2">
          <div className="flex flex-wrap items-center justify-center gap-3">
            {session.source === "android" || import.meta.env.DEV ? (
              <button
                className="glass-panel inline-flex items-center gap-3 rounded-full border border-outline-variant/60 px-4 py-3 text-sm font-semibold text-on-surface shadow-soft transition-transform hover:-translate-y-0.5 active:scale-95"
                onClick={openNativeQrLoginScanner}
                type="button"
              >
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-container-highest text-lg">
                  ⛶
                </span>
                <span>{t("scanQrLogin")}</span>
              </button>
            ) : null}
            <button
              aria-label={t("themeToggle")}
              className="glass-panel inline-flex items-center gap-3 rounded-full border border-outline-variant/60 px-4 py-3 text-sm font-semibold text-on-surface shadow-soft transition-transform hover:-translate-y-0.5 active:scale-95"
              onClick={handleThemeToggle}
              type="button"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-container-highest text-lg">
                {themePreference === "dark" ? "☀" : "☾"}
              </span>
              <span>{themePreference === "dark" ? t("switchToLight") : t("switchToDark")}</span>
            </button>
          </div>
        </section>

        {showAndroidAppLink ? (
          <section className="pb-4 pt-6">
            <div className="app-card flex flex-col items-center justify-between gap-4 p-4 text-center sm:p-5 lg:flex-row lg:text-left">
              <div className="max-w-xl">
                <p className="metric-label mb-2">Android App</p>
                <h2 className="font-headline text-xl font-black text-on-surface">Energy Controller on Google Play</h2>
                <p className="mt-1 text-sm leading-6 text-on-surface-variant">
                  Install the mobile app for quick access to your energy controls and live dashboard.
                </p>
              </div>
              <a
                className="transition-transform duration-300 hover:-translate-y-0.5"
                href="https://play.google.com/store/apps/details?id=com.nitramite.energycontroller"
                rel="noreferrer"
                target="_blank"
              >
                <img
                  alt="Get it on Google Play"
                  className="h-auto w-[160px] max-w-full"
                  src="/get_it_on_google_play_badge.svg"
                />
              </a>
            </div>
          </section>
        ) : null}
      </main>
    </>
  );
}

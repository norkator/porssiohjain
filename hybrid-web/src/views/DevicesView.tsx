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

import DeviceCard from "@/components/DeviceCard";
import HeatPumpStateDialog from "@/components/HeatPumpStateDialog";
import PageHeader from "@/components/PageHeader";
import { useDevices } from "@/hooks/useDevices";
import { fetchHeatPumpState, formatAcType, formatDeviceLastCommunication, formatDeviceType, getDeviceAccent, getDeviceConnectionState, sendHeatPumpCommand, sendMqttRelayDebugCommand, type AcType, type ApiDevice } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";
import { useState } from "react";
import { Link } from "react-router-dom";

export default function DevicesView() {
  const { t } = useI18n("devices");
  const common = useI18n("common").t;
  const { devices, error, isLoading, latestCommunication, onlineCount, totalCount } = useDevices();
  const [relayDialogDevice, setRelayDialogDevice] = useState<ApiDevice | null>(null);
  const [relayStates, setRelayStates] = useState<boolean[]>([false, false, false, false]);
  const [isSendingRelayChannel, setIsSendingRelayChannel] = useState<number | null>(null);
  const [relayError, setRelayError] = useState<string | null>(null);
  const [heatPumpDialogDevice, setHeatPumpDialogDevice] = useState<ApiDevice | null>(null);
  const [heatPumpDialogValue, setHeatPumpDialogValue] = useState("");
  const [heatPumpCurrentState, setHeatPumpCurrentState] = useState<string | null>(null);
  const [heatPumpLastPolledState, setHeatPumpLastPolledState] = useState<string | null>(null);
  const [heatPumpDialogAcType, setHeatPumpDialogAcType] = useState<AcType>("NONE");
  const [isLoadingHeatPumpState, setIsLoadingHeatPumpState] = useState(false);
  const [isSendingHeatPumpCommand, setIsSendingHeatPumpCommand] = useState(false);
  const [heatPumpError, setHeatPumpError] = useState<string | null>(null);

  const handleRelayToggle = async (channel: number) => {
    if (!relayDialogDevice) {
      return;
    }

    const nextState = !relayStates[channel];
    setRelayError(null);
    setIsSendingRelayChannel(channel);

    try {
      await sendMqttRelayDebugCommand(relayDialogDevice.id, channel, nextState);
      setRelayStates((current) => current.map((state, index) => (index === channel ? nextState : state)));
    } catch (error) {
      setRelayError(error instanceof Error ? error.message : t("failedRelayCommand"));
    } finally {
      setIsSendingRelayChannel((current) => (current === channel ? null : current));
    }
  };

  const loadHeatPumpStateForDialog = async (device: ApiDevice) => {
    setIsLoadingHeatPumpState(true);
    setHeatPumpError(null);
    try {
      const response = await fetchHeatPumpState(device.id);
      const bestState = response.currentState || response.lastPolledState || "";
      setHeatPumpDialogAcType(response.acType);
      setHeatPumpCurrentState(response.currentState);
      setHeatPumpLastPolledState(response.lastPolledState);
      setHeatPumpDialogValue(bestState);
    } catch (error) {
      setHeatPumpError(error instanceof Error ? error.message : t("failedLoadHeatPumpState"));
      setHeatPumpCurrentState(null);
      setHeatPumpLastPolledState(null);
      setHeatPumpDialogAcType((device.acType as AcType | null) ?? "NONE");
      setHeatPumpDialogValue("");
    } finally {
      setIsLoadingHeatPumpState(false);
    }
  };

  const handleOpenHeatPumpDialog = async (device: ApiDevice) => {
    setHeatPumpDialogDevice(device);
    setHeatPumpDialogAcType((device.acType as AcType | null) ?? "NONE");
    setHeatPumpDialogValue("");
    setHeatPumpCurrentState(null);
    setHeatPumpLastPolledState(null);
    setHeatPumpError(null);
    await loadHeatPumpStateForDialog(device);
  };

  const handleSendHeatPumpCommand = async (value: string) => {
    if (!heatPumpDialogDevice) {
      return;
    }

    setIsSendingHeatPumpCommand(true);
    setHeatPumpError(null);
    try {
      await sendHeatPumpCommand(heatPumpDialogDevice.id, value);
      await loadHeatPumpStateForDialog(heatPumpDialogDevice);
    } catch (error) {
      setHeatPumpError(error instanceof Error ? error.message : t("failedSendHeatPumpCommand"));
    } finally {
      setIsSendingHeatPumpCommand(false);
    }
  };

  return (
    <>
      <PageHeader
        rightSlot={(
          <Link className="secondary-action px-4 py-2 text-sm" to="/menu">
            {common("menu")}
          </Link>
        )}
        translucent
      />

      <main className="app-page pt-4 sm:pt-12">
        <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
              {t("title")}
            </h1>
            <p className="max-w-lg text-lg text-on-surface-variant">
              {t("description")}
            </p>
          </div>

          <Link className="primary-action transition-all duration-300 hover:-translate-y-0.5 hover:shadow-soft" to="/devices/add/type">
            <span>+</span>
            {t("addNewDevice")}
          </Link>
        </section>

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {isLoading ? (
            <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingDevices")}</div>
          ) : null}

          {!isLoading && error ? (
            <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
              {t("failedToLoad", { error })}
            </div>
          ) : null}

          {!isLoading && !error
            ? devices.map((device) => {
                const connection = getDeviceConnectionState(device);

                return (
                  <DeviceCard
                    key={device.id}
                    accent={getDeviceAccent(device)}
                    actions={[
                      ...(device.deviceType === "HEAT_PUMP" ? [{
                        label: t("test"),
                        onClick: () => {
                          void handleOpenHeatPumpDialog(device);
                        }
                      }] : []),
                      ...(device.deviceType === "STANDARD" && device.mqttOnline ? [{
                        label: t("relays"),
                        onClick: () => {
                          setRelayDialogDevice(device);
                          setRelayStates([false, false, false, false]);
                          setRelayError(null);
                        }
                      }] : [])
                    ]}
                    detailLabel={t("lastSeen")}
                    detailValue={formatDeviceLastCommunication(device.lastCommunication)}
                    manageTo={`/devices/${device.id}`}
                    channelStatus={device.deviceType === "STANDARD"
                      ? {
                          label: device.hasActiveChannels ? t("on") : t("off"),
                          tone: device.hasActiveChannels ? "active" : "inactive"
                        }
                      : undefined}
                    status={connection.label}
                    statusTone={connection.tone}
                    subtitle={t("uuid", { uuid: device.uuid })}
                    title={device.deviceName}
                    type={formatDeviceType(device.deviceType)}
                  />
                );
              })
            : null}

          <Link
            className="group flex flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low p-6 text-center transition-all duration-300 hover:-translate-y-1 hover:border-primary hover:bg-surface-container-high hover:shadow-soft"
            to="/devices/add/type"
          >
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-container-highest font-headline text-xl font-black text-primary transition-all duration-300 group-hover:scale-110 group-hover:bg-white">
              +
            </div>
            <div>
              <h3 className="font-headline text-lg font-bold text-on-surface">{t("provisionUnit")}</h3>
              <p className="px-8 text-xs text-on-surface-variant">{t("provisionDescription")}</p>
            </div>
          </Link>
        </section>

        <section className="mt-20 grid grid-cols-1 gap-8 items-center md:grid-cols-12">
          <div className="group relative overflow-hidden rounded-3xl bg-primary-container p-6 shadow-2xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_24px_48px_rgba(0,67,66,0.22)] md:col-span-4">
            <div className="absolute inset-0 bg-gradient-to-t from-primary/60 to-transparent" />
            <div className="absolute -right-10 -top-10 h-36 w-36 rounded-full bg-secondary-container/20 blur-3xl transition-transform duration-500 group-hover:scale-125" />
            <div className="relative text-white">
              <p className="text-xs font-semibold uppercase tracking-widest opacity-80">{t("devicesOnline")}</p>
              <p className="font-headline text-4xl font-black">{onlineCount} / {totalCount}</p>
            </div>
          </div>

          <div className="flex flex-col gap-6 md:col-span-8">
            <h3 className="font-headline text-3xl font-bold text-primary">{t("summaryTitle")}</h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">{t("registeredDevices")}</p>
                <p className="text-sm text-on-surface-variant">
                  {isLoading ? t("syncingInventory") : t("connectedDevices", { count: totalCount })}
                </p>
              </div>
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">{t("lastSync")}</p>
                <p className="text-sm text-on-surface-variant">
                  {latestCommunication ? t("latestContact", { time: formatDeviceLastCommunication(latestCommunication) }) : t("noCommunication")}
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      <Link
        className="signature-gradient fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full text-3xl text-on-primary shadow-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_20px_40px_rgba(0,67,66,0.22)] active:scale-90 md:hidden"
        to="/devices/add/type"
      >
        +
      </Link>

      {relayDialogDevice ? (
        <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
          <div className="w-full max-w-xl rounded-xl bg-surface-container-lowest p-6 shadow-2xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <p className="metric-label mb-2">{t("relayDebug")}</p>
                <h2 className="font-headline text-2xl font-bold text-primary">{relayDialogDevice.deviceName}</h2>
                <p className="mt-2 text-sm text-on-surface-variant">
                  {t("relayDescription")}
                </p>
              </div>
              <button className="secondary-action px-3 py-2 text-sm" onClick={() => setRelayDialogDevice(null)} type="button">
                {common("close")}
              </button>
            </div>

            <div className="space-y-3">
              {relayStates.map((isOn, channel) => (
                <div className="flex items-center justify-between rounded-xl bg-surface-container p-4" key={channel}>
                  <div>
                    <p className="font-headline font-bold text-on-surface">{t("relayTitle", { channel })}</p>
                    <p className="text-sm text-on-surface-variant">{t("relayCommandDescription")}</p>
                  </div>
                  <button
                    className={isOn ? "rounded-lg bg-primary px-4 py-2 text-sm font-bold text-on-primary disabled:opacity-60" : "rounded-lg bg-error-container px-4 py-2 text-sm font-bold text-on-error-container disabled:opacity-60"}
                    disabled={isSendingRelayChannel === channel}
                    onClick={() => handleRelayToggle(channel)}
                    type="button"
                  >
                    {isSendingRelayChannel === channel ? t("sending") : isOn ? t("on") : t("off")}
                  </button>
                </div>
              ))}
            </div>

            {relayError ? (
              <div className="mt-4 rounded-xl border border-error-container bg-error-container/50 p-4 text-sm text-on-error-container">
                {relayError}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

      <HeatPumpStateDialog
        acType={heatPumpDialogAcType}
        currentState={heatPumpCurrentState}
        errorMessage={heatPumpError}
        formatAcType={formatAcType}
        isLoading={isLoadingHeatPumpState}
        isOpen={Boolean(heatPumpDialogDevice)}
        isSending={isSendingHeatPumpCommand}
        labels={{
          acType: t("acType"),
          auto: t("auto"),
          cancel: common("cancel"),
          close: common("close"),
          cool: t("cool"),
          dry: t("dry"),
          fanOnly: t("fanOnly"),
          fanSpeed: t("fanSpeed"),
          heat: t("heat"),
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
          selectCommandState: heatPumpDialogDevice?.deviceName ?? t("selectCommandState"),
          sendState: t("sendState"),
          sendingState: t("sending"),
          targetTemperature: t("targetTemperature"),
          useCurrent: t("useCurrent"),
          useLastPolled: t("useLastPolled")
        }}
        lastPolledState={heatPumpLastPolledState}
        onClose={() => {
          if (isSendingHeatPumpCommand) {
            return;
          }
          setHeatPumpDialogDevice(null);
          setHeatPumpError(null);
        }}
        onRefresh={() => heatPumpDialogDevice ? loadHeatPumpStateForDialog(heatPumpDialogDevice) : Promise.resolve()}
        onSave={(value) => setHeatPumpDialogValue(value)}
        onSend={(value) => {
          void handleSendHeatPumpCommand(value);
        }}
        onStateChange={setHeatPumpDialogValue}
        stateValue={heatPumpDialogValue}
      />
    </>
  );
}

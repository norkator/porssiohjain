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
import PageHeader from "@/components/PageHeader";
import { useDevices } from "@/hooks/useDevices";
import { formatDeviceLastCommunication, formatDeviceType, getDeviceAccent, getDeviceConnectionState, sendMqttRelayDebugCommand, type ApiDevice } from "@/lib/devices";
import { useState } from "react";
import { Link } from "react-router-dom";

export default function DevicesView() {
  const { devices, error, isLoading, latestCommunication, onlineCount, totalCount } = useDevices();
  const [relayDialogDevice, setRelayDialogDevice] = useState<ApiDevice | null>(null);
  const [relayStates, setRelayStates] = useState<boolean[]>([false, false, false, false]);
  const [isSendingRelayChannel, setIsSendingRelayChannel] = useState<number | null>(null);
  const [relayError, setRelayError] = useState<string | null>(null);

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
      setRelayError(error instanceof Error ? error.message : "Failed to send relay command");
    } finally {
      setIsSendingRelayChannel((current) => (current === channel ? null : current));
    }
  };

  return (
    <>
      <PageHeader
        rightSlot={(
          <Link className="secondary-action px-4 py-2 text-sm" to="/menu">
            Menu
          </Link>
        )}
        translucent
      />

      <main className="app-page pt-4 sm:pt-12">
        <section className="mb-12 flex flex-col gap-8 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h1 className="mb-4 font-headline text-4xl font-extrabold tracking-tight text-primary md:text-5xl">
              Your Infrastructure
            </h1>
            <p className="max-w-lg text-lg text-on-surface-variant">
              Manage and monitor your precision energy grid components. Real-time status for every connected unit across your network.
            </p>
          </div>

          <Link className="primary-action transition-all duration-300 hover:-translate-y-0.5 hover:shadow-soft" to="/devices/add/type">
            <span>+</span>
            Add New Device
          </Link>
        </section>

        <section className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {isLoading ? (
            <div className="app-card p-6 text-sm text-on-surface-variant">Loading devices...</div>
          ) : null}

          {!isLoading && error ? (
            <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
              Failed to load devices. {error}
            </div>
          ) : null}

          {!isLoading && !error
            ? devices.map((device) => {
                const connection = getDeviceConnectionState(device);

                return (
                  <DeviceCard
                  key={device.id}
                    accent={getDeviceAccent(device)}
                    detailLabel="Last Seen"
                    detailValue={formatDeviceLastCommunication(device.lastCommunication)}
                    extraActionLabel={device.deviceType === "STANDARD" && device.mqttOnline ? "Relays" : undefined}
                    manageTo={`/devices/${device.id}`}
                    onExtraAction={device.deviceType === "STANDARD" && device.mqttOnline ? () => {
                      setRelayDialogDevice(device);
                      setRelayStates([false, false, false, false]);
                      setRelayError(null);
                    } : undefined}
                    status={connection.label}
                    statusTone={connection.tone}
                    subtitle={`UUID: ${device.uuid}`}
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
              <h3 className="font-headline text-lg font-bold text-on-surface">Provision Unit</h3>
              <p className="px-8 text-xs text-on-surface-variant">Quickly add a new device to your energy ecosystem</p>
            </div>
          </Link>
        </section>

        <section className="mt-20 grid grid-cols-1 gap-8 items-center md:grid-cols-12">
          <div className="group relative overflow-hidden rounded-3xl bg-primary-container p-6 shadow-2xl transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_24px_48px_rgba(0,67,66,0.22)] md:col-span-4">
            <div className="absolute inset-0 bg-gradient-to-t from-primary/60 to-transparent" />
            <div className="absolute -right-10 -top-10 h-36 w-36 rounded-full bg-secondary-container/20 blur-3xl transition-transform duration-500 group-hover:scale-125" />
            <div className="relative text-white">
              <p className="text-xs font-semibold uppercase tracking-widest opacity-80">Devices Online</p>
              <p className="font-headline text-4xl font-black">{onlineCount} / {totalCount}</p>
            </div>
          </div>

          <div className="flex flex-col gap-6 md:col-span-8">
            <h3 className="font-headline text-3xl font-bold text-primary">Precise Control, Modular Design.</h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">Registered Devices</p>
                <p className="text-sm text-on-surface-variant">
                  {isLoading ? "Syncing account inventory..." : `${totalCount} devices connected to this account.`}
                </p>
              </div>
              <div className="rounded-2xl bg-surface-container p-6 transition-all duration-300 hover:-translate-y-0.5 hover:bg-surface-container-high">
                <p className="font-bold">Last Sync</p>
                <p className="text-sm text-on-surface-variant">
                  {latestCommunication ? `Latest device contact at ${formatDeviceLastCommunication(latestCommunication)}.` : "No device communication reported yet."}
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
                <p className="metric-label mb-2">MQTT Relay Debug</p>
                <h2 className="font-headline text-2xl font-bold text-primary">{relayDialogDevice.deviceName}</h2>
                <p className="mt-2 text-sm text-on-surface-variant">
                  Toggle relay outputs in real time for this MQTT-connected standard device.
                </p>
              </div>
              <button className="secondary-action px-3 py-2 text-sm" onClick={() => setRelayDialogDevice(null)} type="button">
                Close
              </button>
            </div>

            <div className="space-y-3">
              {relayStates.map((isOn, channel) => (
                <div className="flex items-center justify-between rounded-xl bg-surface-container p-4" key={channel}>
                  <div>
                    <p className="font-headline font-bold text-on-surface">Relay {channel}</p>
                    <p className="text-sm text-on-surface-variant">Send immediate MQTT switch command</p>
                  </div>
                  <button
                    className={isOn ? "rounded-lg bg-primary px-4 py-2 text-sm font-bold text-on-primary disabled:opacity-60" : "rounded-lg bg-error-container px-4 py-2 text-sm font-bold text-on-error-container disabled:opacity-60"}
                    disabled={isSendingRelayChannel === channel}
                    onClick={() => handleRelayToggle(channel)}
                    type="button"
                  >
                    {isSendingRelayChannel === channel ? "Sending..." : isOn ? "On" : "Off"}
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
    </>
  );
}

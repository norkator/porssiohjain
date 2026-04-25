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
import { showNativeToast } from "@/lib/android-bridge";
import ProgressHeader from "@/components/ProgressHeader";
import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import {
  getDeviceTypeOption,
  readAddDeviceDraft,
  writeProvisionedDeviceDraft
} from "@/lib/add-device-flow";
import { createDevice } from "@/lib/device-create";

export default function AddDeviceReviewView() {
  const navigate = useNavigate();
  const draft = readAddDeviceDraft();
  const deviceType = getDeviceTypeOption(draft.deviceTypeId);
  const isHeatPump = deviceType?.id === "toshiba-heat-pump" || deviceType?.id === "mitsubishi-heat-pump";
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  if (!deviceType) {
    return <Navigate replace to="/devices/add/type" />;
  }

  const handleCompleteSetup = async () => {
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const response = await createDevice({
        deviceName: draft.deviceName,
        deviceTypeId: deviceType.id,
        timezone: draft.timezone,
        hpName: draft.hpName,
        acUsername: draft.acUsername,
        acPassword: draft.acPassword,
        acDeviceId: draft.acDeviceId,
        buildingId: draft.acBuildingId,
        acDeviceUniqueId: draft.acDeviceUniqueId
      });

      writeProvisionedDeviceDraft({
        id: response.id,
        uuid: response.uuid,
        mqttPassword: response.mqttPassword,
        mqttUsername: response.mqttUsername
      });
      showNativeToast("Device provisioned successfully");
      navigate("/devices/add/integration");
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Failed to provision device");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader title="Add Device" compact />

      <main className="app-page pb-8 pt-4 sm:py-8">
        <section className="mb-10">
          <ProgressHeader label="Provision Device" step={3} total={4} />
        </section>

        <div className="grid gap-12 items-start lg:grid-cols-12">
          <div className="space-y-10 lg:col-span-7">
            <section>
              <h2 className="mb-4 font-headline text-3xl font-extrabold leading-tight text-primary md:text-5xl">
                Provision device in Energy Controller
              </h2>
              <p className="max-w-xl text-lg text-on-surface-variant">
                Confirm the selected device details and provision the device in your Energy Controller service.
              </p>
            </section>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-xl bg-surface-container-low p-6">
                <p className="metric-label mb-2">Device Type</p>
                <p className="font-headline text-xl font-bold text-on-surface">{deviceType.title}</p>
              </div>
              <div className="rounded-xl bg-surface-container-low p-6">
                <p className="metric-label mb-2">Device Name</p>
                <p className="font-headline text-xl font-bold text-on-surface">{draft.deviceName}</p>
              </div>
              <div className="rounded-xl bg-surface-container-low p-6 sm:col-span-2">
                <p className="metric-label mb-2">Timezone</p>
                <p className="font-headline text-xl font-bold text-on-surface">{draft.timezone}</p>
              </div>
              {isHeatPump ? (
                <>
                  <div className="rounded-xl bg-surface-container-low p-6">
                    <p className="metric-label mb-2">Heat Pump Device Name</p>
                    <p className="font-headline text-xl font-bold text-on-surface">{draft.hpName}</p>
                  </div>
                  <div className="rounded-xl bg-surface-container-low p-6">
                    <p className="metric-label mb-2">Selected AC Device</p>
                    <p className="font-headline text-lg font-bold text-on-surface">{draft.acDeviceLabel}</p>
                    <div className="mt-3 space-y-1 text-sm text-on-surface-variant">
                      <p>Device ID: {draft.acDeviceId}</p>
                      {draft.acBuildingId ? (
                        <p>Building ID: {draft.acBuildingId}</p>
                      ) : null}
                      {draft.acDeviceUniqueId ? (
                        <p>Unique ID: {draft.acDeviceUniqueId}</p>
                      ) : null}
                    </div>
                  </div>
                </>
              ) : null}
            </div>

            <div className="pt-6">
              <button
                className="primary-action w-full justify-center disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isSubmitting}
                onClick={handleCompleteSetup}
                type="button"
              >
                {isSubmitting ? "Provisioning..." : "Provision Device"}
              </button>
              <Link className="mt-4 block w-full py-3 text-center text-sm font-bold text-primary/60 transition-colors hover:text-primary" to="/devices/add/configure">
                Back
              </Link>
            </div>

            {submitError ? (
              <div className="rounded-xl border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
                {submitError}
              </div>
            ) : null}
          </div>
        </div>
      </main>
    </>
  );
}

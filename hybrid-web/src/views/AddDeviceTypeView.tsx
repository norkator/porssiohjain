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
import ProgressHeader from "@/components/ProgressHeader";
import StepOptionCard from "@/components/StepOptionCard";
import { Link, useNavigate } from "react-router-dom";
import {
  clearProvisionedDeviceDraft,
  DEVICE_TYPE_OPTIONS,
  getCurrentTimezone,
  updateAddDeviceDraft
} from "@/lib/add-device-flow";

export default function AddDeviceTypeView() {
  const navigate = useNavigate();

  const handleSelectDeviceType = (deviceTypeId: string) => {
    clearProvisionedDeviceDraft();
    updateAddDeviceDraft({
      deviceTypeId,
      deviceName: "",
      timezone: getCurrentTimezone(),
      hpName: "",
      acUsername: "",
      acPassword: "",
      acDeviceId: "",
      acDeviceLabel: "",
      acBuildingId: "",
      acDeviceUniqueId: "",
      uuid: ""
    });
    navigate("/devices/add/configure");
  };

  return (
    <>
      <PageHeader title="Add Device" compact />

      <main className="app-page pb-8 pt-4 sm:py-8">
        <ProgressHeader label="Select Device Type" step={1} total={4} />

        <section className="grid w-full grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {DEVICE_TYPE_OPTIONS.map((option) => (
            <div key={option.id} className="h-full w-full" onClick={() => handleSelectDeviceType(option.id)} onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                handleSelectDeviceType(option.id);
              }
            }} role="link" tabIndex={0}>
              <StepOptionCard {...option} />
            </div>
          ))}

          <div className="relative flex h-full w-full flex-col justify-between overflow-hidden rounded-xl border-2 border-dashed border-outline-variant/30 bg-surface-container p-8">
            <div className="z-10">
              <h4 className="mb-2 font-headline text-lg font-bold">Don&apos;t see your device?</h4>
              <p className="mb-4 text-sm text-on-surface-variant">
                Our engineering team is constantly adding new hardware drivers.
              </p>
              <a
                className="flex items-center gap-2 text-sm font-bold text-primary transition-all hover:gap-4"
                href="mailto:nitramite@outlook.com"
              >
                Request Integration
              </a>
            </div>
            <div className="absolute -bottom-8 -right-8 h-32 w-32 rounded-full bg-primary/5 blur-3xl" />
          </div>
        </section>
      </main>
    </>
  );
}

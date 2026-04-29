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
import { clearAddDeviceDraft, clearProvisionedDeviceDraft, getDeviceTypeOption, readAddDeviceDraft, readProvisionedDeviceDraft } from "@/lib/add-device-flow";
import { showNativeToast } from "@/lib/android-bridge";
import { useI18n } from "@/lib/i18n";
import { Link, Navigate, useNavigate } from "react-router-dom";

export default function AddDeviceIntegrationView() {
  const navigate = useNavigate();
  const { t } = useI18n("addDeviceIntegration");
  const common = useI18n("common").t;
  const draft = readAddDeviceDraft();
  const provisionedDevice = readProvisionedDeviceDraft();
  const deviceType = getDeviceTypeOption(draft.deviceTypeId);

  if (!deviceType || !provisionedDevice) {
    return <Navigate replace to="/devices/add/type" />;
  }

  const handleFinish = () => {
    clearAddDeviceDraft();
    clearProvisionedDeviceDraft();
    showNativeToast(t("successToast"));
    navigate("/devices");
  };

  const shellyScript = `// Shelly script placeholder
// deviceUuid: ${provisionedDevice.uuid}
// mqttUsername: ${provisionedDevice.mqttUsername ?? "pending"}
// mqttPassword: ${provisionedDevice.mqttPassword ?? "pending"}
// Full Shelly script will be added here later.`;

  return (
    <>
      <PageHeader
        rightSlot={<Link className="secondary-action px-4 py-2 text-sm" to="/menu">{common("menu")}</Link>}
        title={t("title")}
        compact
      />

      <main className="app-page pb-8 pt-4 sm:py-8">
        <section className="mb-10">
          <ProgressHeader label={t("stepLabel")} step={4} total={4} />
        </section>

        <div className="grid gap-12 items-start lg:grid-cols-12">
          <div className="space-y-8 lg:col-span-7">
            <section>
              <h2 className="mb-4 font-headline text-3xl font-extrabold leading-tight text-primary md:text-5xl">
                {t("headline", { deviceType: deviceType.title })}
              </h2>
              <p className="max-w-2xl text-lg text-on-surface-variant">
                {t("description")}
              </p>
            </section>

            <div className="rounded-xl bg-surface-container-low p-6">
              <p className="metric-label mb-2">{t("deviceIdentifier")}</p>
              <p className="font-mono text-base font-bold tracking-wider text-primary">{provisionedDevice.uuid}</p>
            </div>

            <div className="rounded-xl bg-surface-container-low p-6">
              <p className="metric-label mb-2">{t("setupInstructions")}</p>
              <p className="text-sm leading-relaxed text-on-surface-variant">
                {t("instructions")}
              </p>
            </div>

            <div className="rounded-xl bg-surface-container-low p-6">
              <p className="metric-label mb-2">{t("shellyJavascript")}</p>
              <div className="rounded-xl bg-surface-container-highest p-4">
                <pre className="overflow-x-auto whitespace-pre-wrap text-xs leading-relaxed text-on-surface">{shellyScript}</pre>
              </div>
            </div>

            <div className="pt-4">
              <button
                className="primary-action w-full justify-center"
                onClick={handleFinish}
                type="button"
              >
                {t("finishSetup")}
              </button>
              <Link className="mt-4 block w-full py-3 text-center text-sm font-bold text-primary/60 transition-colors hover:text-primary" to="/devices/add/review">
                {common("back")}
              </Link>
            </div>
          </div>
        </div>
      </main>
    </>
  );
}

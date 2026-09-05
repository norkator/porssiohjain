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
import { useControls } from "@/hooks/useControls";
import { clearAddDeviceDraft, clearProvisionedDeviceDraft, getDeviceTypeOption, readAddDeviceDraft, readProvisionedDeviceDraft } from "@/lib/add-device-flow";
import { showNativeToast } from "@/lib/android-bridge";
import { useI18n } from "@/lib/i18n";
import { MQTT_HOST, MQTT_PORT } from "@/lib/mqtt-config";
import shellyTemplate from "../../../devices/shelly/script.js?raw";
import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";

export default function AddDeviceIntegrationView() {
  const navigate = useNavigate();
  const { t } = useI18n("addDeviceIntegration");
  const deviceTypeLabels: Record<string, string> = useI18n("addDeviceType").group("deviceTypes");
  const common = useI18n("common").t;
  const draft = readAddDeviceDraft();
  const provisionedDevice = readProvisionedDeviceDraft();
  const deviceType = getDeviceTypeOption(draft.deviceTypeId);
  const [isCopying, setIsCopying] = useState(false);
  const {
    error: controlsError,
    isLoading: isControlsLoading,
    totalCount: controlsCount
  } = useControls();

  if (!deviceType || !provisionedDevice) {
    return <Navigate replace to="/devices/add/type" />;
  }

  const translatedDeviceTypeTitle = deviceTypeLabels[`${deviceType.id}.title`] ?? deviceType.title;
  const isOpenBeken = deviceType.id === "openbeken";
  const isShelly = deviceType.id === "shelly-pro-relays";
  const showFirstControlNextStep = !isControlsLoading && !controlsError && controlsCount === 0;

  const handleFinish = () => {
    clearAddDeviceDraft();
    clearProvisionedDeviceDraft();
    showNativeToast(t("successToast"));
    navigate("/devices");
  };

  const handleCreateFirstControl = () => {
    clearAddDeviceDraft();
    clearProvisionedDeviceDraft();
    showNativeToast(t("successToast"));
    navigate("/controls/add");
  };

  const shellyScript = shellyTemplate.replace(
    /^const DEVICE_UUID = '.*';$/m,
    `const DEVICE_UUID = '${provisionedDevice.uuid}';`
  );

  const handleCopyScript = async () => {
    setIsCopying(true);

    try {
      await navigator.clipboard.writeText(shellyScript);
      showNativeToast(t("copySuccess"));
    } catch {
      showNativeToast(t("copyFailed"));
    } finally {
      setIsCopying(false);
    }
  };

  const handleCopyValue = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      showNativeToast(t("copyValueSuccess"));
    } catch {
      showNativeToast(t("copyValueFailed"));
    }
  };

  const mqttRows = [
    { label: t("mqttHost"), value: MQTT_HOST },
    { label: t("mqttPort"), value: MQTT_PORT },
    ...(isOpenBeken ? [
      { label: t("openBekenClientTopic"), value: provisionedDevice.uuid },
      { label: t("openBekenGroupTopic"), value: provisionedDevice.uuid }
    ] : [
      { label: t("mqttDeviceUuid"), value: provisionedDevice.uuid }
    ]),
    { label: t("mqttUsername"), value: provisionedDevice.mqttUsername ?? t("pendingValue") },
    { label: t("mqttPassword"), value: provisionedDevice.mqttPassword ?? t("pendingValue") }
  ];

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

        <div className="mx-auto w-full max-w-5xl">
          <div className="space-y-8">
            <section>
              <h2 className="mb-4 font-headline text-3xl font-extrabold leading-tight text-primary md:text-5xl">
                {t("headline", { deviceType: translatedDeviceTypeTitle })}
              </h2>
              <p className="max-w-2xl text-lg text-on-surface-variant">
                {t(isOpenBeken ? "openBekenDescription" : "description")}
              </p>
            </section>

            <div className="overflow-hidden rounded-xl bg-surface-container-low p-4 sm:p-6">
              <p className="metric-label mb-2">{t("deviceIdentifier")}</p>
              <div className="overflow-x-auto">
                <p className="w-max min-w-full font-mono text-base font-bold tracking-wider text-primary">{provisionedDevice.uuid}</p>
              </div>
            </div>

            <div className="overflow-hidden rounded-xl bg-surface-container-low p-4 sm:p-6">
              <p className="metric-label mb-2">{t("setupInstructions")}</p>
              <p className="text-sm leading-relaxed text-on-surface-variant">
                {t(isOpenBeken ? "openBekenInstructions" : "instructions")}
              </p>
            </div>

            <div className="overflow-hidden rounded-xl bg-surface-container-low p-4 sm:p-6">
              <p className="metric-label mb-2">{t("mqttCredentials")}</p>
              <div className="space-y-4 rounded-xl bg-surface-container-highest p-4">
                {mqttRows.map((row) => (
                  <div key={row.label} className="rounded-lg bg-surface-container p-3">
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <p className="text-xs font-bold uppercase tracking-[0.16em] text-on-surface-variant">{row.label}</p>
                      <button
                        className="secondary-action shrink-0 px-3 py-1 text-xs"
                        onClick={() => void handleCopyValue(row.value)}
                        type="button"
                      >
                        {t("copyValue")}
                      </button>
                    </div>
                    <div className="overflow-x-auto">
                      <p className="w-max min-w-full font-mono text-sm font-bold text-on-surface">{row.value}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {isShelly ? (
            <div className="overflow-hidden rounded-xl bg-surface-container-low p-4 sm:p-6">
              <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="metric-label">{t("shellyJavascript")}</p>
                <button
                  className="secondary-action w-full justify-center px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                  disabled={isCopying}
                  onClick={() => void handleCopyScript()}
                  type="button"
                >
                  {isCopying ? t("copying") : t("copyScript")}
                </button>
              </div>
              <div className="max-h-72 max-w-full overflow-x-auto overflow-y-auto rounded-xl bg-surface-container-highest p-4 sm:max-h-[22rem]">
                <pre className="inline-block min-w-full whitespace-pre text-xs leading-relaxed text-on-surface">{shellyScript}</pre>
              </div>
            </div>
            ) : null}

            <div className="pt-4">
              {showFirstControlNextStep ? (
                <div className="mb-5 rounded-xl border border-primary/30 bg-surface-container-low p-4 sm:p-5">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="metric-label mb-2">{t("firstControlNextStepLabel")}</p>
                      <h3 className="font-headline text-xl font-black text-on-surface">{t("firstControlNextStepTitle")}</h3>
                      <p className="mt-1 text-sm leading-6 text-on-surface-variant">
                        {t("firstControlNextStepDescription")}
                      </p>
                    </div>
                    <button
                      className="primary-action w-full shrink-0 justify-center px-4 py-3 text-sm sm:w-auto"
                      onClick={handleCreateFirstControl}
                      type="button"
                    >
                      {t("createFirstControl")}
                    </button>
                  </div>
                </div>
              ) : null}
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

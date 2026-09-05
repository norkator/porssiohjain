import ThermostatCurveDialog from "@/components/ThermostatCurveDialog";
import AppDialog from "@/components/AppDialog";
import { addControlThermostatLink, deleteControlThermostatLink, fetchControlThermostatLinks, updateControlThermostatLink, type ControlThermostatLink, type ControlThermostatLinkPayload } from "@/lib/controls";
import { fetchDevices, type ApiDevice } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";
import { FormEvent, useEffect, useState } from "react";

const DEFAULT_CURVE = JSON.stringify([{ price: 0, temperature: 22 }, { price: 10, temperature: 21 }, { price: 20, temperature: 19 }], null, 2);

function optionalNumber(value: string) { return value === "" ? null : Number(value); }

function compareByName(left: string | null | undefined, right: string | null | undefined) {
  return (left ?? "").localeCompare(right ?? "", undefined, { sensitivity: "base" });
}

type ControlThermostatsCardProps = {
  controlId: number;
  isReadOnly: boolean;
  onLinksChange?: (count: number) => void;
};

export default function ControlThermostatsCard({ controlId, isReadOnly, onLinksChange }: ControlThermostatsCardProps) {
  const { t } = useI18n("manageControl");
  const common = useI18n("common").t;
  const [links, setLinks] = useState<ControlThermostatLink[]>([]);
  const [devices, setDevices] = useState<ApiDevice[]>([]);
  const [deviceId, setDeviceId] = useState("");
  const [channel, setChannel] = useState("1");
  const [curveJson, setCurveJson] = useState(DEFAULT_CURVE);
  const [minTemperature, setMinTemperature] = useState("");
  const [maxTemperature, setMaxTemperature] = useState("");
  const [fallbackTemperature, setFallbackTemperature] = useState("");
  const [estimatedPowerKw, setEstimatedPowerKw] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [curveOpen, setCurveOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sortedLinks = [...links].sort((left, right) =>
    compareByName(left.device.deviceName, right.device.deviceName)
    || left.thermostatChannel - right.thermostatChannel
    || left.id - right.id
  );

  useEffect(() => {
    Promise.all([fetchControlThermostatLinks(controlId), fetchDevices()])
      .then(([nextLinks, nextDevices]) => {
        setLinks(nextLinks);
        onLinksChange?.(nextLinks.length);
        setDevices(nextDevices.filter((device) => device.deviceType === "THERMOSTAT" && !device.shared));
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : t("failedLoadThermostats")));
  }, [controlId, onLinksChange]);

  const reset = () => {
    setEditingId(null); setDeviceId(""); setChannel("1"); setCurveJson(DEFAULT_CURVE);
    setMinTemperature(""); setMaxTemperature(""); setFallbackTemperature(""); setEstimatedPowerKw(""); setEnabled(true);
  };

  const openCreateForm = () => {
    reset();
    setIsFormOpen(true);
  };

  const closeForm = () => {
    setIsFormOpen(false);
    setCurveOpen(false);
    reset();
  };

  const edit = (link: ControlThermostatLink) => {
    setEditingId(link.id); setDeviceId(String(link.deviceId)); setChannel(String(link.thermostatChannel)); setCurveJson(link.curveJson);
    setMinTemperature(link.minTemperature === null ? "" : String(link.minTemperature));
    setMaxTemperature(link.maxTemperature === null ? "" : String(link.maxTemperature));
    setFallbackTemperature(link.fallbackTemperature === null ? "" : String(link.fallbackTemperature));
    setEstimatedPowerKw(link.estimatedPowerKw === null ? "" : String(link.estimatedPowerKw)); setEnabled(link.enabled);
    setIsFormOpen(true);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!deviceId || !Number.isInteger(Number(channel)) || Number(channel) < 0) return;
    const payload: ControlThermostatLinkPayload = { deviceId: Number(deviceId), thermostatChannel: Number(channel), curveJson, minTemperature: optionalNumber(minTemperature), maxTemperature: optionalNumber(maxTemperature), fallbackTemperature: optionalNumber(fallbackTemperature), estimatedPowerKw: optionalNumber(estimatedPowerKw), enabled };
    setIsSaving(true); setError(null);
    try {
      if (editingId === null) await addControlThermostatLink(controlId, payload);
      else await updateControlThermostatLink(editingId, payload);
      const nextLinks = await fetchControlThermostatLinks(controlId);
      setLinks(nextLinks);
      onLinksChange?.(nextLinks.length);
      closeForm();
    } catch (reason) { setError(reason instanceof Error ? reason.message : t("failedSaveThermostat")); }
    finally { setIsSaving(false); }
  };

  const remove = async (id: number) => {
    setError(null);
    try {
      await deleteControlThermostatLink(id);
      const nextLinks = links.filter((link) => link.id !== id);
      setLinks(nextLinks);
      onLinksChange?.(nextLinks.length);
      if (editingId === id) closeForm();
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : t("failedRemoveThermostat")); }
    setDeletingId(null);
  };

  return <>
    {!isReadOnly ? <div className="mb-4 flex justify-end">
      <button className="primary-action px-5 py-3 text-base" onClick={openCreateForm} type="button">{t("linkThermostatDevice")}</button>
    </div> : null}
    {links.length === 0 ? <p className="text-sm text-on-surface-variant">{t("noThermostatLinks")}</p> : null}
    <div className="space-y-3">
      {sortedLinks.map((link) => <div className="rounded-xl bg-surface-container p-4" key={link.id}>
        <div className="flex items-start justify-between gap-3">
          <div><p className="font-headline font-bold text-on-surface">{link.device.deviceName}</p><p className="text-sm text-on-surface-variant">{common("channel")} {link.thermostatChannel} · {link.enabled ? common("enabled") : common("disabled")}</p></div>
          {!isReadOnly ? <div className="flex gap-2">
            <button className="secondary-action px-3 py-2 text-xs" onClick={() => edit(link)} type="button">{common("edit")}</button>
            {deletingId === link.id ? <><button className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container" onClick={() => remove(link.id)} type="button">{common("confirm")}</button><button className="secondary-action px-3 py-2 text-xs" onClick={() => setDeletingId(null)} type="button">{common("cancel")}</button></> : <button className="rounded-lg bg-error-container px-3 py-2 text-xs font-bold text-on-error-container" onClick={() => setDeletingId(link.id)} type="button">{common("remove")}</button>}
          </div> : null}
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
          {[[t("minTemperature"), link.minTemperature], [t("maxTemperature"), link.maxTemperature], [t("fallbackTemperature"), link.fallbackTemperature], [t("estimatedKw"), link.estimatedPowerKw], [t("lastAppliedTemperature"), link.lastAppliedTemperature]].map(([label, value]) => <div key={String(label)}><span className="metric-label">{label}</span><p className="font-semibold text-on-surface">{value ?? "-"}</p></div>)}
        </div>
        <pre className="mt-3 max-h-32 overflow-auto rounded-lg bg-surface-container-highest p-3 text-xs text-on-surface">{link.curveJson}</pre>
      </div>)}
    </div>
    {error ? <div className="mt-4 rounded-xl bg-error-container/50 p-4 text-sm text-on-error-container">{error}</div> : null}
    {!isReadOnly ? <AppDialog
      description={editingId === null ? t("thermostatCreateDialogDescription") : t("thermostatEditDialogDescription")}
      isOpen={isFormOpen}
      maxWidthClassName="max-w-4xl"
      onClose={closeForm}
      title={editingId === null ? t("thermostatCreateDialogTitle") : t("thermostatEditDialogTitle")}
    >
      <form className="space-y-4" onSubmit={submit}>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <label className="text-sm font-bold">{t("device")}<select className="mt-2 w-full rounded-t-lg bg-surface-container-highest px-4 py-3 font-normal" onChange={(e) => setDeviceId(e.target.value)} value={deviceId}><option value="">{t("selectThermostatDevice")}</option>{devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}</select></label>
          <label className="text-sm font-bold">{common("channel")}<input className="mt-2 w-full rounded-t-lg bg-surface-container-highest px-4 py-3 font-normal" min="0" onChange={(e) => setChannel(e.target.value)} step="1" type="number" value={channel} /></label>
          {[[t("minTemperature"), minTemperature, setMinTemperature], [t("maxTemperature"), maxTemperature, setMaxTemperature], [t("fallbackTemperature"), fallbackTemperature, setFallbackTemperature], [t("estimatedKw"), estimatedPowerKw, setEstimatedPowerKw]].map(([label, value, setter]) => <label className="text-sm font-bold" key={String(label)}>{label as string}<input className="mt-2 w-full rounded-t-lg bg-surface-container-highest px-4 py-3 font-normal" onChange={(e) => (setter as (value: string) => void)(e.target.value)} step="0.1" type="number" value={value as string} /></label>)}
          <label className="flex items-center justify-between rounded-xl bg-surface-container p-4 text-sm font-bold">{common("enabled")}<input checked={enabled} onChange={(e) => setEnabled(e.target.checked)} type="checkbox" /></label>
          <button className="secondary-action justify-center" onClick={() => setCurveOpen(true)} type="button">{t("editCurve")}</button>
        </div>
        <label className="block text-sm font-bold">{t("curveJson")}<textarea className="mt-2 min-h-40 w-full rounded-xl bg-surface-container-highest p-4 font-mono text-xs font-normal" onChange={(e) => setCurveJson(e.target.value)} value={curveJson} /></label>
        <div className="flex flex-col gap-3 sm:flex-row"><button className="primary-action justify-center disabled:opacity-50" disabled={isSaving || !deviceId} type="submit">{isSaving ? t("saving") : editingId === null ? t("linkThermostatDevice") : t("saveThermostatRule")}</button><button className="secondary-action justify-center" onClick={closeForm} type="button">{common("cancel")}</button></div>
      </form>
    </AppDialog> : null}
    <ThermostatCurveDialog curveJson={curveJson} isOpen={curveOpen} labels={{ title: t("curveDialogTitle"), instructions: t("curveDialogInstructions"), addPoint: t("addPoint"), removePoint: t("removeSelectedPoint"), reset: t("resetCurve"), curveJson: t("curveJson"), invalidJson: t("invalidCurveJson"), priceAxis: t("priceAxis"), temperatureAxis: t("temperatureAxis"), pointTable: t("curvePointTable"), price: t("curvePointPrice"), temperature: t("curvePointTemperature"), cancel: common("cancel"), save: common("save") }} onClose={() => setCurveOpen(false)} onSave={(value) => { setCurveJson(value); setCurveOpen(false); }} />
  </>;
}

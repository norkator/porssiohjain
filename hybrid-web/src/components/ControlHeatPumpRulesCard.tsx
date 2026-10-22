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

import { useEffect, useRef, useState } from "react";
import AppDialog from "@/components/AppDialog";
import HeatPumpStateDialog from "@/components/HeatPumpStateDialog";
import HeatPumpRuleTimeline from "@/components/HeatPumpRuleTimeline";
import { previewHeatPumpRules, saveHeatPumpRules, type ControlHeatPumpLink, type HeatPumpRulesPayload, type HeatPumpPreview, type HeatPumpRule } from "@/lib/controls";
import { fetchHeatPumpState, type ApiDevice, type AcType } from "@/lib/devices";
import { useI18n } from "@/lib/i18n";

type Props = {
  controlId: number; timezone: string; links: ControlHeatPumpLink[]; devices: ApiDevice[];
  isReadOnly: boolean; onLinksChange: (links: ControlHeatPumpLink[]) => void;
};
type DraftRule = Omit<HeatPumpRule, "priceLimit" | "estimatedPowerKw"> & { key: string; price: string; power: string };
const blank = (outsideActiveTime = false): DraftRule => ({ key: crypto.randomUUID(), id: null, stateHex: "",
  controlAction: outsideActiveTime ? "TURN_OFF" : "TURN_ON", comparisonType: outsideActiveTime ? null : "LESS_THAN", price: "", power: "", outsideActiveTime });
const fromLink = (link: ControlHeatPumpLink): DraftRule => ({ ...link, key: crypto.randomUUID(),
  price: link.priceLimit === null ? "" : String(link.priceLimit), power: link.estimatedPowerKw === null ? "" : String(link.estimatedPowerKw) });

export default function ControlHeatPumpRulesCard({ controlId, timezone, links, devices, isReadOnly, onLinksChange }: Props) {
  const { t } = useI18n("heatPumpRules");
  const common = useI18n("common").t;
  const manage = useI18n("manageControl");
  const actionLabels: Record<string, string> = manage.group("actions");
  const acTypes: Record<string, string> = manage.group("acTypes");
  const [open, setOpen] = useState(false);
  const [deviceId, setDeviceId] = useState("");
  const [rules, setRules] = useState<DraftRule[]>([]);
  const [outside, setOutside] = useState<DraftRule>(() => blank(true));
  const [timed, setTimed] = useState(false);
  const [from, setFrom] = useState("07:00");
  const [until, setUntil] = useState("22:00");
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<HeatPumpPreview | null>(null);
  const [previewError, setPreviewError] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [target, setTarget] = useState<string | null>(null);
  const [stateValue, setStateValue] = useState("");
  const [currentState, setCurrentState] = useState<string | null>(null);
  const [lastPolled, setLastPolled] = useState<string | null>(null);
  const [acType, setAcType] = useState<AcType>("NONE");
  const [stateLoading, setStateLoading] = useState(false);
  const requestVersion = useRef(0);
  const selectedRules = timed ? [...rules, outside] : rules;
  const valid = Boolean(deviceId) && rules.length > 0 && (!timed || Boolean(from && until && from !== until))
    && selectedRules.every((rule) => rule.stateHex.trim() && (rule.comparisonType === null || rule.price.trim() !== "" && Number.isFinite(Number(rule.price)))
      && (rule.power === "" || Number.isFinite(Number(rule.power)) && Number(rule.power) >= 0));
  const payload: HeatPumpRulesPayload = { activeFrom: timed ? from : null, activeUntil: timed ? until : null,
    rules: selectedRules.map((rule) => ({ id: rule.id, stateHex: rule.stateHex, controlAction: rule.controlAction,
      comparisonType: rule.comparisonType, priceLimit: rule.comparisonType === null || rule.price === "" ? null : Number(rule.price),
      estimatedPowerKw: rule.power === "" ? null : Number(rule.power), outsideActiveTime: rule.outsideActiveTime })) };
  const serialized = JSON.stringify(payload);
  const pumps = [...new Map(links.map((link) => [link.deviceId, link])).values()].sort((a, b) => a.device.deviceName.localeCompare(b.device.deviceName));

  const close = () => {
    if (saving || target !== null) return;
    if (dirty && !window.confirm(t("discard"))) return;
    requestVersion.current++;
    setOpen(false);
    setDirty(false);
  };
  useEffect(() => {
    if (!dirty || !open) return;
    const beforeUnload = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [dirty, open]);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setPreview(null);
    setPreviewError(false);
    setPreviewLoading(false);
    if (!isReadOnly && !valid) return;
    setPreviewLoading(true);
    const timer = window.setTimeout(() => {
      previewHeatPumpRules(controlId, isReadOnly ? undefined : Number(deviceId), isReadOnly ? undefined : JSON.parse(serialized))
        .then((value) => { if (active) setPreview(value); })
        .catch(() => { if (active) setPreviewError(true); })
        .finally(() => { if (active) setPreviewLoading(false); });
    }, 350);
    return () => { active = false; window.clearTimeout(timer); };
  }, [open, controlId, deviceId, serialized, valid, isReadOnly, refresh]);

  function loadPump(id: string) {
    const existing = links.filter((link) => link.deviceId === Number(id)).sort((a, b) => (a.ruleOrder ?? a.id) - (b.ruleOrder ?? b.id));
    setDeviceId(id);
    const activeRules = existing.filter((link) => !link.outsideActiveTime).map(fromLink);
    const outsideRule = existing.find((link) => link.outsideActiveTime);
    setRules(activeRules.length ? activeRules : [blank()]);
    setOutside(outsideRule ? fromLink(outsideRule) : blank(true));
    setTimed(Boolean(existing[0]?.activeFrom));
    setFrom(existing[0]?.activeFrom?.slice(0, 5) ?? "07:00");
    setUntil(existing[0]?.activeUntil?.slice(0, 5) ?? "22:00");
    setDirty(false);
    setError(null);
    setPreview(null);
  }
  function change(key: string, patch: Partial<DraftRule>) {
    setRules((values) => values.map((rule) => rule.key === key ? { ...rule, ...patch } : rule));
    setOutside((value) => value.key === key ? { ...value, ...patch } : value);
    setDirty(true);
  }
  async function poll(rule: DraftRule) {
    const version = ++requestVersion.current;
    setStateLoading(true);
    setError(null);
    try {
      const response = await fetchHeatPumpState(Number(deviceId));
      if (version !== requestVersion.current) return;
      setAcType(response.acType);
      setCurrentState(response.currentState);
      setLastPolled(response.lastPolledState);
      setStateValue(rule.stateHex || response.currentState || response.lastPolledState || "");
    } catch {
      if (version === requestVersion.current) setError(manage.t("failedLoadHeatPumpState"));
    } finally { if (version === requestVersion.current) setStateLoading(false); }
  }
  function capture(rule: DraftRule) {
    setTarget(rule.key);
    setStateValue(rule.stateHex);
    setCurrentState(null);
    setLastPolled(null);
    setAcType("NONE");
    void poll(rule);
  }
  async function save() {
    if (!valid) { setError(t("invalid")); return; }
    setSaving(true);
    setError(null);
    try {
      const updated = await saveHeatPumpRules(controlId, Number(deviceId), payload);
      onLinksChange([...links.filter((link) => link.deviceId !== Number(deviceId)), ...updated]);
      setDirty(false);
      setOpen(false);
    } catch { setError(t("saveError")); }
    finally { setSaving(false); }
  }
  async function remove() {
    if (!window.confirm(t("confirmRemove"))) return;
    setSaving(true);
    try {
      await saveHeatPumpRules(controlId, Number(deviceId), { activeFrom: null, activeUntil: null, rules: [] });
      onLinksChange(links.filter((link) => link.deviceId !== Number(deviceId)));
      setDirty(false);
      setOpen(false);
    } catch { setError(manage.t("failedRemoveHeatPump")); }
    finally { setSaving(false); }
  }
  function move(index: number, delta: number) {
    setRules((values) => { const next = [...values]; [next[index], next[index + delta]] = [next[index + delta], next[index]]; return next; });
    setDirty(true);
  }
  function ruleEditor(rule: DraftRule, index?: number) {
    return <div className="space-y-3 rounded-xl bg-surface-container p-4" key={rule.key}>
      {index !== undefined ? <div className="flex flex-wrap items-center gap-2">
        <strong className="mr-auto text-sm">{t("rule", { number: index + 1 })}</strong>
        <button type="button" className="secondary-action px-3 py-1" aria-label={t("moveUp")} disabled={index === 0} onClick={() => move(index, -1)}>↑</button>
        <button type="button" className="secondary-action px-3 py-1" aria-label={t("moveDown")} disabled={index === rules.length - 1} onClick={() => move(index, 1)}>↓</button>
        <button type="button" className="secondary-action px-3 py-1 text-xs" disabled={rules.length === 1}
          onClick={() => { setRules((values) => values.filter((value) => value.key !== rule.key)); setDirty(true); }}>{t("removeRule")}</button>
      </div> : null}
      {!rule.outsideActiveTime ? <div className="flex flex-wrap gap-2">
        <label className="min-w-0 flex-1 text-sm">{t("when")}
          <select className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" value={rule.comparisonType ?? "SCHEDULE"}
            onChange={(event) => change(rule.key, { comparisonType: event.target.value === "SCHEDULE" ? null : event.target.value as DraftRule["comparisonType"] })}>
            <option value="LESS_THAN">{t("below")}</option><option value="GREATER_THAN">{t("above")}</option><option value="SCHEDULE">{t("schedule")}</option>
          </select>
        </label>
        {rule.comparisonType ? <label className="w-32 text-sm">snt/kWh<input className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" type="number" step="0.1" value={rule.price}
          onChange={(event) => change(rule.key, { price: event.target.value })} /></label> : null}
      </div> : null}
      <label className="block text-sm">{t("action")}<select className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" value={rule.controlAction}
        onChange={(event) => change(rule.key, { controlAction: event.target.value as DraftRule["controlAction"] })}>
        {Object.entries(actionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <button className="secondary-action w-full justify-center" type="button" disabled={!deviceId} onClick={() => capture(rule)}>{t("capture")}</button>
      <p className={`text-xs ${rule.stateHex ? "text-primary" : "text-on-surface-variant"}`}>{rule.stateHex ? t("captured") : t("notCaptured")}</p>
      <details><summary className="cursor-pointer text-xs">{t("state")}</summary>
        <pre className="max-h-28 overflow-auto whitespace-pre-wrap break-all p-2 text-xs">{rule.stateHex || "—"}</pre>
        <label className="block text-sm">{manage.t("estimatedKw")}<input className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" type="number" min="0" step="0.1" value={rule.power}
          onChange={(event) => change(rule.key, { power: event.target.value })} /></label>
      </details>
    </div>;
  }
  const activeTarget = [...rules, outside].find((rule) => rule.key === target);
  return <>
    <div className="space-y-3">
      {pumps.length === 0 ? <p className="text-sm text-on-surface-variant">{manage.t("noHeatPumpLinks")}</p> : null}
      {pumps.map((pump) => <button key={pump.deviceId} type="button" className="flex w-full items-center justify-between gap-3 rounded-xl bg-surface-container p-4 text-left"
        onClick={() => { loadPump(String(pump.deviceId)); setOpen(true); }}>
        <span><strong className="block">{pump.device.deviceName}</strong><span className="text-xs text-on-surface-variant">
          {links.filter((link) => link.deviceId === pump.deviceId && !link.outsideActiveTime).length} {t("rules")} · {pump.activeFrom ? `${pump.activeFrom.slice(0, 5)} – ${pump.activeUntil?.slice(0, 5)}` : t("allDay")}
        </span></span><span className="text-sm font-bold text-primary">{t("edit")}</span>
      </button>)}
      {!isReadOnly ? <button type="button" className="secondary-action w-full justify-center" onClick={() => { loadPump(""); setOpen(true); }}>{t("addPump")}</button> : null}
    </div>
    <AppDialog isOpen={open} onClose={close} title={t("title")} description={t("description")} maxWidthClassName="max-w-7xl" isDismissible={!saving && target === null}>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(300px,2fr)_minmax(0,3fr)]">
        {!isReadOnly ? <fieldset disabled={saving} className="min-w-0 space-y-4">
          <label className="block text-sm font-bold">{t("pump")}<select className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" value={deviceId} disabled={Boolean(deviceId && rules.some((rule) => rule.stateHex))}
            onChange={(event) => loadPump(event.target.value)}><option value="">{t("pump")}</option>
            {devices.filter((device) => !device.shared).map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
          </select></label>
          <p className="text-xs text-on-surface-variant">{t("order")}</p>
          {rules.map((rule, index) => ruleEditor(rule, index))}
          <button type="button" className="secondary-action w-full justify-center" onClick={() => { setRules((values) => [...values, blank()]); setDirty(true); }}>{t("addRule")}</button>
          <label className="flex items-center gap-2 text-sm font-bold"><input type="checkbox" checked={timed} onChange={(event) => { setTimed(event.target.checked); setDirty(true); }} />{t("timed")}</label>
          {timed ? <><div className="grid grid-cols-2 gap-3">
            <label className="text-sm">{t("from")}<input type="time" className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" value={from} onChange={(event) => { setFrom(event.target.value); setDirty(true); }} /></label>
            <label className="text-sm">{t("until")}<input type="time" className="rounded-lg bg-surface-container-highest px-3 py-2 text-on-surface mt-1 w-full" value={until} onChange={(event) => { setUntil(event.target.value); setDirty(true); }} /></label>
          </div><p className="text-sm font-bold">{t("outside")}</p>{ruleEditor(outside)}</> : null}
          <p className="text-xs text-on-surface-variant">{t("timezone", { timezone })}</p>
          <p className="text-xs text-on-surface-variant">{t("captureHelp")}</p>
        </fieldset> : null}
        <section className={`order-first min-w-0 space-y-3 lg:order-last lg:sticky lg:top-0 ${isReadOnly ? "lg:col-span-2" : ""}`} aria-busy={previewLoading}>
          <h3 className="font-headline text-lg font-bold">{t("preview")}</h3>
          <p className="text-xs text-on-surface-variant">{t("previewHelp")}</p>
          {previewLoading ? <p className="text-sm" role="status">{t("loading")}</p> : null}
          {preview ? <HeatPumpRuleTimeline preview={preview} deviceId={Number(deviceId)} /> : null}
          {!previewLoading && !preview && !previewError ? <p className="rounded-xl bg-surface-container p-6 text-sm">{valid ? t("loading") : selectedRules.some((rule) => !rule.stateHex) || !deviceId ? t("captureHint") : t("invalid")}</p> : null}
          {previewError ? <div className="rounded-xl bg-error-container p-4 text-sm" role="alert"><p>{t("previewError")}</p>
            <button className="secondary-action mt-2" type="button" onClick={() => setRefresh((value) => value + 1)}>{t("retry")}</button></div> : null}
        </section>
      </div>
      {error ? <p className="mt-4 rounded-xl bg-error-container p-4 text-sm" role="alert">{error}</p> : null}
      {!isReadOnly ? <div className="mt-6 flex flex-wrap justify-end gap-3">
        {links.some((link) => link.deviceId === Number(deviceId)) ? <button className="secondary-action mr-auto" type="button" disabled={saving} onClick={() => void remove()}>{t("removePump")}</button> : null}
        <button className="secondary-action" type="button" disabled={saving} onClick={close}>{common("cancel")}</button>
        <button className="primary-action" type="button" disabled={saving || !valid} onClick={() => void save()}>{saving ? common("loading") : t("save")}</button>
      </div> : null}
    </AppDialog>
    <HeatPumpStateDialog acType={acType} errorMessage={error} currentState={currentState} lastPolledState={lastPolled} stateValue={stateValue}
      formatAcType={(value) => acTypes[value] ?? value} isLoading={stateLoading} isOpen={target !== null}
      onClose={() => { requestVersion.current++; setTarget(null); setStateLoading(false); }}
      onRefresh={() => { if (activeTarget) void poll(activeTarget); }} onStateChange={setStateValue}
      onSave={(value) => { if (target) change(target, { stateHex: value }); requestVersion.current++; setTarget(null); setStateLoading(false); }}
      labels={{ acType: manage.t("acType"), auto: manage.t("auto"), cancel: common("cancel"), close: common("close"), cool: manage.t("cool"), dry: manage.t("dry"),
        fanOnly: manage.t("fanOnly"), fanSpeed: manage.t("fanSpeed"), heat: manage.t("heat"), heatPumpState: manage.t("heatPumpState"), heatPumpStateHelp: manage.t("heatPumpStateHelp"),
        invalidState: manage.t("invalidMitsubishiState"), loading: common("loading"), mode: manage.t("workingMode"), off: t("off"), on: t("on"), power: manage.t("powerMode"),
        rawState: manage.t("rawState"), refreshCurrentState: manage.t("refreshCurrentState"), saveState: manage.t("saveState"), selectCommandState: manage.t("selectCommandState"),
        targetTemperature: manage.t("targetTemperature"), useCurrent: manage.t("useCurrent"), useLastPolled: manage.t("useLastPolled") }} />
  </>;
}

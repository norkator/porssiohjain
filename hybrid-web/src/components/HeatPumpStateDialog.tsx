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

import { type AcType } from "@/lib/devices";
import { useEffect, useMemo, useRef, useState } from "react";

const MITSUBISHI_EFFECTIVE_FLAG_POWER = 0x01;
const MITSUBISHI_EFFECTIVE_FLAG_MODE = 0x02;
const MITSUBISHI_EFFECTIVE_FLAG_TEMPERATURE = 0x04;
const MITSUBISHI_EFFECTIVE_FLAG_FAN_SPEED = 0x08;

type MitsubishiModeCode = 1 | 2 | 3 | 7 | 8;

type MitsubishiState = {
  EffectiveFlags?: number | null;
  SetTemperature?: number | null;
  SetFanSpeed?: number | null;
  OperationMode?: number | null;
  NumberOfFanSpeeds?: number | null;
  Power?: boolean | null;
  HasPendingCommand?: boolean | null;
  [key: string]: unknown;
};

type HeatPumpStateDialogLabels = {
  close: string;
  cancel: string;
  loading: string;
  heatPumpState: string;
  selectCommandState: string;
  refreshCurrentState: string;
  useCurrent: string;
  useLastPolled: string;
  acType: string;
  heatPumpStateHelp: string;
  saveState: string;
  power: string;
  mode: string;
  targetTemperature: string;
  fanSpeed: string;
  rawState: string;
  mitsubishiEditorHelp: string;
  on: string;
  off: string;
  auto: string;
  heat: string;
  dry: string;
  cool: string;
  fanOnly: string;
  invalidState: string;
};

type Props = {
  acType: AcType;
  currentState: string | null;
  isLoading: boolean;
  isOpen: boolean;
  labels: HeatPumpStateDialogLabels;
  lastPolledState: string | null;
  onClose: () => void;
  onRefresh: () => void;
  onSave: (value: string) => void;
  onStateChange: (value: string) => void;
  stateValue: string;
  formatAcType: (value: AcType) => string;
};

const MITSUBISHI_MODES: Array<{ code: MitsubishiModeCode; key: keyof Pick<HeatPumpStateDialogLabels, "auto" | "cool" | "dry" | "fanOnly" | "heat"> }> = [
  { code: 1, key: "heat" },
  { code: 2, key: "dry" },
  { code: 3, key: "cool" },
  { code: 7, key: "fanOnly" },
  { code: 8, key: "auto" }
];

function parseMitsubishiState(value: string) {
  if (!value.trim()) {
    return null;
  }

  const parsed = JSON.parse(value) as MitsubishiState;
  return parsed && typeof parsed === "object" ? parsed : null;
}

function stringifyMitsubishiState(value: MitsubishiState) {
  return JSON.stringify(value, null, 2);
}

export default function HeatPumpStateDialog({
  acType,
  currentState,
  formatAcType,
  isLoading,
  isOpen,
  labels,
  lastPolledState,
  onClose,
  onRefresh,
  onSave,
  onStateChange,
  stateValue
}: Props) {
  const [effectiveFlags, setEffectiveFlags] = useState(0);
  const editorUpdatingRef = useRef(false);
  const isMitsubishi = acType === "MITSUBISHI";

  const parsedMitsubishiState = useMemo(() => {
    if (!isMitsubishi) {
      return null;
    }
    try {
      return parseMitsubishiState(stateValue);
    } catch {
      return null;
    }
  }, [isMitsubishi, stateValue]);

  const mitsubishiParseError = isMitsubishi && stateValue.trim() && !parsedMitsubishiState ? labels.invalidState : null;
  const maxFanSpeed = parsedMitsubishiState?.NumberOfFanSpeeds && parsedMitsubishiState.NumberOfFanSpeeds > 0
    ? parsedMitsubishiState.NumberOfFanSpeeds
    : undefined;

  useEffect(() => {
    if (!isOpen || !isMitsubishi) {
      return;
    }
    if (editorUpdatingRef.current) {
      editorUpdatingRef.current = false;
      return;
    }
    setEffectiveFlags(0);
  }, [isMitsubishi, isOpen, stateValue]);

  if (!isOpen) {
    return null;
  }

  const applyMitsubishiChange = (flag: number, updater: (state: MitsubishiState) => void) => {
    if (!parsedMitsubishiState) {
      return;
    }
    const nextFlags = effectiveFlags | flag;
    const nextState: MitsubishiState = { ...parsedMitsubishiState };
    updater(nextState);
    nextState.EffectiveFlags = nextFlags;
    nextState.HasPendingCommand = true;
    editorUpdatingRef.current = true;
    setEffectiveFlags(nextFlags);
    onStateChange(stringifyMitsubishiState(nextState));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center">
      <div className="w-full max-w-4xl rounded-xl bg-surface-container-lowest p-6 shadow-2xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="metric-label mb-2">{labels.heatPumpState}</p>
            <h2 className="font-headline text-2xl font-bold text-primary">{labels.selectCommandState}</h2>
          </div>
          <button className="secondary-action px-3 py-2 text-sm" onClick={onClose} type="button">{labels.close}</button>
        </div>

        <div className="mb-4 flex flex-wrap gap-2">
          <button
            className="secondary-action px-4 py-3 text-sm disabled:opacity-60"
            disabled={isLoading}
            onClick={onRefresh}
            type="button"
          >
            {isLoading ? labels.loading : labels.refreshCurrentState}
          </button>
          <button
            className="secondary-action px-4 py-3 text-sm disabled:opacity-60"
            disabled={!currentState}
            onClick={() => onStateChange(currentState ?? "")}
            type="button"
          >
            {labels.useCurrent}
          </button>
          <button
            className="secondary-action px-4 py-3 text-sm disabled:opacity-60"
            disabled={!lastPolledState}
            onClick={() => onStateChange(lastPolledState ?? "")}
            type="button"
          >
            {labels.useLastPolled}
          </button>
        </div>

        <div className="mb-4 grid gap-4 md:grid-cols-2">
          <div className="rounded-xl bg-surface-container p-4">
            <span className="metric-label">{labels.acType}</span>
            <p className="mt-2 font-semibold">{formatAcType(acType)}</p>
          </div>
          <div className="rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">
            {labels.heatPumpStateHelp}
          </div>
        </div>

        {isMitsubishi ? (
          <>
            <div className="mb-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.power}</span>
                <select
                  className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary disabled:opacity-60"
                  disabled={!parsedMitsubishiState}
                  onChange={(event) => {
                    const value = event.target.value;
                    if (!value) {
                      return;
                    }
                    applyMitsubishiChange(MITSUBISHI_EFFECTIVE_FLAG_POWER, (state) => {
                      state.Power = value === "ON";
                    });
                  }}
                  value={parsedMitsubishiState?.Power === true ? "ON" : parsedMitsubishiState?.Power === false ? "OFF" : ""}
                >
                  <option value=""></option>
                  <option value="ON">{labels.on}</option>
                  <option value="OFF">{labels.off}</option>
                </select>
              </label>

              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.mode}</span>
                <select
                  className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary disabled:opacity-60"
                  disabled={!parsedMitsubishiState}
                  onChange={(event) => {
                    const nextMode = Number(event.target.value);
                    if (!Number.isFinite(nextMode)) {
                      return;
                    }
                    applyMitsubishiChange(MITSUBISHI_EFFECTIVE_FLAG_MODE, (state) => {
                      state.OperationMode = nextMode;
                    });
                  }}
                  value={parsedMitsubishiState?.OperationMode ?? ""}
                >
                  <option value=""></option>
                  {MITSUBISHI_MODES.map((mode) => (
                    <option key={mode.code} value={mode.code}>{labels[mode.key]}</option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.targetTemperature}</span>
                <input
                  className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary disabled:opacity-60"
                  disabled={!parsedMitsubishiState}
                  onChange={(event) => {
                    const nextTemperature = Number(event.target.value);
                    if (!Number.isFinite(nextTemperature)) {
                      return;
                    }
                    applyMitsubishiChange(MITSUBISHI_EFFECTIVE_FLAG_TEMPERATURE, (state) => {
                      state.SetTemperature = nextTemperature;
                    });
                  }}
                  step="0.5"
                  type="number"
                  value={parsedMitsubishiState?.SetTemperature ?? ""}
                />
              </label>

              <label className="block">
                <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.fanSpeed}</span>
                <input
                  className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary disabled:opacity-60"
                  disabled={!parsedMitsubishiState}
                  max={maxFanSpeed}
                  min="0"
                  onChange={(event) => {
                    const nextFanSpeed = Number(event.target.value);
                    if (!Number.isInteger(nextFanSpeed)) {
                      return;
                    }
                    applyMitsubishiChange(MITSUBISHI_EFFECTIVE_FLAG_FAN_SPEED, (state) => {
                      state.SetFanSpeed = nextFanSpeed;
                    });
                  }}
                  step="1"
                  type="number"
                  value={parsedMitsubishiState?.SetFanSpeed ?? ""}
                />
                <span className="mt-2 ml-1 block text-xs text-on-surface-variant">
                  {maxFanSpeed ? `${labels.auto} = 0, 1..${maxFanSpeed}` : `${labels.auto} = 0`}
                </span>
              </label>
            </div>

            <div className="mb-4 rounded-xl bg-surface-container p-4 text-sm text-on-surface-variant">
              {labels.mitsubishiEditorHelp}
            </div>
          </>
        ) : null}

        {mitsubishiParseError ? (
          <div className="mb-4 rounded-xl bg-error-container/70 p-4 text-sm text-on-error-container">
            {mitsubishiParseError}
          </div>
        ) : null}

        <label className="block">
          <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.rawState}</span>
          <textarea
            className="min-h-72 w-full rounded-xl bg-surface-container px-4 py-3 font-mono text-xs outline-none"
            onChange={(event) => onStateChange(event.target.value)}
            value={stateValue}
          />
        </label>

        <div className="mt-5 grid grid-cols-2 gap-3">
          <button
            className="primary-action justify-center disabled:opacity-60"
            disabled={Boolean(mitsubishiParseError)}
            onClick={() => onSave(stateValue.trim())}
            type="button"
          >
            {labels.saveState}
          </button>
          <button className="secondary-action justify-center" onClick={onClose} type="button">{labels.cancel}</button>
        </div>
      </div>
    </div>
  );
}

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
const TOSHIBA_EXPECTED_BYTE_LENGTH = 19;
const TOSHIBA_EXPECTED_HEX_LENGTH = TOSHIBA_EXPECTED_BYTE_LENGTH * 2;
const TOSHIBA_MIN_TARGET_TEMPERATURE = 16;
const TOSHIBA_MAX_TARGET_TEMPERATURE = 30;

type MitsubishiModeCode = 1 | 2 | 3 | 7 | 8;
type ToshibaModeCode = "AUTO" | "COOL" | "HEAT" | "DRY" | "FAN";

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
  sendState?: string;
  sendingState?: string;
};

type Props = {
  acType: AcType;
  currentState: string | null;
  errorMessage?: string | null;
  isLoading: boolean;
  isOpen: boolean;
  labels: HeatPumpStateDialogLabels;
  lastPolledState: string | null;
  onClose: () => void;
  onRefresh: () => void;
  onSave: (value: string) => void;
  onSend?: (value: string) => void;
  onStateChange: (value: string) => void;
  stateValue: string;
  formatAcType: (value: AcType) => string;
  isSending?: boolean;
};

const MITSUBISHI_MODES: Array<{ code: MitsubishiModeCode; key: keyof Pick<HeatPumpStateDialogLabels, "auto" | "cool" | "dry" | "fanOnly" | "heat"> }> = [
  { code: 1, key: "heat" },
  { code: 2, key: "dry" },
  { code: 3, key: "cool" },
  { code: 7, key: "fanOnly" },
  { code: 8, key: "auto" }
];

const TOSHIBA_MODES: Array<{ code: ToshibaModeCode; key: keyof Pick<HeatPumpStateDialogLabels, "auto" | "cool" | "dry" | "fanOnly" | "heat">; rawHex: string }> = [
  { code: "AUTO", key: "auto", rawHex: "41" },
  { code: "COOL", key: "cool", rawHex: "42" },
  { code: "HEAT", key: "heat", rawHex: "43" },
  { code: "DRY", key: "dry", rawHex: "44" },
  { code: "FAN", key: "fanOnly", rawHex: "45" }
];

function normalizeToshibaState(value: string) {
  if (!value.trim()) {
    throw new Error("AC state hex is empty.");
  }

  let normalized = value.trim().replace(/ /g, "").toUpperCase();
  if ((normalized.length % 2) !== 0) {
    throw new Error("AC state hex length must be even.");
  }
  if (!/^[0-9A-F]+$/.test(normalized)) {
    throw new Error("AC state hex contains non-hex characters.");
  }
  if (normalized.length < TOSHIBA_EXPECTED_HEX_LENGTH) {
    throw new Error("AC state hex must contain at least 19 bytes.");
  }
  if (normalized.length > TOSHIBA_EXPECTED_HEX_LENGTH) {
    normalized = normalized.slice(0, TOSHIBA_EXPECTED_HEX_LENGTH);
  }
  return normalized;
}

function parseToshibaMode(rawHex: string): ToshibaModeCode | "" {
  switch (rawHex) {
    case "41":
      return "AUTO";
    case "42":
      return "COOL";
    case "43":
      return "HEAT";
    case "44":
      return "DRY";
    case "45":
      return "FAN";
    default:
      return "";
  }
}

function updateToshibaState(
  value: string,
  updates: {
    mode?: ToshibaModeCode;
    power?: boolean;
    targetTemperature?: number;
  }
) {
  const normalized = normalizeToshibaState(value);
  const bytes = normalized.match(/.{2}/g);

  if (!bytes) {
    throw new Error("AC state hex is empty.");
  }

  if (updates.power !== undefined) {
    bytes[0] = updates.power ? "30" : "31";
  }
  if (updates.mode) {
    const mode = TOSHIBA_MODES.find((item) => item.code === updates.mode);
    if (mode) {
      bytes[1] = mode.rawHex;
    }
  }
  if (updates.targetTemperature !== undefined) {
    if (updates.targetTemperature < TOSHIBA_MIN_TARGET_TEMPERATURE || updates.targetTemperature > TOSHIBA_MAX_TARGET_TEMPERATURE) {
      throw new Error("Target temperature out of range.");
    }
    bytes[2] = updates.targetTemperature.toString(16).padStart(2, "0").toUpperCase();
  }

  return bytes.join("");
}

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
  errorMessage,
  formatAcType,
  isLoading,
  isOpen,
  labels,
  lastPolledState,
  onClose,
  onRefresh,
  onSave,
  onSend,
  onStateChange,
  stateValue,
  isSending = false
}: Props) {
  const [effectiveFlags, setEffectiveFlags] = useState(0);
  const editorUpdatingRef = useRef(false);
  const isMitsubishi = acType === "MITSUBISHI";
  const isToshiba = acType === "TOSHIBA";

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
  const normalizedToshibaState = useMemo(() => {
    if (!isToshiba || !stateValue.trim()) {
      return null;
    }
    try {
      return normalizeToshibaState(stateValue);
    } catch {
      return null;
    }
  }, [isToshiba, stateValue]);
  const toshibaParseError = isToshiba && stateValue.trim() && !normalizedToshibaState ? labels.invalidState : null;
  const toshibaBytes = normalizedToshibaState?.match(/.{2}/g) ?? null;
  const toshibaPowerValue = toshibaBytes?.[0] === "30" ? "ON" : toshibaBytes?.[0] === "31" ? "OFF" : "";
  const toshibaModeValue = toshibaBytes?.[1] ? parseToshibaMode(toshibaBytes[1]) : "";
  const toshibaTargetTemperatureValue = toshibaBytes?.[2] ? Number.parseInt(toshibaBytes[2], 16) : "";

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
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-on-surface/40 p-4 sm:items-center">
      <div className="my-auto flex max-h-[calc(100dvh-2rem)] w-full max-w-4xl flex-col rounded-xl bg-surface-container-lowest shadow-2xl">
        <div className="min-h-0 overflow-y-auto p-6">
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

        {isToshiba ? (
          <div className="mb-4 grid gap-4 md:grid-cols-3">
            <label className="block">
              <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.power}</span>
              <select
                className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary disabled:opacity-60"
                disabled={!normalizedToshibaState}
                onChange={(event) => {
                  const value = event.target.value;
                  if (!value) {
                    return;
                  }
                  onStateChange(updateToshibaState(stateValue, { power: value === "ON" }));
                }}
                value={toshibaPowerValue}
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
                disabled={!normalizedToshibaState}
                onChange={(event) => {
                  const value = event.target.value as ToshibaModeCode | "";
                  if (!value) {
                    return;
                  }
                  onStateChange(updateToshibaState(stateValue, { mode: value }));
                }}
                value={toshibaModeValue}
              >
                <option value=""></option>
                {TOSHIBA_MODES.map((mode) => (
                  <option key={mode.code} value={mode.code}>{labels[mode.key]}</option>
                ))}
              </select>
            </label>

            <label className="block">
              <span className="mb-2 ml-1 block font-headline text-sm font-bold text-on-surface">{labels.targetTemperature}</span>
              <input
                className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container px-4 py-3 text-on-surface outline-none transition-all focus:border-primary disabled:opacity-60"
                disabled={!normalizedToshibaState}
                max={TOSHIBA_MAX_TARGET_TEMPERATURE}
                min={TOSHIBA_MIN_TARGET_TEMPERATURE}
                onChange={(event) => {
                  const nextTemperature = Number(event.target.value);
                  if (!Number.isInteger(nextTemperature)) {
                    return;
                  }
                  onStateChange(updateToshibaState(stateValue, { targetTemperature: nextTemperature }));
                }}
                step="1"
                type="number"
                value={toshibaTargetTemperatureValue}
              />
              <span className="mt-2 ml-1 block text-xs text-on-surface-variant">
                {TOSHIBA_MIN_TARGET_TEMPERATURE}..{TOSHIBA_MAX_TARGET_TEMPERATURE} C
              </span>
            </label>
          </div>
        ) : null}

        {mitsubishiParseError || toshibaParseError ? (
          <div className="mb-4 rounded-xl bg-error-container/70 p-4 text-sm text-on-error-container">
            {mitsubishiParseError ?? toshibaParseError}
          </div>
        ) : null}

        {errorMessage ? (
          <div className="mb-4 rounded-xl bg-error-container/70 p-4 text-sm text-on-error-container">
            {errorMessage}
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
            disabled={Boolean(mitsubishiParseError || toshibaParseError) || isSending}
            onClick={() => onSave(stateValue.trim())}
            type="button"
          >
            {labels.saveState}
          </button>
          {onSend ? (
            <button
              className="primary-action justify-center disabled:opacity-60"
              disabled={Boolean(mitsubishiParseError || toshibaParseError) || !stateValue.trim() || isSending}
              onClick={() => onSend(stateValue.trim())}
              type="button"
            >
              {isSending ? labels.sendingState ?? labels.loading : labels.sendState ?? labels.saveState}
            </button>
          ) : (
            <button className="secondary-action justify-center" onClick={onClose} type="button">{labels.cancel}</button>
          )}
        </div>
        {onSend ? (
          <div className="mt-3">
            <button className="secondary-action w-full justify-center" disabled={isSending} onClick={onClose} type="button">{labels.cancel}</button>
          </div>
        ) : null}
        </div>
      </div>
    </div>
  );
}

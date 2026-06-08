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

import type { InputHTMLAttributes } from "react";
import { getCurrentIntlLocales } from "@/lib/i18n";

type TimeInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "inputMode" | "onChange" | "pattern" | "placeholder" | "type" | "value"> & {
  onChange: (value: string) => void;
  value: string;
};

const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, minute) => String(minute).padStart(2, "0"));
const HOUR_12_OPTIONS = Array.from({ length: 12 }, (_, hour) => String(hour + 1));

function usesTwelveHourClock() {
  if (typeof Intl === "undefined") {
    return false;
  }

  return Intl.DateTimeFormat(getCurrentIntlLocales(), { hour: "numeric" }).resolvedOptions().hour12 === true;
}

function parseTime(value: string) {
  const [hourValue = "0", minuteValue = "0"] = value.split(":");
  const hours = Math.min(23, Math.max(0, Number(hourValue) || 0));
  const minutes = Math.min(59, Math.max(0, Number(minuteValue) || 0));

  return { hours, minutes };
}

function formatPartialTimeInput(value: string) {
  const digits = value.replace(/\D/g, "").slice(0, 4);

  if (digits.length <= 2) {
    return digits;
  }

  return `${digits.slice(0, 2)}:${digits.slice(2)}`;
}

function normalizeTime(value: string) {
  if (!value.trim()) {
    return "00:00";
  }

  const match = /^(\d{1,2}):?(\d{0,2})$/.exec(value.trim());

  if (!match) {
    return value;
  }

  const hours = Math.min(23, Number(match[1] || "0"));
  const minutes = Math.min(59, Number(match[2] || "0"));

  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

function toTimeValue(hours: number, minutes: number) {
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

function toTwentyFourHour(displayHour: number, period: string) {
  const normalizedHour = displayHour % 12;

  return period === "PM" ? normalizedHour + 12 : normalizedHour;
}

export default function TimeInput({ className, disabled, id, onBlur, onChange, required, value, ...props }: TimeInputProps) {
  const { hours, minutes } = parseTime(value);
  const period = hours >= 12 ? "PM" : "AM";
  const displayHour = hours % 12 || 12;

  if (usesTwelveHourClock()) {
    return (
      <div className={`${className ?? ""} flex items-center gap-2`}>
        <select
          aria-label={props["aria-label"]}
          className="min-w-0 flex-1 bg-transparent text-on-surface outline-none"
          disabled={disabled}
          id={id}
          onChange={(event) => onChange(toTimeValue(toTwentyFourHour(Number(event.target.value), period), minutes))}
          required={required}
          value={String(displayHour)}
        >
          {HOUR_12_OPTIONS.map((hour) => <option key={hour} value={hour}>{hour}</option>)}
        </select>
        <select
          aria-label={props["aria-label"] ? `${props["aria-label"]} minutes` : undefined}
          className="min-w-0 flex-1 bg-transparent text-on-surface outline-none"
          disabled={disabled}
          onChange={(event) => onChange(toTimeValue(hours, Number(event.target.value)))}
          required={required}
          value={String(minutes).padStart(2, "0")}
        >
          {MINUTE_OPTIONS.map((minute) => <option key={minute} value={minute}>{minute}</option>)}
        </select>
        <select
          aria-label={props["aria-label"] ? `${props["aria-label"]} period` : undefined}
          className="min-w-0 flex-1 bg-transparent text-on-surface outline-none"
          disabled={disabled}
          onChange={(event) => onChange(toTimeValue(toTwentyFourHour(displayHour, event.target.value), minutes))}
          required={required}
          value={period}
        >
          <option value="AM">AM</option>
          <option value="PM">PM</option>
        </select>
      </div>
    );
  }

  return (
    <input
      {...props}
      className={className}
      disabled={disabled}
      id={id}
      inputMode="numeric"
      maxLength={5}
      onBlur={(event) => {
        const normalizedValue = normalizeTime(event.target.value);
        onChange(normalizedValue);
        onBlur?.(event);
      }}
      onChange={(event) => onChange(formatPartialTimeInput(event.target.value))}
      pattern="([01][0-9]|2[0-3]):[0-5][0-9]"
      placeholder="00:00"
      required={required}
      type="text"
      value={value}
    />
  );
}

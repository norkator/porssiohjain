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
import { type HeatPumpPreview, type HeatPumpPreviewPeriod } from "@/lib/controls";
import { useI18n, getCurrentIntlLocales } from "@/lib/i18n";

export default function HeatPumpRuleTimeline({ preview, deviceId }: { preview: HeatPumpPreview; deviceId: number }) {
  const { t } = useI18n("heatPumpRules");
  const chartRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  useEffect(() => {
    const node = chartRef.current;
    if (!node) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(280, entry.contentRect.width)));
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  const [day, setDay] = useState(0);
  const [selection, setSelection] = useState<number | null>(null);
  const pump = preview.pumps.find((value) => value.id === deviceId);
  const dateKey = (value: string) => new Intl.DateTimeFormat("en-CA", {
    timeZone: preview.timezone, year: "numeric", month: "2-digit", day: "2-digit"
  }).format(new Date(value));
  const dates = [...new Set(preview.prices.map((price) => dateKey(price.start)))];
  const prices = preview.prices.filter((price) => dateKey(price.start) === dates[day]);
  if (!pump || prices.length === 0) return <p>{t("captureHint")}</p>;
  const start = Date.parse(prices[0].start);
  const end = Date.parse(prices[prices.length - 1].end);
  const periods = pump.periods.filter((period) => Date.parse(period.start) < end && Date.parse(period.end) > start);
  const x = (time: string | number) => 45 + (Math.max(start, Math.min(end, typeof time === "number" ? time : Date.parse(time))) - start) / (end - start) * (width - 60);
  const priceValues = prices.flatMap((price) => price.price === null ? [] : [price.price]);
  const minPrice = Math.min(0, ...priceValues);
  const maxPrice = Math.max(minPrice + 1, ...priceValues);
  const py = (value: number) => 135 - (value - minPrice) / (maxPrice - minPrice) * 95;
  const temperatures = periods.flatMap((period) => period.state?.temperature == null ? [] : [period.state.temperature]);
  const minTemp = Math.min(16, ...temperatures);
  const maxTemp = Math.max(26, minTemp + 1, ...temperatures);
  const ty = (value: number) => 255 - (value - minTemp) / (maxTemp - minTemp) * 60;
  const time = (value: string | number) => new Intl.DateTimeFormat(getCurrentIntlLocales(), {
    timeZone: preview.timezone, hour: "2-digit", minute: "2-digit", timeZoneName: "shortOffset"
  }).format(new Date(value));
  const summary = (period: HeatPumpPreviewPeriod) => {
    const state = period.state;
    return state == null || state.power === null ? t("unknown") : !state.power ? t("off")
      : `${t("on")}${state.mode ? ` · ${state.mode}` : ""}${state.temperature === null ? "" : ` · ${state.temperature} °C`}`;
  };
  const reason = (period: HeatPumpPreviewPeriod) => {
    switch (period.reason) {
      case "PRICE": return `${period.comparison === "LESS_THAN" ? "<" : ">"} ${period.threshold} snt/kWh`;
      case "SCHEDULE": return t("schedule");
      case "INACTIVE_TIME": return t("outside");
      case "MISSING_PRICE": return t("missingPrices");
      case "DISABLED": return t("disabled");
      case "NO_AC_DATA": return t("noAcData");
      default: return t("noMatch");
    }
  };
  const now = Date.parse(preview.generatedAt);
  const currentIndex = periods.findIndex((period) => Date.parse(period.start) <= now && now < Date.parse(period.end));
  const selected = periods[selection ?? Math.max(0, currentIndex)];
  const axisTime = (value: number) => new Intl.DateTimeFormat(getCurrentIntlLocales(), {
    timeZone: preview.timezone, hour: "2-digit", minute: "2-digit", hour12: false
  }).format(new Date(value));
  const tickCount = width < 520 ? 3 : 5;
  return <div className="space-y-3" ref={chartRef}>
    <div className="flex gap-2" role="group" aria-label={t("preview")}>
      {dates.map((date, index) => <button type="button" className={`secondary-action ${day === index ? "ring-2 ring-primary" : ""}`} key={date}
        aria-pressed={day === index} onClick={() => { setDay(index); setSelection(null); }}>{index === 0 ? t("today") : t("tomorrow")}</button>)}
    </div>
    <p className="text-xs text-on-surface-variant">{dates[day]} · {preview.timezone}</p>
    {pump.competingRules ? <p className="text-sm text-on-surface-variant">{t("competing")}</p> : null}
    <svg viewBox={`0 0 ${width} 310`} className="w-full min-w-0 rounded-xl bg-surface-container" role="img" aria-label={t("preview")}>
      <text x="45" y="22" fontSize="14" fill="currentColor">{t("price")}</text>
      {[minPrice, (maxPrice + minPrice) / 2, maxPrice].map((value) => <g key={value}>
        <line x1="45" x2={width - 15} y1={py(value)} y2={py(value)} stroke="currentColor" opacity="0.12" />
        <text x="37" y={py(value) + 4} textAnchor="end" fontSize="12" fill="currentColor">{value.toFixed(1)}</text>
      </g>)}
      {prices.map((price) => price.price === null ? null : <line key={price.start} x1={x(price.start)} x2={x(price.end)}
        y1={py(price.price)} y2={py(price.price)} stroke="#64748b" strokeWidth="2.5" />)}
      <text x="45" y="158" fontSize="14" fill="currentColor">{t("power")}</text>
      <text x="45" y="192" fontSize="14" fill="currentColor">{t("temperature")}</text>
      {periods.map((period, index) => <g key={period.start} onClick={() => setSelection(index)} style={{ cursor: "pointer" }}>
        <title>{time(period.start)} – {time(period.end)}: {summary(period)} · {reason(period)}{period.assumed ? ` · ${t("assumed")}` : ""}</title>
        <rect x={x(period.start)} y="164" width={Math.max(1, x(period.end) - x(period.start))} height="13"
          fill={period.state?.power == null ? "#94a3b8" : period.state.power ? "#16a34a" : "#475569"}
          opacity={period.assumed ? "0.35" : "0.9"} stroke={selection === index ? "#f59e0b" : "none"} strokeWidth="3" />
        {period.state?.power && period.state.temperature !== null ? <line x1={x(period.start)} x2={x(period.end)}
          y1={ty(period.state.temperature)} y2={ty(period.state.temperature)} stroke="#ea580c" strokeWidth="3"
          strokeDasharray={period.assumed ? "5 4" : undefined} /> : null}
      </g>)}
      {[minTemp, maxTemp].map((value) => <text key={value} x="37" y={ty(value) + 4} textAnchor="end" fontSize="12" fill="currentColor">{value}°</text>)}
      {Array.from({ length: tickCount }, (_, i) => start + (end - start) * i / (tickCount - 1)).map((value) => <text key={value}
        x={x(value)} y="286" textAnchor={value === start ? "start" : value === end ? "end" : "middle"} fontSize="12" fill="currentColor">{axisTime(value)}</text>)}
      {Date.parse(preview.generatedAt) >= start && Date.parse(preview.generatedAt) < end ? <line x1={x(preview.generatedAt)} x2={x(preview.generatedAt)} y1="32" y2="260" stroke="#dc2626" strokeDasharray="4 4" /> : null}
    </svg>
    <div className="flex flex-wrap gap-4 text-xs text-on-surface-variant">
      <span>🟩 {t("on")}</span><span>⬛ {t("off")}</span><span>◻ {t("unknown")}</span><span>⋯ {t("assumed")}</span>
    </div>
    {prices.some((price) => price.price === null) ? <p className="text-sm text-on-surface-variant">{t("missingPrices")}</p> : null}
    {selected ? <p className="rounded-lg border border-primary/30 bg-surface-container p-3 text-sm" aria-live="polite">{time(Math.max(start, Date.parse(selected.start)))} – {time(Math.min(end, Date.parse(selected.end)))} · {summary(selected)} · {reason(selected)}{selected.assumed ? ` · ${t("assumed")}` : ""}</p> : null}
    <details><summary className="cursor-pointer text-sm font-bold">{t("changes")}</summary>
    <div className="mt-2 max-h-48 space-y-1 overflow-y-auto">
      {periods.map((period, index) => <button key={period.start} type="button" onClick={() => setSelection(index)}
        className="block w-full rounded-lg bg-surface-container px-3 py-2 text-left text-xs" aria-pressed={selection === index}>
        <strong>{time(Math.max(start, Date.parse(period.start)))} – {time(Math.min(end, Date.parse(period.end)))} · {summary(period)}</strong>
        <span className="ml-2 text-on-surface-variant">{reason(period)}{period.assumed ? ` · ${t("assumed")}` : ""}</span>
      </button>)}
    </div></details>
  </div>;
}

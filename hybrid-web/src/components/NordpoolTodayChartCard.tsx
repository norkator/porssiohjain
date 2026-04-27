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

import { useNordpoolTodayChart } from "@/hooks/useNordpoolTodayChart";
import { useI18n } from "@/lib/i18n";
import { formatNordpoolPrice, formatNordpoolTime } from "@/lib/nordpool";

const CHART_HEIGHT = 240;
const CHART_WIDTH = 960;
const CHART_PADDING_X = 44;
const CHART_PADDING_Y = 18;
const Y_AXIS_STEPS = 4;

function buildChartPath(values: number[], innerWidth: number, innerHeight: number) {
  if (values.length === 0) {
    return "";
  }

  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue || 1;

  return values
    .map((value, index) => {
      const x = CHART_PADDING_X + (innerWidth * index) / Math.max(values.length - 1, 1);
      const y = CHART_PADDING_Y + innerHeight - ((value - minValue) / range) * innerHeight;

      return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

function buildAreaPath(values: number[], innerWidth: number, innerHeight: number) {
  if (values.length === 0) {
    return "";
  }

  const linePath = buildChartPath(values, innerWidth, innerHeight);
  const lastX = CHART_PADDING_X + innerWidth;
  const baselineY = CHART_PADDING_Y + innerHeight;

  return `${linePath} L ${lastX.toFixed(2)} ${baselineY.toFixed(2)} L ${CHART_PADDING_X} ${baselineY.toFixed(2)} Z`;
}

export default function NordpoolTodayChartCard() {
  const { t } = useI18n("charts");
  const { chart, error, isLoading } = useNordpoolTodayChart();

  if (isLoading) {
    return <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingNordpool")}</div>;
  }

  if (error) {
    return (
      <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
        {t("failedNordpool", { error })}
      </div>
    );
  }

  if (!chart || chart.points.length === 0) {
    return (
      <div className="app-card p-6 text-sm text-on-surface-variant">
        {t("noNordpool")}
      </div>
    );
  }

  const values = chart.points.map((point) => point.price);
  const innerWidth = CHART_WIDTH - CHART_PADDING_X * 2;
  const innerHeight = CHART_HEIGHT - CHART_PADDING_Y * 2;
  const linePath = buildChartPath(values, innerWidth, innerHeight);
  const areaPath = buildAreaPath(values, innerWidth, innerHeight);
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue || 1;
  let currentPointIndex = -1;
  for (let index = chart.points.length - 1; index >= 0; index -= 1) {
    if (new Date(chart.points[index].timestamp).getTime() <= Date.now()) {
      currentPointIndex = index;
      break;
    }
  }
  const currentPoint = currentPointIndex >= 0 ? chart.points[currentPointIndex] : chart.points[0];
  const currentX = CHART_PADDING_X + (innerWidth * Math.max(currentPointIndex, 0)) / Math.max(chart.points.length - 1, 1);
  const currentY = CHART_PADDING_Y + innerHeight - ((currentPoint.price - minValue) / range) * innerHeight;
  const yAxisValues = Array.from({ length: Y_AXIS_STEPS + 1 }, (_, index) => maxValue - (range * index) / Y_AXIS_STEPS);
  const timeLabels = [0, 24, 48, 72, 95]
    .map((index) => chart.points[Math.min(index, chart.points.length - 1)])
    .filter((point, index, points) => points.findIndex((candidate) => candidate.timestamp === point.timestamp) === index);

  return (
    <article className="app-card overflow-hidden">
      <div className="grid grid-cols-1 gap-8 p-6 lg:grid-cols-[minmax(0,1fr)_17rem] lg:p-8">
        <div>
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-primary">{t("nordpoolTitle")}</p>
              <h3 className="font-headline text-3xl font-black tracking-tight text-on-surface">{t("nordpoolHeadline")}</h3>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-on-surface-variant">
                {t("nordpoolDescription", { timezone: chart.timezone })}
              </p>
            </div>

            <div className="rounded-2xl bg-surface-container p-4">
              <p className="metric-label mb-1">{t("current")}</p>
              <p className="font-headline text-3xl font-black text-primary">{formatNordpoolPrice(chart.current)}</p>
              <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
            </div>
          </div>

          <div className="relative overflow-hidden rounded-3xl bg-[linear-gradient(180deg,rgba(0,67,66,0.08),rgba(0,67,66,0.02))] p-4 sm:p-5">
            <svg
              aria-label={t("nordpoolAria")}
              className="h-64 w-full"
              role="img"
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            >
              <defs>
                <linearGradient id="nordpool-area" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="rgb(0 103 125 / 0.35)" />
                  <stop offset="100%" stopColor="rgb(0 103 125 / 0.02)" />
                </linearGradient>
              </defs>

              {yAxisValues.map((value, index) => {
                const y = CHART_PADDING_Y + (innerHeight * index) / Y_AXIS_STEPS;

                return (
                  <g key={value}>
                    <line
                      stroke="rgb(191 200 199 / 0.6)"
                      strokeDasharray="6 8"
                      strokeWidth="1"
                      x1={CHART_PADDING_X}
                      x2={CHART_PADDING_X + innerWidth}
                      y1={y}
                      y2={y}
                    />
                    <text fill="rgb(63 72 72)" fontSize="12" textAnchor="end" x={CHART_PADDING_X - 8} y={y + 4}>
                      {formatNordpoolPrice(value)}
                    </text>
                  </g>
                );
              })}

              {timeLabels.map((point) => {
                const index = chart.points.findIndex((candidate) => candidate.timestamp === point.timestamp);
                const x = CHART_PADDING_X + (innerWidth * index) / Math.max(chart.points.length - 1, 1);

                return (
                  <g key={point.timestamp}>
                    <line
                      stroke="rgb(191 200 199 / 0.5)"
                      strokeWidth="1"
                      x1={x}
                      x2={x}
                      y1={CHART_PADDING_Y}
                      y2={CHART_PADDING_Y + innerHeight}
                    />
                    <text
                      fill="rgb(63 72 72)"
                      fontSize="12"
                      textAnchor={index === chart.points.length - 1 ? "end" : index === 0 ? "start" : "middle"}
                      x={x}
                      y={CHART_PADDING_Y + innerHeight + 20}
                    >
                      {formatNordpoolTime(point.timestamp, chart.timezone)}
                    </text>
                  </g>
                );
              })}

              <path d={areaPath} fill="url(#nordpool-area)" />
              <path d={linePath} fill="none" stroke="rgb(0 67 66)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />

              <line
                stroke="rgb(0 103 125 / 0.45)"
                strokeDasharray="5 7"
                strokeWidth="2"
                x1={currentX}
                x2={currentX}
                y1={CHART_PADDING_Y}
                y2={CHART_PADDING_Y + innerHeight}
              />
              <circle cx={currentX} cy={currentY} fill="rgb(108 221 254)" r="7" stroke="rgb(0 67 66)" strokeWidth="3" />
            </svg>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-on-surface-variant">
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("now", { time: formatNordpoolTime(currentPoint.timestamp, chart.timezone) })}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("resolution", { minutes: chart.resolutionMinutes })}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("range", { min: formatNordpoolPrice(chart.min), max: formatNordpoolPrice(chart.max) })}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-2">
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("minimum")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolPrice(chart.min)}</p>
            <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
          </div>
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("average")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolPrice(chart.avg)}</p>
            <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
          </div>
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("maximum")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolPrice(chart.max)}</p>
            <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
          </div>
          <div className="rounded-3xl bg-primary-container p-5 text-on-primary">
            <p className="metric-label mb-2 text-primary-fixed">{t("dataPoints")}</p>
            <p className="font-headline text-2xl font-black">{chart.points.length}</p>
            <p className="text-xs text-primary-fixed">{t("todayIntervals")}</p>
          </div>
        </div>
      </div>
    </article>
  );
}

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

import { useEffect, useState } from "react";
import { fetchPowerLimitHistory, type PowerLimitHistoryPoint } from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { formatNordpoolTime } from "@/lib/nordpool";

const CHART_HEIGHT = 300;
const CHART_WIDTH = 960;
const CHART_PADDING_LEFT = 58;
const CHART_PADDING_RIGHT = 22;
const CHART_PADDING_TOP = 20;
const CHART_PADDING_BOTTOM = 42;
const Y_AXIS_STEPS = 4;

type PowerLimitHistoryChartCardProps = {
  powerLimitId: number;
  timezone: string;
  limitKw: number | null;
};

type ChartState = {
  error: string | null;
  history: PowerLimitHistoryPoint[];
  isLoading: boolean;
};

function formatKwValue(value: number) {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
}

function buildLinePath(values: number[], innerWidth: number, innerHeight: number, minValue: number, range: number) {
  return values
    .map((value, index) => {
      const x = CHART_PADDING_LEFT + (innerWidth * index) / Math.max(values.length - 1, 1);
      const y = CHART_PADDING_TOP + innerHeight - ((value - minValue) / range) * innerHeight;
      return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

function buildAreaPath(values: number[], innerWidth: number, innerHeight: number, minValue: number, range: number) {
  if (values.length === 0) {
    return "";
  }

  const linePath = buildLinePath(values, innerWidth, innerHeight, minValue, range);
  const startX = CHART_PADDING_LEFT;
  const endX = CHART_PADDING_LEFT + innerWidth;
  const baselineY = CHART_PADDING_TOP + innerHeight;

  return `${linePath} L ${endX.toFixed(2)} ${baselineY.toFixed(2)} L ${startX.toFixed(2)} ${baselineY.toFixed(2)} Z`;
}

function getTimeLabelPoints(history: PowerLimitHistoryPoint[]) {
  const targetLabels = 5;
  const lastIndex = history.length - 1;
  if (lastIndex <= 0) {
    return history;
  }

  const indexes = Array.from({ length: Math.min(targetLabels, history.length) }, (_, index) =>
    Math.round((lastIndex * index) / Math.max(Math.min(targetLabels, history.length) - 1, 1))
  );

  return indexes
    .map((index) => history[index])
    .filter((point, index, points) => points.findIndex((candidate) => candidate.createdAt === point.createdAt) === index);
}

function usePowerLimitHistory(powerLimitId: number): ChartState {
  const { t } = useI18n("charts");
  const [state, setState] = useState<ChartState>({
    error: null,
    history: [],
    isLoading: true
  });

  useEffect(() => {
    let isActive = true;

    setState({
      error: null,
      history: [],
      isLoading: true
    });

    fetchPowerLimitHistory(powerLimitId, 24)
      .then((history) => {
        if (!isActive) return;
        setState({
          error: null,
          history,
          isLoading: false
        });
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        setState({
          error: error instanceof Error ? error.message : t("failedLoadChart"),
          history: [],
          isLoading: false
        });
      });

    return () => {
      isActive = false;
    };
  }, [powerLimitId]);

  return state;
}

export default function PowerLimitHistoryChartCard({ powerLimitId, timezone, limitKw }: PowerLimitHistoryChartCardProps) {
  const { t } = useI18n("charts");
  const { history, error, isLoading } = usePowerLimitHistory(powerLimitId);

  if (isLoading) {
    return <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingPowerLimitHistory")}</div>;
  }

  if (error) {
    return (
      <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
        {t("failedPowerLimitHistory", { error })}
      </div>
    );
  }

  if (history.length === 0) {
    return (
      <div className="app-card p-6 text-sm text-on-surface-variant">
        {t("noPowerLimitHistory")}
      </div>
    );
  }

  const innerWidth = CHART_WIDTH - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
  const innerHeight = CHART_HEIGHT - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const values = history.map((point) => point.kilowatts);
  const allValues = limitKw === null ? values : [...values, limitKw];
  const minValue = Math.min(...allValues, 0);
  const maxValue = Math.max(...allValues);
  const range = maxValue - minValue || 1;
  const yAxisValues = Array.from({ length: Y_AXIS_STEPS + 1 }, (_, index) => maxValue - (range * index) / Y_AXIS_STEPS);
  const timeLabels = getTimeLabelPoints(history);

  let currentPointIndex = -1;
  for (let index = history.length - 1; index >= 0; index -= 1) {
    if (new Date(history[index].createdAt).getTime() <= Date.now()) {
      currentPointIndex = index;
      break;
    }
  }

  const currentIndex = Math.max(currentPointIndex, 0);
  const currentPoint = history[currentIndex];
  const currentX = CHART_PADDING_LEFT + (innerWidth * currentIndex) / Math.max(history.length - 1, 1);
  const currentY = CHART_PADDING_TOP + innerHeight - ((currentPoint.kilowatts - minValue) / range) * innerHeight;
  const limitLineY = limitKw === null ? null : CHART_PADDING_TOP + innerHeight - ((limitKw - minValue) / range) * innerHeight;

  return (
    <article className="app-card overflow-hidden">
      <div className="grid grid-cols-1 gap-8 p-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:p-8">
        <div>
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-primary">{t("powerLimitHistory")}</p>
              <h3 className="font-headline text-3xl font-black tracking-tight text-on-surface">{t("powerUsageTimeline")}</h3>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-on-surface-variant">
                {t("powerLimitDescription", { timezone })}
              </p>
            </div>

            <div className="rounded-2xl bg-surface-container p-4">
              <p className="metric-label mb-1">{t("configuredLimit")}</p>
              <p className="font-headline text-2xl font-black text-primary">{limitKw === null ? "-" : `${formatKwValue(limitKw)} kW`}</p>
            </div>
          </div>

          <div className="relative overflow-hidden rounded-3xl bg-[linear-gradient(180deg,rgba(0,67,66,0.08),rgba(0,67,66,0.02))] p-4 sm:p-5">
            <svg
              aria-label={t("powerLimitAria")}
              className="h-72 w-full"
              role="img"
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            >
              <defs>
                <linearGradient id="power-limit-fill" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="rgb(0 103 125)" stopOpacity="0.28" />
                  <stop offset="100%" stopColor="rgb(0 103 125)" stopOpacity="0.04" />
                </linearGradient>
              </defs>

              <rect
                fill="rgb(247 250 249 / 0.7)"
                height={innerHeight}
                rx="18"
                width={innerWidth}
                x={CHART_PADDING_LEFT}
                y={CHART_PADDING_TOP}
              />

              {yAxisValues.map((value, index) => {
                const y = CHART_PADDING_TOP + (innerHeight * index) / Y_AXIS_STEPS;

                return (
                  <g key={value}>
                    <line
                      stroke="rgb(191 200 199 / 0.6)"
                      strokeDasharray="6 8"
                      strokeWidth="1"
                      x1={CHART_PADDING_LEFT}
                      x2={CHART_PADDING_LEFT + innerWidth}
                      y1={y}
                      y2={y}
                    />
                    <text fill="rgb(63 72 72)" fontSize="12" textAnchor="end" x={CHART_PADDING_LEFT - 10} y={y + 4}>
                      {formatKwValue(value)} kW
                    </text>
                  </g>
                );
              })}

              {timeLabels.map((point) => {
                const index = history.findIndex((candidate) => candidate.createdAt === point.createdAt);
                const x = CHART_PADDING_LEFT + (innerWidth * index) / Math.max(history.length - 1, 1);

                return (
                  <g key={point.createdAt}>
                    <line
                      stroke="rgb(191 200 199 / 0.5)"
                      strokeWidth="1"
                      x1={x}
                      x2={x}
                      y1={CHART_PADDING_TOP}
                      y2={CHART_PADDING_TOP + innerHeight}
                    />
                    <text
                      fill="rgb(63 72 72)"
                      fontSize="12"
                      textAnchor={index === history.length - 1 ? "end" : index === 0 ? "start" : "middle"}
                      x={x}
                      y={CHART_PADDING_TOP + innerHeight + 24}
                    >
                      {formatNordpoolTime(point.createdAt, timezone)}
                    </text>
                  </g>
                );
              })}

              {limitLineY !== null ? (
                <>
                  <line
                    stroke="rgb(204 51 51 / 0.85)"
                    strokeDasharray="8 8"
                    strokeWidth="3"
                    x1={CHART_PADDING_LEFT}
                    x2={CHART_PADDING_LEFT + innerWidth}
                    y1={limitLineY}
                    y2={limitLineY}
                  />
                  <text fill="rgb(204 51 51)" fontSize="12" fontWeight="700" textAnchor="end" x={CHART_PADDING_LEFT + innerWidth - 8} y={limitLineY - 8}>
                    {t("limitLine", { kw: formatKwValue(limitKw ?? 0) })}
                  </text>
                </>
              ) : null}

              <path d={buildAreaPath(values, innerWidth, innerHeight, minValue, range)} fill="url(#power-limit-fill)" stroke="none" />
              <path
                d={buildLinePath(values, innerWidth, innerHeight, minValue, range)}
                fill="none"
                stroke="rgb(0 103 125)"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="4"
              />

              <line
                stroke="rgb(0 103 125 / 0.45)"
                strokeDasharray="5 7"
                strokeWidth="2"
                x1={currentX}
                x2={currentX}
                y1={CHART_PADDING_TOP}
                y2={CHART_PADDING_TOP + innerHeight}
              />
              <circle cx={currentX} cy={currentY} fill="rgb(108 221 254)" r="7" stroke="rgb(0 67 66)" strokeWidth="3" />

              <text fill="rgb(63 72 72)" fontSize="12" fontWeight="700" textAnchor="middle" x={CHART_PADDING_LEFT + innerWidth / 2} y={CHART_HEIGHT - 6}>
                {t("timeInTimezone", { timezone })}
              </text>
            </svg>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-on-surface-variant">
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("now", { time: formatNordpoolTime(currentPoint.createdAt, timezone) })}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("kwRange", { min: formatKwValue(Math.min(...values)), max: formatKwValue(Math.max(...values)) })}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("intervalPoints", { count: history.length })}
            </span>
          </div>

          <div className="mt-4 flex flex-wrap gap-4 rounded-2xl bg-surface-container-low px-4 py-3 text-sm text-on-surface">
            <div className="flex items-center gap-3">
              <span className="h-1 w-8 rounded-full bg-[rgb(0_103_125)]" />
              <span>{t("intervalPowerSum")}</span>
            </div>
            <div className="flex items-center gap-3">
              <span className="h-0.5 w-8 rounded-full bg-[rgb(204_51_51)]" />
              <span>{t("configuredLimit")}</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-1">
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("currentInterval")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatKwValue(currentPoint.kilowatts)}</p>
            <p className="text-xs text-on-surface-variant">{t("kwCurrentBucket")}</p>
          </div>
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("peak24h")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatKwValue(Math.max(...values))}</p>
            <p className="text-xs text-on-surface-variant">{t("highestIntervalSum")}</p>
          </div>
          <div className="rounded-3xl bg-primary-container p-5 text-on-primary">
            <p className="metric-label mb-2 text-primary-fixed">{t("status")}</p>
            <p className="font-headline text-2xl font-black">
              {limitKw !== null && currentPoint.kilowatts > limitKw ? t("aboveLimit") : t("withinLimit")}
            </p>
            <p className="text-xs text-primary-fixed">{t("latestIntervalStatus")}</p>
          </div>
        </div>
      </div>
    </article>
  );
}

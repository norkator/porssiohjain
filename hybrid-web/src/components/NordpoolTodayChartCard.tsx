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
import { formatNordpoolPrice, formatNordpoolTime, type NordpoolTodayChart } from "@/lib/nordpool";
import MarketNotificationsDialog from "@/components/MarketNotificationsDialog";
import AppDialog from "@/components/AppDialog";
import { type PointerEvent, useState } from "react";

const CHART_WIDTH = 960;
const CHART_PADDING_X = 44;
const CHART_PADDING_TOP = 18;
const CHART_PADDING_BOTTOM = 34;
const Y_AXIS_STEPS = 4;

type NordpoolChartSurfaceProps = {
  chart: NordpoolTodayChart;
  chartHeight: number;
  selectedPointIndex?: number | null;
  variant?: "compact" | "expanded";
  onActivate?: () => void;
  onSelectedPointChange?: (index: number | null) => void;
};

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
      const y = CHART_PADDING_TOP + innerHeight - ((value - minValue) / range) * innerHeight;

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
  const baselineY = CHART_PADDING_TOP + innerHeight;

  return `${linePath} L ${lastX.toFixed(2)} ${baselineY.toFixed(2)} L ${CHART_PADDING_X} ${baselineY.toFixed(2)} Z`;
}

function getTimeLabelPoints(chart: NordpoolTodayChart, targetLabels: number) {
  const lastIndex = chart.points.length - 1;
  if (lastIndex <= 0) {
    return chart.points;
  }

  const labelCount = Math.min(targetLabels, chart.points.length);
  const indexes = Array.from({ length: labelCount }, (_, index) =>
    Math.round((lastIndex * index) / Math.max(labelCount - 1, 1))
  );

  return indexes
    .map((index) => chart.points[index])
    .filter((point, index, points) => points.findIndex((candidate) => candidate.timestamp === point.timestamp) === index);
}

function getCurrentPointIndex(chart: NordpoolTodayChart) {
  for (let index = chart.points.length - 1; index >= 0; index -= 1) {
    if (new Date(chart.points[index].timestamp).getTime() <= Date.now()) {
      return index;
    }
  }

  return 0;
}

function getPointCoordinates(values: number[], index: number, innerWidth: number, innerHeight: number) {
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue || 1;
  const value = values[index];

  return {
    x: CHART_PADDING_X + (innerWidth * index) / Math.max(values.length - 1, 1),
    y: CHART_PADDING_TOP + innerHeight - ((value - minValue) / range) * innerHeight
  };
}

function NordpoolChartSurface({
  chart,
  chartHeight,
  onActivate,
  onSelectedPointChange,
  selectedPointIndex,
  variant = "compact"
}: NordpoolChartSurfaceProps) {
  const { t } = useI18n("charts");
  const values = chart.points.map((point) => point.price);
  const innerWidth = CHART_WIDTH - CHART_PADDING_X * 2;
  const innerHeight = chartHeight - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const linePath = buildChartPath(values, innerWidth, innerHeight);
  const areaPath = buildAreaPath(values, innerWidth, innerHeight);
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue || 1;
  const currentPointIndex = getCurrentPointIndex(chart);
  const currentPointCoordinates = getPointCoordinates(values, currentPointIndex, innerWidth, innerHeight);
  const activePointIndex = selectedPointIndex ?? currentPointIndex;
  const activePoint = chart.points[activePointIndex];
  const activePointCoordinates = getPointCoordinates(values, activePointIndex, innerWidth, innerHeight);
  const yAxisValues = Array.from({ length: Y_AXIS_STEPS + 1 }, (_, index) => maxValue - (range * index) / Y_AXIS_STEPS);
  const timeLabels = getTimeLabelPoints(chart, variant === "expanded" ? 9 : 5);
  const activeTooltipX = Math.min(Math.max(activePointCoordinates.x, CHART_PADDING_X + 76), CHART_PADDING_X + innerWidth - 76);
  const activeTooltipY = Math.max(activePointCoordinates.y - 44, CHART_PADDING_TOP + 22);

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    if (!onSelectedPointChange) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    const relativeX = ((event.clientX - rect.left) / rect.width) * CHART_WIDTH;
    const boundedX = Math.min(Math.max(relativeX, CHART_PADDING_X), CHART_PADDING_X + innerWidth);
    const index = Math.round(((boundedX - CHART_PADDING_X) / innerWidth) * Math.max(chart.points.length - 1, 1));
    onSelectedPointChange(Math.min(Math.max(index, 0), chart.points.length - 1));
  }

  return (
    <div
      className={`relative rounded-3xl p-4 sm:p-5 ${onActivate ? "cursor-pointer transition-transform active:scale-[0.99]" : ""}`}
      onClick={onActivate}
      style={{ background: "linear-gradient(180deg, rgb(var(--chart-panel-start)), rgb(var(--chart-panel-end)))" }}
    >
      <div className="-mx-1 overflow-x-auto px-1 pb-2 sm:mx-0 sm:px-0">
        <svg
          aria-label={t("nordpoolAria")}
          className={`h-auto ${variant === "expanded" ? "min-w-[54rem] aspect-[16/7]" : "min-w-[44rem] aspect-[4/1]"} w-full sm:min-w-0`}
          onPointerLeave={() => onSelectedPointChange?.(null)}
          onPointerMove={handlePointerMove}
          role="img"
          viewBox={`0 0 ${CHART_WIDTH} ${chartHeight}`}
        >
          <defs>
            <linearGradient id={`nordpool-area-${variant}`} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="rgb(var(--color-secondary) / 0.35)" />
              <stop offset="100%" stopColor="rgb(var(--color-secondary) / 0.02)" />
            </linearGradient>
          </defs>

          <rect
            fill="rgb(var(--chart-plot-background))"
            height={innerHeight}
            rx="18"
            width={innerWidth}
            x={CHART_PADDING_X}
            y={CHART_PADDING_TOP}
          />

          {yAxisValues.map((value, index) => {
            const y = CHART_PADDING_TOP + (innerHeight * index) / Y_AXIS_STEPS;

            return (
              <g key={value}>
                <line
                  stroke="rgb(var(--color-outline-variant) / 0.6)"
                  strokeDasharray="6 8"
                  strokeWidth="1"
                  x1={CHART_PADDING_X}
                  x2={CHART_PADDING_X + innerWidth}
                  y1={y}
                  y2={y}
                />
                <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor="end" x={CHART_PADDING_X - 8} y={y + 4}>
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
                  stroke="rgb(var(--color-outline-variant) / 0.5)"
                  strokeWidth="1"
                  x1={x}
                  x2={x}
                  y1={CHART_PADDING_TOP}
                  y2={CHART_PADDING_TOP + innerHeight}
                />
                <text
                  fill="rgb(var(--color-on-surface-variant))"
                  fontSize="12"
                  textAnchor={index === chart.points.length - 1 ? "end" : index === 0 ? "start" : "middle"}
                  x={x}
                  y={CHART_PADDING_TOP + innerHeight + 20}
                >
                  {formatNordpoolTime(point.timestamp, chart.timezone)}
                </text>
              </g>
            );
          })}

          <path d={areaPath} fill={`url(#nordpool-area-${variant})`} />
          <path d={linePath} fill="none" stroke="rgb(var(--color-primary))" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />

          <line
            stroke="rgb(var(--color-secondary) / 0.45)"
            strokeDasharray="5 7"
            strokeWidth="2"
            x1={currentPointCoordinates.x}
            x2={currentPointCoordinates.x}
            y1={CHART_PADDING_TOP}
            y2={CHART_PADDING_TOP + innerHeight}
          />
          <circle
            cx={currentPointCoordinates.x}
            cy={currentPointCoordinates.y}
            fill="rgb(var(--color-secondary-container))"
            r="7"
            stroke="rgb(var(--color-primary))"
            strokeWidth="3"
          />

          {variant === "expanded" ? (
            <g>
              <line
                stroke="rgb(204 51 51 / 0.65)"
                strokeDasharray="4 6"
                strokeWidth="2"
                x1={activePointCoordinates.x}
                x2={activePointCoordinates.x}
                y1={CHART_PADDING_TOP}
                y2={CHART_PADDING_TOP + innerHeight}
              />
              <circle
                cx={activePointCoordinates.x}
                cy={activePointCoordinates.y}
                fill="rgb(var(--color-surface-container-lowest))"
                r="8"
                stroke="rgb(204 51 51)"
                strokeWidth="3"
              />
              <rect
                fill="rgb(var(--color-surface-container-lowest))"
                height="42"
                rx="10"
                stroke="rgb(var(--color-outline-variant))"
                width="152"
                x={activeTooltipX - 76}
                y={activeTooltipY - 20}
              />
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" textAnchor="middle" x={activeTooltipX} y={activeTooltipY - 3}>
                {formatNordpoolTime(activePoint.timestamp, chart.timezone)}
              </text>
              <text fill="rgb(var(--color-on-surface))" fontSize="14" fontWeight="800" textAnchor="middle" x={activeTooltipX} y={activeTooltipY + 14}>
                {formatNordpoolPrice(activePoint.price)} snt/kWh
              </text>
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" fontWeight="700" textAnchor="middle" x={CHART_PADDING_X + innerWidth / 2} y={chartHeight - 6}>
                {t("timeInTimezone", { timezone: chart.timezone })}
              </text>
            </g>
          ) : null}
        </svg>
      </div>

      {onActivate ? (
        <span className="absolute right-5 top-5 rounded-full bg-surface-container-lowest/90 px-3 py-1 text-xs font-bold text-primary shadow-sm">
          {t("openDetailedChart")}
        </span>
      ) : null}
    </div>
  );
}

export default function NordpoolTodayChartCard() {
  const { t } = useI18n("charts");
  const { chart, error, isLoading } = useNordpoolTodayChart();
  const [isNotificationDialogOpen, setIsNotificationDialogOpen] = useState(false);
  const [isChartDialogOpen, setIsChartDialogOpen] = useState(false);
  const [selectedPointIndex, setSelectedPointIndex] = useState<number | null>(null);

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

  const currentPointIndex = getCurrentPointIndex(chart);
  const currentPoint = chart.points[currentPointIndex];
  const dialogPointIndex = selectedPointIndex ?? currentPointIndex;
  const dialogPoint = chart.points[dialogPointIndex];

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

          <NordpoolChartSurface
            chart={chart}
            chartHeight={240}
            onActivate={() => setIsChartDialogOpen(true)}
          />

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
            <button
              className="secondary-action justify-center px-4 py-2 text-xs"
              onClick={() => setIsNotificationDialogOpen(true)}
              type="button"
            >
              {t("marketNotifications")}
            </button>
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
          <div className="market-stat-accent">
            <p className="metric-label mb-2 text-primary-fixed">{t("dataPoints")}</p>
            <p className="font-headline text-2xl font-black">{chart.points.length}</p>
            <p className="text-xs text-primary-fixed">{t("todayIntervals")}</p>
          </div>
        </div>
      </div>
      <MarketNotificationsDialog
        isOpen={isNotificationDialogOpen}
        onClose={() => setIsNotificationDialogOpen(false)}
        timezone={chart.timezone}
      />
      <AppDialog
        description={t("nordpoolExpandedDescription", { timezone: chart.timezone })}
        eyebrow={t("nordpoolTitle")}
        isOpen={isChartDialogOpen}
        maxWidthClassName="max-w-6xl"
        onClose={() => {
          setIsChartDialogOpen(false);
          setSelectedPointIndex(null);
        }}
        title={t("nordpoolExpandedTitle")}
      >
        <NordpoolChartSurface
          chart={chart}
          chartHeight={420}
          onSelectedPointChange={setSelectedPointIndex}
          selectedPointIndex={selectedPointIndex}
          variant="expanded"
        />
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedInterval")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">
              {formatNordpoolTime(dialogPoint.timestamp, chart.timezone)}
            </p>
          </div>
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedPrice")}</p>
            <p className="font-headline text-2xl font-black text-primary">
              {formatNordpoolPrice(dialogPoint.price)}
            </p>
            <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
          </div>
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("visibleRange")}</p>
            <p className="font-headline text-lg font-black text-on-surface">
              {formatNordpoolPrice(chart.min)} - {formatNordpoolPrice(chart.max)}
            </p>
            <p className="text-xs text-on-surface-variant">{t("pointsCount", { count: chart.points.length })}</p>
          </div>
        </div>
      </AppDialog>
    </article>
  );
}

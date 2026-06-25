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

import AppDialog from "@/components/AppDialog";
import { type PointerEvent, useEffect, useState } from "react";
import { fetchControlChart, type ControlChart } from "@/lib/controls";
import { useI18n } from "@/lib/i18n";
import { formatNordpoolPrice, formatNordpoolTime } from "@/lib/nordpool";

const CHART_WIDTH = 960;
const CHART_PADDING_LEFT = 58;
const CHART_PADDING_RIGHT = 22;
const CHART_PADDING_TOP = 20;
const CHART_PADDING_BOTTOM = 42;
const Y_AXIS_STEPS = 4;
const COMPACT_CHART_HEIGHT = 300;
const EXPANDED_CHART_HEIGHT = 420;

type ControlPriceChartCardProps = {
  controlId: number;
};

type ChartState = {
  chart: ControlChart | null;
  error: string | null;
  isLoading: boolean;
};

type ControlChartSurfaceProps = {
  chart: ControlChart;
  chartHeight: number;
  selectedPointIndex?: number | null;
  variant?: "compact" | "expanded";
  onActivate?: () => void;
  onSelectedPointChange?: (index: number | null) => void;
};

function buildLinePath(values: Array<number | null>, innerWidth: number, innerHeight: number, minValue: number, range: number) {
  let path = "";
  let segmentOpen = false;

  values.forEach((value, index) => {
    if (value === null) {
      segmentOpen = false;
      return;
    }

    const x = CHART_PADDING_LEFT + (innerWidth * index) / Math.max(values.length - 1, 1);
    const y = CHART_PADDING_TOP + innerHeight - ((value - minValue) / range) * innerHeight;
    path = `${path}${segmentOpen ? " L" : " M"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    segmentOpen = true;
  });

  return path.trim();
}

function formatChartPriceLabel(value: number) {
  return `${formatNordpoolPrice(value)} snt`;
}

function getTimeLabelPoints(chart: ControlChart, targetLabels: number) {
  const lastIndex = chart.points.length - 1;
  if (lastIndex <= 0) {
    return chart.points;
  }

  const indexes = Array.from({ length: Math.min(targetLabels, chart.points.length) }, (_, index) =>
    Math.round((lastIndex * index) / Math.max(Math.min(targetLabels, chart.points.length) - 1, 1))
  );

  return indexes
    .map((index) => chart.points[index])
    .filter((point, index, points) => points.findIndex((candidate) => candidate.timestamp === point.timestamp) === index);
}

function getCurrentPointIndex(chart: ControlChart) {
  for (let index = chart.points.length - 1; index >= 0; index -= 1) {
    if (new Date(chart.points[index].timestamp).getTime() <= Date.now()) {
      return index;
    }
  }

  return 0;
}

function getPointCoordinates(index: number, value: number, innerWidth: number, innerHeight: number, minValue: number, range: number, pointCount: number) {
  return {
    x: CHART_PADDING_LEFT + (innerWidth * index) / Math.max(pointCount - 1, 1),
    y: CHART_PADDING_TOP + innerHeight - ((value - minValue) / range) * innerHeight
  };
}

function useControlChart(controlId: number): ChartState {
  const { t } = useI18n("charts");
  const [state, setState] = useState<ChartState>({
    chart: null,
    error: null,
    isLoading: true
  });

  useEffect(() => {
    let isActive = true;

    setState({
      chart: null,
      error: null,
      isLoading: true
    });

    fetchControlChart(controlId)
      .then((chart) => {
        if (!isActive) {
          return;
        }

        setState({
          chart,
          error: null,
          isLoading: false
        });
      })
      .catch((error: unknown) => {
        if (!isActive) {
          return;
        }

        setState({
          chart: null,
          error: error instanceof Error ? error.message : t("failedLoadChart"),
          isLoading: false
        });
      });

    return () => {
      isActive = false;
    };
  }, [controlId]);

  return state;
}

function ControlChartSurface({
  chart,
  chartHeight,
  onActivate,
  onSelectedPointChange,
  selectedPointIndex,
  variant = "compact"
}: ControlChartSurfaceProps) {
  const { t } = useI18n("charts");
  const innerWidth = CHART_WIDTH - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
  const innerHeight = chartHeight - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const nordpoolValues = chart.points.map((point) => point.nordpoolPrice);
  const transferValues = chart.points.map((point) => point.transferPrice);
  const finalValues = chart.points.map((point) => point.finalControlPrice);
  const allValues = [...nordpoolValues, ...transferValues, ...finalValues].filter((value): value is number => value !== null);
  const minValue = Math.min(...allValues);
  const maxValue = Math.max(...allValues);
  const range = maxValue - minValue || 1;
  const yAxisValues = Array.from({ length: Y_AXIS_STEPS + 1 }, (_, index) => maxValue - (range * index) / Y_AXIS_STEPS);
  const timeLabels = getTimeLabelPoints(chart, variant === "expanded" ? 9 : 5);
  const currentIndex = getCurrentPointIndex(chart);
  const currentPoint = chart.points[currentIndex];
  const currentMarkerValue = currentPoint.finalControlPrice ?? currentPoint.nordpoolPrice;
  const currentCoordinates = getPointCoordinates(currentIndex, currentMarkerValue, innerWidth, innerHeight, minValue, range, chart.points.length);
  const activePointIndex = selectedPointIndex ?? currentIndex;
  const activePoint = chart.points[activePointIndex];
  const activeMarkerValue = activePoint.finalControlPrice ?? activePoint.nordpoolPrice;
  const activeCoordinates = getPointCoordinates(activePointIndex, activeMarkerValue, innerWidth, innerHeight, minValue, range, chart.points.length);
  const activeTooltipX = Math.min(Math.max(activeCoordinates.x, CHART_PADDING_LEFT + 86), CHART_PADDING_LEFT + innerWidth - 86);
  const activeTooltipY = Math.max(activeCoordinates.y - 54, CHART_PADDING_TOP + 34);

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    if (!onSelectedPointChange) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    const relativeX = ((event.clientX - rect.left) / rect.width) * CHART_WIDTH;
    const boundedX = Math.min(Math.max(relativeX, CHART_PADDING_LEFT), CHART_PADDING_LEFT + innerWidth);
    const index = Math.round(((boundedX - CHART_PADDING_LEFT) / innerWidth) * Math.max(chart.points.length - 1, 1));
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
          aria-label={t("controlAria")}
          className={`h-auto ${variant === "expanded" ? "min-w-[54rem] aspect-[16/7]" : "min-w-[44rem] aspect-[16/5]"} w-full sm:min-w-0`}
          onPointerLeave={() => onSelectedPointChange?.(null)}
          onPointerMove={handlePointerMove}
          role="img"
          viewBox={`0 0 ${CHART_WIDTH} ${chartHeight}`}
        >
          <rect fill="rgb(var(--chart-plot-background))" height={innerHeight} rx="18" width={innerWidth} x={CHART_PADDING_LEFT} y={CHART_PADDING_TOP} />

          {yAxisValues.map((value, index) => {
            const y = CHART_PADDING_TOP + (innerHeight * index) / Y_AXIS_STEPS;

            return (
              <g key={value}>
                <line stroke="rgb(var(--color-outline-variant) / 0.6)" strokeDasharray="6 8" strokeWidth="1" x1={CHART_PADDING_LEFT} x2={CHART_PADDING_LEFT + innerWidth} y1={y} y2={y} />
                <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor="end" x={CHART_PADDING_LEFT - 10} y={y + 4}>
                  {formatChartPriceLabel(value)}
                </text>
              </g>
            );
          })}

          {timeLabels.map((point) => {
            const index = chart.points.findIndex((candidate) => candidate.timestamp === point.timestamp);
            const x = CHART_PADDING_LEFT + (innerWidth * index) / Math.max(chart.points.length - 1, 1);

            return (
              <g key={point.timestamp}>
                <line stroke="rgb(var(--color-outline-variant) / 0.5)" strokeWidth="1" x1={x} x2={x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
                <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor={index === chart.points.length - 1 ? "end" : index === 0 ? "start" : "middle"} x={x} y={CHART_PADDING_TOP + innerHeight + 24}>
                  {formatNordpoolTime(point.timestamp, chart.timezone)}
                </text>
              </g>
            );
          })}

          <path d={buildLinePath(transferValues, innerWidth, innerHeight, minValue, range)} fill="none" stroke="rgb(255 179 67)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" />
          <path d={buildLinePath(nordpoolValues, innerWidth, innerHeight, minValue, range)} fill="none" stroke="rgb(var(--color-secondary))" strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" />
          <path d={buildLinePath(finalValues, innerWidth, innerHeight, minValue, range)} fill="none" stroke="rgb(204 51 51)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />

          <line stroke="rgb(var(--color-secondary) / 0.45)" strokeDasharray="5 7" strokeWidth="2" x1={currentCoordinates.x} x2={currentCoordinates.x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
          <circle cx={currentCoordinates.x} cy={currentCoordinates.y} fill="rgb(var(--color-secondary-container))" r="7" stroke="rgb(var(--color-primary))" strokeWidth="3" />

          {variant === "expanded" ? (
            <g>
              <line stroke="rgb(204 51 51 / 0.65)" strokeDasharray="4 6" strokeWidth="2" x1={activeCoordinates.x} x2={activeCoordinates.x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
              <circle cx={activeCoordinates.x} cy={activeCoordinates.y} fill="rgb(var(--color-surface-container-lowest))" r="8" stroke="rgb(204 51 51)" strokeWidth="3" />
              <rect fill="rgb(var(--color-surface-container-lowest))" height="58" rx="10" stroke="rgb(var(--color-outline-variant))" width="172" x={activeTooltipX - 86} y={activeTooltipY - 26} />
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" textAnchor="middle" x={activeTooltipX} y={activeTooltipY - 7}>
                {formatNordpoolTime(activePoint.timestamp, chart.timezone)}
              </text>
              <text fill="rgb(var(--color-on-surface))" fontSize="14" fontWeight="800" textAnchor="middle" x={activeTooltipX} y={activeTooltipY + 10}>
                {activePoint.finalControlPrice === null ? "-" : formatNordpoolPrice(activePoint.finalControlPrice)} snt/kWh
              </text>
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" textAnchor="middle" x={activeTooltipX} y={activeTooltipY + 25}>
                {t("finalControlPrice")}
              </text>
            </g>
          ) : null}

          <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" fontWeight="700" textAnchor="middle" x={CHART_PADDING_LEFT + innerWidth / 2} y={chartHeight - 6}>
            {t("timeInTimezone", { timezone: chart.timezone })}
          </text>
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

export default function ControlPriceChartCard({ controlId }: ControlPriceChartCardProps) {
  const { t } = useI18n("charts");
  const { chart, error, isLoading } = useControlChart(controlId);
  const [isChartDialogOpen, setIsChartDialogOpen] = useState(false);
  const [selectedPointIndex, setSelectedPointIndex] = useState<number | null>(null);

  if (isLoading) {
    return <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingControlChart")}</div>;
  }

  if (error) {
    return (
      <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
        {t("failedControlChart", { error })}
      </div>
    );
  }

  if (!chart || chart.points.length === 0) {
    return (
      <div className="app-card p-6 text-sm text-on-surface-variant">
        {t("noControlChart")}
      </div>
    );
  }

  const nordpoolValues = chart.points.map((point) => point.nordpoolPrice);
  const transferValues = chart.points.map((point) => point.transferPrice);
  const finalValues = chart.points.map((point) => point.finalControlPrice);
  const allValues = [...nordpoolValues, ...transferValues, ...finalValues].filter((value): value is number => value !== null);
  const minValue = Math.min(...allValues);
  const maxValue = Math.max(...allValues);
  const currentIndex = getCurrentPointIndex(chart);
  const currentPoint = chart.points[currentIndex];
  const dialogPointIndex = selectedPointIndex ?? currentIndex;
  const dialogPoint = chart.points[dialogPointIndex];

  return (
    <article className="app-card overflow-hidden">
      <div className="grid grid-cols-1 gap-8 p-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:p-8">
        <div>
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-primary">{t("controlPricing")}</p>
              <h3 className="font-headline text-3xl font-black tracking-tight text-on-surface">{t("controlTimeline")}</h3>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-on-surface-variant">
                {t("controlDescription", { timezone: chart.timezone })}
              </p>
            </div>

            <div className="rounded-2xl bg-surface-container p-4">
              <p className="metric-label mb-1">{t("transferContract")}</p>
              <p className="font-headline text-2xl font-black text-primary">{chart.transferContractName ?? t("notSet")}</p>
            </div>
          </div>

          <ControlChartSurface chart={chart} chartHeight={COMPACT_CHART_HEIGHT} onActivate={() => setIsChartDialogOpen(true)} />

          <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-on-surface-variant">
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("now", { time: formatNordpoolTime(currentPoint.timestamp, chart.timezone) })}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("range", { min: formatNordpoolPrice(minValue), max: formatNordpoolPrice(maxValue) })}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {t("hourlyPoints", { count: chart.points.length })}
            </span>
          </div>

          <div className="mt-4 flex flex-wrap gap-4 rounded-2xl bg-surface-container-low px-4 py-3 text-sm text-on-surface">
            <div className="flex items-center gap-3">
              <span className="h-0.5 w-8 rounded-full bg-[rgb(255_179_67)]" />
              <span>{t("transferPrice")}</span>
            </div>
            <div className="flex items-center gap-3">
              <span className="h-0.5 w-8 rounded-full bg-[rgb(0_103_125)]" />
              <span>{t("nordpoolPrice")}</span>
            </div>
            <div className="flex items-center gap-3">
              <span className="h-1 w-8 rounded-full bg-[rgb(204_51_51)]" />
              <span>{t("finalControlPrice")}</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-1">
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("currentNordpool")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolPrice(currentPoint.nordpoolPrice)}</p>
            <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
          </div>
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("currentTransfer")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">
              {currentPoint.transferPrice === null ? "-" : formatNordpoolPrice(currentPoint.transferPrice)}
            </p>
            <p className="text-xs text-on-surface-variant">{t("priceUnit")}</p>
          </div>
          <div className="rounded-3xl bg-primary-container p-5 text-on-primary">
            <p className="metric-label mb-2 text-primary-fixed">{t("currentFinal")}</p>
            <p className="font-headline text-2xl font-black">
              {currentPoint.finalControlPrice === null ? "-" : formatNordpoolPrice(currentPoint.finalControlPrice)}
            </p>
            <p className="text-xs text-primary-fixed">{t("finalPriceUnit")}</p>
          </div>
        </div>
      </div>
      <AppDialog
        description={t("controlExpandedDescription", { timezone: chart.timezone })}
        eyebrow={t("controlPricing")}
        isOpen={isChartDialogOpen}
        maxWidthClassName="max-w-6xl"
        onClose={() => {
          setIsChartDialogOpen(false);
          setSelectedPointIndex(null);
        }}
        title={t("controlExpandedTitle")}
      >
        <ControlChartSurface
          chart={chart}
          chartHeight={EXPANDED_CHART_HEIGHT}
          onSelectedPointChange={setSelectedPointIndex}
          selectedPointIndex={selectedPointIndex}
          variant="expanded"
        />
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-4">
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedInterval")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolTime(dialogPoint.timestamp, chart.timezone)}</p>
          </div>
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedNordpool")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolPrice(dialogPoint.nordpoolPrice)}</p>
            <p className="text-xs text-on-surface-variant">{t("priceUnitTax")}</p>
          </div>
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedTransfer")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{dialogPoint.transferPrice === null ? "-" : formatNordpoolPrice(dialogPoint.transferPrice)}</p>
            <p className="text-xs text-on-surface-variant">{t("priceUnit")}</p>
          </div>
          <div className="rounded-2xl bg-primary-container p-4 text-on-primary">
            <p className="metric-label mb-2 text-primary-fixed">{t("selectedFinal")}</p>
            <p className="font-headline text-2xl font-black">{dialogPoint.finalControlPrice === null ? "-" : formatNordpoolPrice(dialogPoint.finalControlPrice)}</p>
            <p className="text-xs text-primary-fixed">{t("finalPriceUnit")}</p>
          </div>
        </div>
      </AppDialog>
    </article>
  );
}

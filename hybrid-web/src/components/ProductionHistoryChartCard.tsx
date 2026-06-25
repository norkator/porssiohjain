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
import { fetchProductionHistory, type ProductionHistoryPoint } from "@/lib/automation-resources";
import { useI18n } from "@/lib/i18n";
import { formatNordpoolTime } from "@/lib/nordpool";

const CHART_WIDTH = 960;
const CHART_PADDING_LEFT = 58;
const CHART_PADDING_RIGHT = 22;
const CHART_PADDING_TOP = 20;
const CHART_PADDING_BOTTOM = 42;
const Y_AXIS_STEPS = 4;
const COMPACT_CHART_HEIGHT = 300;
const EXPANDED_CHART_HEIGHT = 420;

type ProductionHistoryChartCardProps = {
  sourceId: number;
  timezone: string;
};

type ChartState = {
  error: string | null;
  history: ProductionHistoryPoint[];
  isLoading: boolean;
};

type ProductionHistoryChartSurfaceProps = {
  chartHeight: number;
  history: ProductionHistoryPoint[];
  selectedPointIndex?: number | null;
  timezone: string;
  variant?: "compact" | "expanded";
  onActivate?: () => void;
  onSelectedPointChange?: (index: number | null) => void;
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

function getTimeLabelPoints(history: ProductionHistoryPoint[], targetLabels: number) {
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

function getCurrentPointIndex(history: ProductionHistoryPoint[]) {
  for (let index = history.length - 1; index >= 0; index -= 1) {
    if (new Date(history[index].createdAt).getTime() <= Date.now()) {
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

function useProductionHistory(sourceId: number): ChartState {
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

    fetchProductionHistory(sourceId, 24)
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
  }, [sourceId]);

  return state;
}

function ProductionHistoryChartSurface({
  chartHeight,
  history,
  onActivate,
  onSelectedPointChange,
  selectedPointIndex,
  timezone,
  variant = "compact"
}: ProductionHistoryChartSurfaceProps) {
  const { t } = useI18n("charts");
  const innerWidth = CHART_WIDTH - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
  const innerHeight = chartHeight - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const values = history.map((point) => point.kilowatts);
  const minValue = Math.min(...values, 0);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue || 1;
  const yAxisValues = Array.from({ length: Y_AXIS_STEPS + 1 }, (_, index) => maxValue - (range * index) / Y_AXIS_STEPS);
  const timeLabels = getTimeLabelPoints(history, variant === "expanded" ? 9 : 5);
  const currentIndex = getCurrentPointIndex(history);
  const currentPoint = history[currentIndex];
  const currentCoordinates = getPointCoordinates(currentIndex, currentPoint.kilowatts, innerWidth, innerHeight, minValue, range, history.length);
  const activePointIndex = selectedPointIndex ?? currentIndex;
  const activePoint = history[activePointIndex];
  const activeCoordinates = getPointCoordinates(activePointIndex, activePoint.kilowatts, innerWidth, innerHeight, minValue, range, history.length);
  const activeTooltipX = Math.min(Math.max(activeCoordinates.x, CHART_PADDING_LEFT + 76), CHART_PADDING_LEFT + innerWidth - 76);
  const activeTooltipY = Math.max(activeCoordinates.y - 44, CHART_PADDING_TOP + 22);

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    if (!onSelectedPointChange) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    const relativeX = ((event.clientX - rect.left) / rect.width) * CHART_WIDTH;
    const boundedX = Math.min(Math.max(relativeX, CHART_PADDING_LEFT), CHART_PADDING_LEFT + innerWidth);
    const index = Math.round(((boundedX - CHART_PADDING_LEFT) / innerWidth) * Math.max(history.length - 1, 1));
    onSelectedPointChange(Math.min(Math.max(index, 0), history.length - 1));
  }

  return (
    <div
      className={`relative rounded-3xl p-4 sm:p-5 ${onActivate ? "cursor-pointer transition-transform active:scale-[0.99]" : ""}`}
      onClick={onActivate}
      style={{ background: "linear-gradient(180deg, rgb(var(--chart-panel-start)), rgb(var(--chart-panel-end)))" }}
    >
      <div className="-mx-1 overflow-x-auto px-1 pb-2 sm:mx-0 sm:px-0">
        <svg
          aria-label={t("productionAria")}
          className={`h-auto ${variant === "expanded" ? "min-w-[54rem] aspect-[16/7]" : "min-w-[44rem] aspect-[16/5]"} w-full sm:min-w-0`}
          onPointerLeave={() => onSelectedPointChange?.(null)}
          onPointerMove={handlePointerMove}
          role="img"
          viewBox={`0 0 ${CHART_WIDTH} ${chartHeight}`}
        >
          <defs>
            <linearGradient id={`production-fill-${variant}`} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="rgb(255 179 67)" stopOpacity="0.30" />
              <stop offset="100%" stopColor="rgb(255 179 67)" stopOpacity="0.05" />
            </linearGradient>
          </defs>

          <rect fill="rgb(var(--chart-plot-background))" height={innerHeight} rx="18" width={innerWidth} x={CHART_PADDING_LEFT} y={CHART_PADDING_TOP} />

          {yAxisValues.map((value, index) => {
            const y = CHART_PADDING_TOP + (innerHeight * index) / Y_AXIS_STEPS;

            return (
              <g key={value}>
                <line stroke="rgb(var(--color-outline-variant) / 0.6)" strokeDasharray="6 8" strokeWidth="1" x1={CHART_PADDING_LEFT} x2={CHART_PADDING_LEFT + innerWidth} y1={y} y2={y} />
                <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor="end" x={CHART_PADDING_LEFT - 10} y={y + 4}>
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
                <line stroke="rgb(var(--color-outline-variant) / 0.5)" strokeWidth="1" x1={x} x2={x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
                <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor={index === history.length - 1 ? "end" : index === 0 ? "start" : "middle"} x={x} y={CHART_PADDING_TOP + innerHeight + 24}>
                  {formatNordpoolTime(point.createdAt, timezone)}
                </text>
              </g>
            );
          })}

          <path d={buildAreaPath(values, innerWidth, innerHeight, minValue, range)} fill={`url(#production-fill-${variant})`} stroke="none" />
          <path d={buildLinePath(values, innerWidth, innerHeight, minValue, range)} fill="none" stroke="rgb(255 179 67)" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />

          <line stroke="rgb(255 179 67 / 0.5)" strokeDasharray="5 7" strokeWidth="2" x1={currentCoordinates.x} x2={currentCoordinates.x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
          <circle cx={currentCoordinates.x} cy={currentCoordinates.y} fill="rgb(255 232 179)" r="7" stroke="rgb(168 110 0)" strokeWidth="3" />

          {variant === "expanded" ? (
            <g>
              <line stroke="rgb(204 51 51 / 0.65)" strokeDasharray="4 6" strokeWidth="2" x1={activeCoordinates.x} x2={activeCoordinates.x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
              <circle cx={activeCoordinates.x} cy={activeCoordinates.y} fill="rgb(var(--color-surface-container-lowest))" r="8" stroke="rgb(204 51 51)" strokeWidth="3" />
              <rect fill="rgb(var(--color-surface-container-lowest))" height="42" rx="10" stroke="rgb(var(--color-outline-variant))" width="152" x={activeTooltipX - 76} y={activeTooltipY - 20} />
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" textAnchor="middle" x={activeTooltipX} y={activeTooltipY - 3}>
                {formatNordpoolTime(activePoint.createdAt, timezone)}
              </text>
              <text fill="rgb(var(--color-on-surface))" fontSize="14" fontWeight="800" textAnchor="middle" x={activeTooltipX} y={activeTooltipY + 14}>
                {formatKwValue(activePoint.kilowatts)} kW
              </text>
            </g>
          ) : null}

          <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" fontWeight="700" textAnchor="middle" x={CHART_PADDING_LEFT + innerWidth / 2} y={chartHeight - 6}>
            {t("timeInTimezone", { timezone })}
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

export default function ProductionHistoryChartCard({ sourceId, timezone }: ProductionHistoryChartCardProps) {
  const { t } = useI18n("charts");
  const { history, error, isLoading } = useProductionHistory(sourceId);
  const [isChartDialogOpen, setIsChartDialogOpen] = useState(false);
  const [selectedPointIndex, setSelectedPointIndex] = useState<number | null>(null);

  if (isLoading) {
    return <div className="app-card p-6 text-sm text-on-surface-variant">{t("loadingProductionHistory")}</div>;
  }

  if (error) {
    return (
      <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
        {t("failedProductionHistory", { error })}
      </div>
    );
  }

  if (history.length === 0) {
    return (
      <div className="app-card p-6 text-sm text-on-surface-variant">
        {t("noProductionHistory")}
      </div>
    );
  }

  const values = history.map((point) => point.kilowatts);
  const currentIndex = getCurrentPointIndex(history);
  const currentPoint = history[currentIndex];
  const dialogPointIndex = selectedPointIndex ?? currentIndex;
  const dialogPoint = history[dialogPointIndex];

  return (
    <article className="app-card overflow-hidden">
      <div className="grid grid-cols-1 gap-8 p-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:p-8">
        <div>
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-primary">{t("productionHistory")}</p>
              <h3 className="font-headline text-3xl font-black tracking-tight text-on-surface">{t("productionTimeline")}</h3>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-on-surface-variant">
                {t("productionDescription", { timezone })}
              </p>
            </div>

            <div className="rounded-2xl bg-surface-container p-4">
              <p className="metric-label mb-1">{t("latestInterval")}</p>
              <p className="font-headline text-2xl font-black text-primary">{formatKwValue(currentPoint.kilowatts)} kW</p>
            </div>
          </div>

          <ProductionHistoryChartSurface
            chartHeight={COMPACT_CHART_HEIGHT}
            history={history}
            onActivate={() => setIsChartDialogOpen(true)}
            timezone={timezone}
          />

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
              <span className="h-1 w-8 rounded-full bg-[rgb(255_179_67)]" />
              <span>{t("averageProduction")}</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-1">
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("currentInterval")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatKwValue(currentPoint.kilowatts)}</p>
            <p className="text-xs text-on-surface-variant">{t("kwAverage")}</p>
          </div>
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">{t("peak24h")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatKwValue(Math.max(...values))}</p>
            <p className="text-xs text-on-surface-variant">{t("highestIntervalValue")}</p>
          </div>
          <div className="rounded-3xl bg-primary-container p-5 text-on-primary">
            <p className="metric-label mb-2 text-primary-fixed">{t("average24h")}</p>
            <p className="font-headline text-2xl font-black">
              {formatKwValue(values.reduce((sum, value) => sum + value, 0) / Math.max(values.length, 1))}
            </p>
            <p className="text-xs text-primary-fixed">{t("kwVisibleRange")}</p>
          </div>
        </div>
      </div>
      <AppDialog
        description={t("productionExpandedDescription", { timezone })}
        eyebrow={t("productionHistory")}
        isOpen={isChartDialogOpen}
        maxWidthClassName="max-w-6xl"
        onClose={() => {
          setIsChartDialogOpen(false);
          setSelectedPointIndex(null);
        }}
        title={t("productionExpandedTitle")}
      >
        <ProductionHistoryChartSurface
          chartHeight={EXPANDED_CHART_HEIGHT}
          history={history}
          onSelectedPointChange={setSelectedPointIndex}
          selectedPointIndex={selectedPointIndex}
          timezone={timezone}
          variant="expanded"
        />
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedInterval")}</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolTime(dialogPoint.createdAt, timezone)}</p>
          </div>
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("selectedProduction")}</p>
            <p className="font-headline text-2xl font-black text-primary">{formatKwValue(dialogPoint.kilowatts)} kW</p>
            <p className="text-xs text-on-surface-variant">{t("kwAverage")}</p>
          </div>
          <div className="rounded-2xl bg-surface-container-low p-4">
            <p className="metric-label mb-2">{t("visibleRange")}</p>
            <p className="font-headline text-lg font-black text-on-surface">
              {formatKwValue(Math.min(...values))} - {formatKwValue(Math.max(...values))} kW
            </p>
            <p className="text-xs text-on-surface-variant">{t("intervalPoints", { count: history.length })}</p>
          </div>
        </div>
      </AppDialog>
    </article>
  );
}

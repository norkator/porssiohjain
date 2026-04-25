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
import { fetchControlChart, type ControlChart } from "@/lib/controls";
import { formatNordpoolPrice, formatNordpoolTime } from "@/lib/nordpool";

const CHART_HEIGHT = 300;
const CHART_WIDTH = 960;
const CHART_PADDING_LEFT = 58;
const CHART_PADDING_RIGHT = 22;
const CHART_PADDING_TOP = 20;
const CHART_PADDING_BOTTOM = 42;
const Y_AXIS_STEPS = 4;

type ControlPriceChartCardProps = {
  controlId: number;
};

type ChartState = {
  chart: ControlChart | null;
  error: string | null;
  isLoading: boolean;
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

function getTimeLabelPoints(chart: ControlChart) {
  const targetLabels = 5;
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

function useControlChart(controlId: number): ChartState {
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
          error: error instanceof Error ? error.message : "Failed to load chart",
          isLoading: false
        });
      });

    return () => {
      isActive = false;
    };
  }, [controlId]);

  return state;
}

export default function ControlPriceChartCard({ controlId }: ControlPriceChartCardProps) {
  const { chart, error, isLoading } = useControlChart(controlId);

  if (isLoading) {
    return <div className="app-card p-6 text-sm text-on-surface-variant">Loading control chart...</div>;
  }

  if (error) {
    return (
      <div className="app-card border border-error-container bg-error-container/50 p-6 text-sm text-on-error-container">
        Failed to load control chart. {error}
      </div>
    );
  }

  if (!chart || chart.points.length === 0) {
    return (
      <div className="app-card p-6 text-sm text-on-surface-variant">
        No control pricing data is available for today.
      </div>
    );
  }

  const innerWidth = CHART_WIDTH - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
  const innerHeight = CHART_HEIGHT - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const nordpoolValues = chart.points.map((point) => point.nordpoolPrice);
  const transferValues = chart.points.map((point) => point.transferPrice);
  const finalValues = chart.points.map((point) => point.finalControlPrice);
  const allValues = [...nordpoolValues, ...transferValues, ...finalValues].filter((value): value is number => value !== null);
  const minValue = Math.min(...allValues);
  const maxValue = Math.max(...allValues);
  const range = maxValue - minValue || 1;
  const yAxisValues = Array.from({ length: Y_AXIS_STEPS + 1 }, (_, index) => maxValue - (range * index) / Y_AXIS_STEPS);
  const timeLabels = getTimeLabelPoints(chart);

  let currentPointIndex = -1;
  for (let index = chart.points.length - 1; index >= 0; index -= 1) {
    if (new Date(chart.points[index].timestamp).getTime() <= Date.now()) {
      currentPointIndex = index;
      break;
    }
  }
  const currentIndex = Math.max(currentPointIndex, 0);
  const currentPoint = chart.points[currentIndex];
  const currentX = CHART_PADDING_LEFT + (innerWidth * currentIndex) / Math.max(chart.points.length - 1, 1);
  const currentMarkerValue = currentPoint.finalControlPrice ?? currentPoint.nordpoolPrice;
  const currentY = CHART_PADDING_TOP + innerHeight - ((currentMarkerValue - minValue) / range) * innerHeight;

  return (
    <article className="app-card overflow-hidden">
      <div className="grid grid-cols-1 gap-8 p-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:p-8">
        <div>
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-primary">Control Pricing</p>
              <h3 className="font-headline text-3xl font-black tracking-tight text-on-surface">Final control timeline</h3>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-on-surface-variant">
                Nord Pool, transfer, and final control price for today in {chart.timezone}.
              </p>
            </div>

            <div className="rounded-2xl bg-surface-container p-4">
              <p className="metric-label mb-1">Transfer Contract</p>
              <p className="font-headline text-2xl font-black text-primary">{chart.transferContractName ?? "Not set"}</p>
            </div>
          </div>

          <div className="relative overflow-hidden rounded-3xl bg-[linear-gradient(180deg,rgba(0,67,66,0.08),rgba(0,67,66,0.02))] p-4 sm:p-5">
            <svg
              aria-label="Control pricing chart for today"
              className="h-72 w-full"
              role="img"
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            >
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
                      textAnchor={index === chart.points.length - 1 ? "end" : index === 0 ? "start" : "middle"}
                      x={x}
                      y={CHART_PADDING_TOP + innerHeight + 24}
                    >
                      {formatNordpoolTime(point.timestamp, chart.timezone)}
                    </text>
                  </g>
                );
              })}

              <path
                d={buildLinePath(transferValues, innerWidth, innerHeight, minValue, range)}
                fill="none"
                stroke="rgb(255 179 67)"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="3"
              />
              <path
                d={buildLinePath(nordpoolValues, innerWidth, innerHeight, minValue, range)}
                fill="none"
                stroke="rgb(0 103 125)"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="3"
              />
              <path
                d={buildLinePath(finalValues, innerWidth, innerHeight, minValue, range)}
                fill="none"
                stroke="rgb(204 51 51)"
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
                Time in {chart.timezone}
              </text>
            </svg>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-on-surface-variant">
            <span className="rounded-full bg-surface-container px-3 py-2">
              Now {formatNordpoolTime(currentPoint.timestamp, chart.timezone)}
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              Range {formatNordpoolPrice(minValue)} - {formatNordpoolPrice(maxValue)} snt/kWh
            </span>
            <span className="rounded-full bg-surface-container px-3 py-2">
              {chart.points.length} hourly points
            </span>
          </div>

          <div className="mt-4 flex flex-wrap gap-4 rounded-2xl bg-surface-container-low px-4 py-3 text-sm text-on-surface">
            <div className="flex items-center gap-3">
              <span className="h-0.5 w-8 rounded-full bg-[rgb(255_179_67)]" />
              <span>Transfer price</span>
            </div>
            <div className="flex items-center gap-3">
              <span className="h-0.5 w-8 rounded-full bg-[rgb(0_103_125)]" />
              <span>Nord Pool price</span>
            </div>
            <div className="flex items-center gap-3">
              <span className="h-1 w-8 rounded-full bg-[rgb(204_51_51)]" />
              <span>Final control price</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-1">
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">Current Nord Pool</p>
            <p className="font-headline text-2xl font-black text-on-surface">{formatNordpoolPrice(currentPoint.nordpoolPrice)}</p>
            <p className="text-xs text-on-surface-variant">snt/kWh incl. tax</p>
          </div>
          <div className="rounded-3xl bg-surface-container-low p-5">
            <p className="metric-label mb-2">Current Transfer</p>
            <p className="font-headline text-2xl font-black text-on-surface">
              {currentPoint.transferPrice === null ? "-" : formatNordpoolPrice(currentPoint.transferPrice)}
            </p>
            <p className="text-xs text-on-surface-variant">snt/kWh</p>
          </div>
          <div className="rounded-3xl bg-primary-container p-5 text-on-primary">
            <p className="metric-label mb-2 text-primary-fixed">Current Final</p>
            <p className="font-headline text-2xl font-black">
              {currentPoint.finalControlPrice === null ? "-" : formatNordpoolPrice(currentPoint.finalControlPrice)}
            </p>
            <p className="text-xs text-primary-fixed">FINAL control price</p>
          </div>
        </div>
      </div>
    </article>
  );
}

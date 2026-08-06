import AppDialog from "@/components/AppDialog";
import { type PointerEvent, useEffect, useMemo, useState } from "react";

export type ThermostatCurvePoint = { price: number; temperature: number };

type Props = {
  curveJson: string;
  isOpen: boolean;
  labels: Record<string, string>;
  onClose: () => void;
  onSave: (curveJson: string) => void;
};

const DEFAULT_POINTS: ThermostatCurvePoint[] = [
  { price: 0, temperature: 22 },
  { price: 10, temperature: 21 },
  { price: 20, temperature: 19 }
];
const CHART_WIDTH = 920;
const CHART_HEIGHT = 420;
const CHART_PADDING_LEFT = 70;
const CHART_PADDING_RIGHT = 30;
const CHART_PADDING_TOP = 28;
const CHART_PADDING_BOTTOM = 62;
const AXIS_STEPS = 5;

function parseCurve(value: string): ThermostatCurvePoint[] {
  const parsed: unknown = JSON.parse(value);
  if (!Array.isArray(parsed) || parsed.length === 0) throw new Error("empty");
  const points = parsed.map((point) => {
    const item = point as Partial<ThermostatCurvePoint>;
    if (!Number.isFinite(item.price) || !Number.isFinite(item.temperature)) throw new Error("invalid");
    return { price: Number(item.price), temperature: Number(item.temperature) };
  });
  if (new Set(points.map((point) => point.price)).size !== points.length) throw new Error("duplicate");
  return points.sort((a, b) => a.price - b.price);
}

function stringify(points: ThermostatCurvePoint[]) {
  return JSON.stringify([...points].sort((a, b) => a.price - b.price).map((point) => ({
    price: Number(point.price.toFixed(2)),
    temperature: Number(point.temperature.toFixed(2))
  })), null, 2);
}

function getStepValues(minValue: number, maxValue: number) {
  return Array.from({ length: AXIS_STEPS + 1 }, (_, index) => minValue + ((maxValue - minValue) * index) / AXIS_STEPS);
}

function formatNumber(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

export default function ThermostatCurveDialog({ curveJson, isOpen, labels, onClose, onSave }: Props) {
  const [points, setPoints] = useState<ThermostatCurvePoint[]>(DEFAULT_POINTS);
  const [json, setJson] = useState(stringify(DEFAULT_POINTS));
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    try {
      const next = parseCurve(curveJson || stringify(DEFAULT_POINTS));
      setPoints(next);
      setJson(stringify(next));
    } catch {
      setPoints(DEFAULT_POINTS);
      setJson(stringify(DEFAULT_POINTS));
    }
    setSelectedIndex(null);
    setDraggingIndex(null);
    setError(null);
  }, [curveJson, isOpen]);

  const bounds = useMemo(() => {
    const prices = points.map((point) => point.price);
    const temperatures = points.map((point) => point.temperature);
    const minPrice = Math.min(0, ...prices);
    const maxPrice = Math.max(20, ...prices);
    const minTemperature = Math.min(15, ...temperatures);
    const maxTemperature = Math.max(25, ...temperatures);
    const pricePadding = Math.max(2, (maxPrice - minPrice) * 0.12);
    const temperaturePadding = Math.max(1, (maxTemperature - minTemperature) * 0.12);

    return {
      maxPrice: maxPrice + pricePadding,
      maxTemperature: maxTemperature + temperaturePadding,
      minPrice: 0,
      minTemperature: minTemperature - temperaturePadding
    };
  }, [points]);
  const innerWidth = CHART_WIDTH - CHART_PADDING_LEFT - CHART_PADDING_RIGHT;
  const innerHeight = CHART_HEIGHT - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const priceRange = bounds.maxPrice - bounds.minPrice || 1;
  const temperatureRange = bounds.maxTemperature - bounds.minTemperature || 1;
  const x = (price: number) => CHART_PADDING_LEFT + ((price - bounds.minPrice) / priceRange) * innerWidth;
  const y = (temperature: number) => CHART_PADDING_TOP + innerHeight - ((temperature - bounds.minTemperature) / temperatureRange) * innerHeight;
  const priceTicks = getStepValues(bounds.minPrice, bounds.maxPrice);
  const temperatureTicks = getStepValues(bounds.minTemperature, bounds.maxTemperature);
  const linePath = points.map((point, index) => `${index === 0 ? "M" : "L"} ${x(point.price).toFixed(2)} ${y(point.temperature).toFixed(2)}`).join(" ");

  const updatePoints = (next: ThermostatCurvePoint[], selectedPoint?: ThermostatCurvePoint) => {
    const sorted = [...next].sort((a, b) => a.price - b.price);
    setPoints(sorted);
    setJson(stringify(sorted));
    if (selectedPoint) {
      setSelectedIndex(sorted.findIndex((point) => point.price === selectedPoint.price && point.temperature === selectedPoint.temperature));
    }
    setError(null);
    return sorted;
  };

  const getPointFromEvent = (event: PointerEvent<SVGSVGElement>) => {
    const svg = event.currentTarget;
    const rect = svg.getBoundingClientRect();
    const px = ((event.clientX - rect.left) / rect.width) * CHART_WIDTH;
    const py = ((event.clientY - rect.top) / rect.height) * CHART_HEIGHT;
    const boundedX = Math.max(CHART_PADDING_LEFT, Math.min(CHART_PADDING_LEFT + innerWidth, px));
    const boundedY = Math.max(CHART_PADDING_TOP, Math.min(CHART_PADDING_TOP + innerHeight, py));
    const price = Math.max(0, bounds.minPrice + ((boundedX - CHART_PADDING_LEFT) / innerWidth) * priceRange);
    const temperature = bounds.minTemperature + ((CHART_PADDING_TOP + innerHeight - boundedY) / innerHeight) * temperatureRange;

    return {
      price: Number(price.toFixed(1)),
      temperature: Number(temperature.toFixed(1))
    };
  };

  const dragPoint = (index: number, event: PointerEvent<SVGSVGElement>) => {
    const point = getPointFromEvent(event);
    const next = [...points];
    next[index] = point;
    const sorted = updatePoints(next, point);
    setDraggingIndex(sorted.findIndex((candidate) => candidate.price === point.price && candidate.temperature === point.temperature));
  };

  const applyJson = () => {
    try {
      updatePoints(parseCurve(json));
    } catch {
      setError(labels.invalidJson);
    }
  };

  const updatePointValue = (index: number, field: keyof ThermostatCurvePoint, value: string) => {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      setError(labels.invalidJson);
      return;
    }

    const next = [...points];
    const point = { ...next[index], [field]: parsed };
    next[index] = point;
    updatePoints(next, point);
  };

  return (
    <AppDialog description={labels.instructions} isOpen={isOpen} maxWidthClassName="max-w-5xl" onClose={onClose} title={labels.title}>
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <button className="secondary-action px-4 py-2 text-sm" onClick={() => { const last = points[points.length - 1]; updatePoints([...points, { price: (last?.price ?? -5) + 5, temperature: last?.temperature ?? 21 }]); }} type="button">{labels.addPoint}</button>
          <button className="secondary-action px-4 py-2 text-sm disabled:opacity-50" disabled={selectedIndex === null || points.length <= 1} onClick={() => { if (selectedIndex !== null) updatePoints(points.filter((_, index) => index !== selectedIndex)); setSelectedIndex(null); }} type="button">{labels.removePoint}</button>
          <button className="secondary-action px-4 py-2 text-sm" onClick={() => updatePoints(DEFAULT_POINTS)} type="button">{labels.reset}</button>
        </div>
        <div className="rounded-3xl p-3 sm:p-4" style={{ background: "linear-gradient(180deg, rgb(var(--chart-panel-start)), rgb(var(--chart-panel-end)))" }}>
          <div className="-mx-1 overflow-x-auto px-1 pb-2 sm:mx-0 sm:px-0">
            <svg
              aria-label={labels.title}
              className="aspect-[16/7] h-auto min-w-[46rem] touch-none select-none sm:min-w-0"
              onPointerCancel={() => setDraggingIndex(null)}
              onPointerLeave={() => setDraggingIndex(null)}
              onPointerMove={(event) => {
                if (draggingIndex !== null) {
                  dragPoint(draggingIndex, event);
                }
              }}
              onPointerUp={(event) => {
                if (event.currentTarget.hasPointerCapture(event.pointerId)) {
                  event.currentTarget.releasePointerCapture(event.pointerId);
                }
                setDraggingIndex(null);
              }}
              role="img"
              viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            >
              <rect fill="rgb(var(--chart-plot-background))" height={innerHeight} rx="18" width={innerWidth} x={CHART_PADDING_LEFT} y={CHART_PADDING_TOP} />

              {temperatureTicks.map((value) => {
                const tickY = y(value);

                return (
                  <g key={`temperature-${value}`}>
                    <line stroke="rgb(var(--color-outline-variant) / 0.6)" strokeDasharray="6 8" strokeWidth="1" x1={CHART_PADDING_LEFT} x2={CHART_PADDING_LEFT + innerWidth} y1={tickY} y2={tickY} />
                    <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" fontWeight="700" textAnchor="end" x={CHART_PADDING_LEFT - 12} y={tickY + 4}>
                      {formatNumber(value)} C
                    </text>
                  </g>
                );
              })}

              {priceTicks.map((value) => {
                const tickX = x(value);

                return (
                  <g key={`price-${value}`}>
                    <line stroke="rgb(var(--color-outline-variant) / 0.48)" strokeWidth="1" x1={tickX} x2={tickX} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP + innerHeight} />
                    <text fill="rgb(var(--color-on-surface-variant))" fontSize="12" fontWeight="700" textAnchor="middle" x={tickX} y={CHART_PADDING_TOP + innerHeight + 26}>
                      {formatNumber(value)}
                    </text>
                  </g>
                );
              })}

              <path d={linePath} fill="none" stroke="rgb(var(--color-primary))" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />

              {points.map((point, index) => {
                const pointX = x(point.price);
                const pointY = y(point.temperature);
                const selected = selectedIndex === index;
                const labelY = Math.max(CHART_PADDING_TOP + 16, pointY - 18);

                return (
                  <g key={`${point.price}-${point.temperature}-${index}`}>
                    <line stroke={selected ? "rgb(204 51 51 / 0.62)" : "rgb(var(--color-primary) / 0.22)"} strokeDasharray="4 6" strokeWidth="2" x1={pointX} x2={pointX} y1={pointY} y2={CHART_PADDING_TOP + innerHeight} />
                    <circle
                      aria-label={`${formatNumber(point.price)} snt/kWh, ${formatNumber(point.temperature)} C`}
                      cx={pointX}
                      cy={pointY}
                      fill={selected ? "rgb(var(--color-surface-container-lowest))" : "rgb(var(--color-secondary-container))"}
                      onPointerDown={(event) => {
                        event.preventDefault();
                        event.currentTarget.ownerSVGElement?.setPointerCapture(event.pointerId);
                        setSelectedIndex(index);
                        setDraggingIndex(index);
                      }}
                      r={selected ? 11 : 9}
                      role="button"
                      stroke={selected ? "rgb(204 51 51)" : "rgb(var(--color-primary))"}
                      strokeWidth="3"
                      tabIndex={0}
                    />
                    <text fill="rgb(var(--color-on-surface))" fontSize="12" fontWeight="800" pointerEvents="none" textAnchor="middle" x={pointX} y={labelY}>
                      {formatNumber(point.temperature)} C
                    </text>
                    <text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" pointerEvents="none" textAnchor="middle" x={pointX} y={labelY + 15}>
                      {formatNumber(point.price)} snt/kWh
                    </text>
                  </g>
                );
              })}

              <text fill="rgb(var(--color-on-surface-variant))" fontSize="13" fontWeight="800" textAnchor="middle" x={CHART_PADDING_LEFT + innerWidth / 2} y={CHART_HEIGHT - 14}>{labels.priceAxis}</text>
              <text fill="rgb(var(--color-on-surface-variant))" fontSize="13" fontWeight="800" textAnchor="middle" transform={`rotate(-90 18 ${CHART_PADDING_TOP + innerHeight / 2})`} x="18" y={CHART_PADDING_TOP + innerHeight / 2}>{labels.temperatureAxis}</text>
            </svg>
          </div>
        </div>
        <div className="rounded-2xl bg-surface-container p-3 sm:p-4">
          <p className="metric-label mb-3">{labels.pointTable}</p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[28rem] border-separate border-spacing-y-2 text-left text-sm">
              <thead>
                <tr className="text-on-surface-variant">
                  <th className="px-2 pb-1 font-headline text-xs font-bold uppercase tracking-[0.16em]">{labels.price}</th>
                  <th className="px-2 pb-1 font-headline text-xs font-bold uppercase tracking-[0.16em]">{labels.temperature}</th>
                </tr>
              </thead>
              <tbody>
                {points.map((point, index) => (
                  <tr key={`row-${index}`}>
                    <td className="px-2">
                      <input
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-2 text-on-surface outline-none focus:border-primary"
                        onChange={(event) => updatePointValue(index, "price", event.target.value)}
                        onFocus={() => setSelectedIndex(index)}
                        step="1"
                        type="number"
                        value={point.price}
                      />
                    </td>
                    <td className="px-2">
                      <input
                        className="w-full rounded-t-lg border-none border-b-2 border-transparent bg-surface-container-highest px-3 py-2 text-on-surface outline-none focus:border-primary"
                        onChange={(event) => updatePointValue(index, "temperature", event.target.value)}
                        onFocus={() => setSelectedIndex(index)}
                        step="0.5"
                        type="number"
                        value={point.temperature}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <label className="block font-headline text-sm font-bold text-on-surface" htmlFor="thermostat-curve-json">{labels.curveJson}</label>
        <textarea className="min-h-48 w-full rounded-xl bg-surface-container-highest p-4 font-mono text-xs text-on-surface outline-none focus:ring-2 focus:ring-primary" id="thermostat-curve-json" onBlur={applyJson} onChange={(event) => setJson(event.target.value)} value={json} />
        {error ? <p className="text-sm text-on-error-container">{error}</p> : null}
        <div className="flex justify-end gap-3">
          <button className="secondary-action" onClick={onClose} type="button">{labels.cancel}</button>
          <button className="primary-action" onClick={() => { try { const normalized = stringify(parseCurve(json)); onSave(normalized); } catch { setError(labels.invalidJson); } }} type="button">{labels.save}</button>
        </div>
      </div>
    </AppDialog>
  );
}

import AppDialog from "@/components/AppDialog";
import { PointerEvent, useEffect, useMemo, useState } from "react";

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

export default function ThermostatCurveDialog({ curveJson, isOpen, labels, onClose, onSave }: Props) {
  const [points, setPoints] = useState<ThermostatCurvePoint[]>(DEFAULT_POINTS);
  const [json, setJson] = useState(stringify(DEFAULT_POINTS));
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
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
    setError(null);
  }, [curveJson, isOpen]);

  const bounds = useMemo(() => {
    const prices = points.map((point) => point.price);
    const temperatures = points.map((point) => point.temperature);
    return {
      maxPrice: Math.max(20, ...prices) + 5,
      maxTemperature: Math.max(25, ...temperatures) + 2,
      minPrice: Math.min(0, ...prices),
      minTemperature: Math.min(15, ...temperatures) - 2
    };
  }, [points]);
  const width = 760;
  const height = 340;
  const pad = 48;
  const x = (price: number) => pad + ((price - bounds.minPrice) / (bounds.maxPrice - bounds.minPrice)) * (width - pad * 2);
  const y = (temperature: number) => height - pad - ((temperature - bounds.minTemperature) / (bounds.maxTemperature - bounds.minTemperature)) * (height - pad * 2);

  const updatePoints = (next: ThermostatCurvePoint[]) => {
    const sorted = [...next].sort((a, b) => a.price - b.price);
    setPoints(sorted);
    setJson(stringify(sorted));
    setError(null);
  };

  const dragPoint = (index: number, event: PointerEvent<SVGCircleElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    const svg = event.currentTarget.ownerSVGElement;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const px = ((event.clientX - rect.left) / rect.width) * width;
    const py = ((event.clientY - rect.top) / rect.height) * height;
    const price = bounds.minPrice + ((Math.max(pad, Math.min(width - pad, px)) - pad) / (width - pad * 2)) * (bounds.maxPrice - bounds.minPrice);
    const temperature = bounds.minTemperature + ((height - pad - Math.max(pad, Math.min(height - pad, py))) / (height - pad * 2)) * (bounds.maxTemperature - bounds.minTemperature);
    const next = [...points];
    next[index] = { price: Number(price.toFixed(1)), temperature: Number(temperature.toFixed(1)) };
    updatePoints(next);
    setSelectedIndex(next.sort((a, b) => a.price - b.price).findIndex((point) => point.price === Number(price.toFixed(1))));
  };

  const applyJson = () => {
    try {
      updatePoints(parseCurve(json));
    } catch {
      setError(labels.invalidJson);
    }
  };

  return (
    <AppDialog description={labels.instructions} isOpen={isOpen} maxWidthClassName="max-w-5xl" onClose={onClose} title={labels.title}>
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <button className="secondary-action px-4 py-2 text-sm" onClick={() => { const last = points[points.length - 1]; updatePoints([...points, { price: (last?.price ?? -5) + 5, temperature: last?.temperature ?? 21 }]); }} type="button">{labels.addPoint}</button>
          <button className="secondary-action px-4 py-2 text-sm disabled:opacity-50" disabled={selectedIndex === null || points.length <= 1} onClick={() => { if (selectedIndex !== null) updatePoints(points.filter((_, index) => index !== selectedIndex)); setSelectedIndex(null); }} type="button">{labels.removePoint}</button>
          <button className="secondary-action px-4 py-2 text-sm" onClick={() => updatePoints(DEFAULT_POINTS)} type="button">{labels.reset}</button>
        </div>
        <div className="overflow-x-auto rounded-xl bg-surface-container p-2">
          <svg className="min-w-[640px] touch-none" role="img" viewBox={`0 0 ${width} ${height}`}>
            <line stroke="currentColor" strokeOpacity=".25" x1={pad} x2={width - pad} y1={height - pad} y2={height - pad} />
            <line stroke="currentColor" strokeOpacity=".25" x1={pad} x2={pad} y1={pad} y2={height - pad} />
            <polyline fill="none" points={points.map((point) => `${x(point.price)},${y(point.temperature)}`).join(" ")} stroke="currentColor" strokeWidth="3" />
            {points.map((point, index) => <circle aria-label={`${point.price}, ${point.temperature}`} cx={x(point.price)} cy={y(point.temperature)} fill={selectedIndex === index ? "#ef6c00" : "#1976d2"} key={`${point.price}-${index}`} onPointerDown={() => setSelectedIndex(index)} onPointerMove={(event) => { if (event.currentTarget.hasPointerCapture(event.pointerId)) dragPoint(index, event); }} r="8" />)}
            <text fill="currentColor" fontSize="12" textAnchor="middle" x={width / 2} y={height - 8}>{labels.priceAxis}</text>
            <text fill="currentColor" fontSize="12" transform={`rotate(-90 14 ${height / 2})`} textAnchor="middle" x="14" y={height / 2}>{labels.temperatureAxis}</text>
          </svg>
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

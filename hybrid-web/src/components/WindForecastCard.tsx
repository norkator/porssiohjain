import { FormEvent, type PointerEvent, useEffect, useState } from "react";
import AppDialog from "@/components/AppDialog";
import { getCurrentTimezone } from "@/lib/add-device-flow";
import { useI18n } from "@/lib/i18n";
import { formatNordpoolTime } from "@/lib/nordpool";
import { createWindNotification, deleteWindNotification, fetchWindForecast, fetchWindNotifications, updateWindNotification, type WindForecast, type WindNotification, type WindRuleType } from "@/lib/wind";

const CHART_WIDTH = 960;
const COMPACT_CHART_HEIGHT = 240;
const EXPANDED_CHART_HEIGHT = 420;
const CHART_PADDING_LEFT = 86;
const CHART_PADDING_RIGHT = 22;
const CHART_PADDING_TOP = 18;
const CHART_PADDING_BOTTOM = 34;
const Y_AXIS_STEPS = 4;
const number = (value: number | null, digits=0) => value == null ? "–" : new Intl.NumberFormat(undefined,{maximumFractionDigits:digits}).format(value);

type WindChartSurfaceProps = {
  chartHeight: number;
  data: WindForecast;
  selectedPointIndex?: number | null;
  variant?: "compact" | "expanded";
  onActivate?: () => void;
  onSelectedPointChange?: (index: number | null) => void;
};

function formatMw(value: number) {
  return `${number(value)} MW`;
}

function getTimestamp(point: WindForecast["points"][number]) {
  return new Date(point.startTime).getTime();
}

function getX(timestamp: number, startTimestamp: number, endTimestamp: number, innerWidth: number) {
  const range = endTimestamp - startTimestamp || 1;
  return CHART_PADDING_LEFT + ((timestamp - startTimestamp) / range) * innerWidth;
}

function getY(value: number, minValue: number, range: number, innerHeight: number) {
  return CHART_PADDING_TOP + innerHeight - ((value - minValue) / range) * innerHeight;
}

function buildLinePath(points: WindForecast["points"], minValue: number, range: number, innerWidth: number, innerHeight: number) {
  if (points.length === 0) {
    return "";
  }

  const startTimestamp = getTimestamp(points[0]);
  const endTimestamp = getTimestamp(points[points.length - 1]);

  return points
    .map((point, index) => {
      const x = getX(getTimestamp(point), startTimestamp, endTimestamp, innerWidth);
      const y = getY(point.megawatts, minValue, range, innerHeight);
      return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

function buildAreaPath(points: WindForecast["points"], minValue: number, range: number, innerWidth: number, innerHeight: number) {
  if (points.length === 0) {
    return "";
  }

  const linePath = buildLinePath(points, minValue, range, innerWidth, innerHeight);
  const startX = CHART_PADDING_LEFT;
  const endX = CHART_PADDING_LEFT + innerWidth;
  const baselineY = CHART_PADDING_TOP + innerHeight;

  return `${linePath} L ${endX.toFixed(2)} ${baselineY.toFixed(2)} L ${startX.toFixed(2)} ${baselineY.toFixed(2)} Z`;
}

function getTimeLabelPoints(points: WindForecast["points"], targetLabels: number) {
  const lastIndex = points.length - 1;
  if (lastIndex <= 0) {
    return points;
  }

  const labelCount = Math.min(targetLabels, points.length);
  const indexes = Array.from({ length: labelCount }, (_, index) =>
    Math.round((lastIndex * index) / Math.max(labelCount - 1, 1))
  );

  return indexes
    .map((index) => points[index])
    .filter((point, index, selectedPoints) => selectedPoints.findIndex((candidate) => candidate.startTime === point.startTime) === index);
}

function getCurrentMarker(points: WindForecast["points"], minValue: number, range: number, innerWidth: number, innerHeight: number) {
  if (points.length === 0) {
    return null;
  }

  const now = Date.now();
  const startTimestamp = getTimestamp(points[0]);
  const endTimestamp = getTimestamp(points[points.length - 1]);
  if (now < startTimestamp || now > endTimestamp) {
    return null;
  }

  let previous = points[0];
  let next = points[points.length - 1];
  for (let index = 1; index < points.length; index += 1) {
    if (getTimestamp(points[index]) >= now) {
      previous = points[index - 1];
      next = points[index];
      break;
    }
  }

  const previousTimestamp = getTimestamp(previous);
  const nextTimestamp = getTimestamp(next);
  const progress = nextTimestamp === previousTimestamp ? 0 : (now - previousTimestamp) / (nextTimestamp - previousTimestamp);
  const value = previous.megawatts + (next.megawatts - previous.megawatts) * progress;

  return {
    x: getX(now, startTimestamp, endTimestamp, innerWidth),
    y: getY(value, minValue, range, innerHeight)
  };
}

function getPointCoordinates(point: WindForecast["points"][number], minValue: number, range: number, innerWidth: number, innerHeight: number, points: WindForecast["points"]) {
  return {
    x: getX(getTimestamp(point), getTimestamp(points[0]), getTimestamp(points[points.length - 1]), innerWidth),
    y: getY(point.megawatts, minValue, range, innerHeight)
  };
}

function formatDayLabel(timestamp: string, timezone: string) {
  return new Intl.DateTimeFormat(undefined, {
    day: "numeric",
    month: "short",
    timeZone: timezone,
    weekday: "short"
  }).format(new Date(timestamp));
}

function getDayKey(timestamp: string, timezone: string) {
  return new Intl.DateTimeFormat("sv-SE", {
    day: "2-digit",
    month: "2-digit",
    timeZone: timezone,
    year: "numeric"
  }).format(new Date(timestamp));
}

function getDayBands(points: WindForecast["points"], timezone: string, innerWidth: number) {
  if (points.length === 0) {
    return [];
  }

  const startTimestamp = getTimestamp(points[0]);
  const endTimestamp = getTimestamp(points[points.length - 1]);
  const bands: { dayKey: string; label: string; startX: number; endX: number }[] = [];

  points.forEach((point) => {
    const dayKey = getDayKey(point.startTime, timezone);
    const lastBand = bands[bands.length - 1];

    if (!lastBand || lastBand.dayKey !== dayKey) {
      bands.push({
        dayKey,
        label: formatDayLabel(point.startTime, timezone),
        startX: getX(getTimestamp(point), startTimestamp, endTimestamp, innerWidth),
        endX: CHART_PADDING_LEFT + innerWidth
      });
    }
  });

  return bands.map((band, index) => ({
    ...band,
    endX: bands[index + 1]?.startX ?? CHART_PADDING_LEFT + innerWidth
  }));
}

function WindChartSurface({ chartHeight, data, onActivate, onSelectedPointChange, selectedPointIndex, variant = "compact" }: WindChartSurfaceProps) {
  const windT = useI18n("windForecast").t;
  const chartT = useI18n("charts").t;
  const values = data.points.map(p => p.megawatts), min = Math.min(...values, 0), max = Math.max(...values, 0), range = max - min || 1;
  const innerWidth = CHART_WIDTH - CHART_PADDING_LEFT - CHART_PADDING_RIGHT, innerHeight = chartHeight - CHART_PADDING_TOP - CHART_PADDING_BOTTOM;
  const yAxisValues = Array.from({length:Y_AXIS_STEPS+1},(_,index)=>max-(range*index)/Y_AXIS_STEPS);
  const timeLabels = getTimeLabelPoints(data.points, variant === "expanded" ? 9 : 6);
  const dayBands = getDayBands(data.points, data.timezone, innerWidth);
  const currentMarker = getCurrentMarker(data.points, min, range, innerWidth, innerHeight);
  const activePointIndex = selectedPointIndex ?? null;
  const activePoint = activePointIndex === null ? null : data.points[activePointIndex];
  const activePointCoordinates = activePoint ? getPointCoordinates(activePoint, min, range, innerWidth, innerHeight, data.points) : null;
  const activeTooltipX = activePointCoordinates ? Math.min(Math.max(activePointCoordinates.x, CHART_PADDING_LEFT + 76), CHART_PADDING_LEFT + innerWidth - 76) : 0;
  const activeTooltipY = activePointCoordinates ? Math.max(activePointCoordinates.y - 44, CHART_PADDING_TOP + 22) : 0;

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    if (!onSelectedPointChange) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    const relativeX = ((event.clientX - rect.left) / rect.width) * CHART_WIDTH;
    const boundedX = Math.min(Math.max(relativeX, CHART_PADDING_LEFT), CHART_PADDING_LEFT + innerWidth);
    const index = Math.round(((boundedX - CHART_PADDING_LEFT) / innerWidth) * Math.max(data.points.length - 1, 1));
    onSelectedPointChange(Math.min(Math.max(index, 0), data.points.length - 1));
  }

  return <div className={`relative rounded-3xl p-4 sm:p-5 ${onActivate ? "cursor-pointer transition-transform active:scale-[0.99]" : ""}`} onClick={onActivate} style={{ background: "linear-gradient(180deg, rgb(var(--chart-panel-start)), rgb(var(--chart-panel-end)))" }}>
    <div className="-mx-1 overflow-x-auto px-1 pb-2 sm:mx-0 sm:px-0">
      <svg className={`h-auto ${variant === "expanded" ? "min-w-[54rem] aspect-[16/7]" : "min-w-[44rem] aspect-[4/1]"} w-full sm:min-w-0`} onPointerLeave={()=>onSelectedPointChange?.(null)} onPointerMove={handlePointerMove} viewBox={`0 0 ${CHART_WIDTH} ${chartHeight}`} role="img" aria-label={windT("aria")}>
        <defs><linearGradient id={`wind-forecast-fill-${variant}`} x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="rgb(var(--color-secondary) / 0.35)"/><stop offset="100%" stopColor="rgb(var(--color-secondary) / 0.02)"/></linearGradient></defs>
        <rect fill="rgb(var(--chart-plot-background))" height={innerHeight} rx="18" width={innerWidth} x={CHART_PADDING_LEFT} y={CHART_PADDING_TOP}/>
        {dayBands.map((band,index)=><g key={band.dayKey}>{index%2===1?<rect fill="rgb(var(--color-primary) / 0.055)" height={innerHeight} width={Math.max(band.endX-band.startX,0)} x={band.startX} y={CHART_PADDING_TOP}/>:null}{index>0?<line stroke="rgb(var(--color-primary) / 0.42)" strokeDasharray="4 6" strokeWidth="1.5" x1={band.startX} x2={band.startX} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP+innerHeight}/>:null}<text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" textAnchor="start" x={band.startX+8} y={CHART_PADDING_TOP+18}>{band.label}</text></g>)}
        {yAxisValues.map((value,index)=>{const y=CHART_PADDING_TOP+(innerHeight*index)/Y_AXIS_STEPS;return <g key={`${value}-${index}`}><line stroke="rgb(var(--color-outline-variant) / 0.6)" strokeDasharray="6 8" strokeWidth="1" x1={CHART_PADDING_LEFT} x2={CHART_PADDING_LEFT+innerWidth} y1={y} y2={y}/><text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor="end" x={CHART_PADDING_LEFT-10} y={y+4}>{formatMw(value)}</text></g>})}
        {timeLabels.map((point)=>{const index=data.points.findIndex(candidate=>candidate.startTime===point.startTime);const x=getX(getTimestamp(point),getTimestamp(data.points[0]),getTimestamp(data.points[data.points.length-1]),innerWidth);return <g key={point.startTime}><line stroke="rgb(var(--color-outline-variant) / 0.45)" strokeWidth="1" x1={x} x2={x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP+innerHeight}/><text fill="rgb(var(--color-on-surface-variant))" fontSize="12" textAnchor={index===data.points.length-1?"end":index===0?"start":"middle"} x={x} y={CHART_PADDING_TOP+innerHeight+20}>{formatNordpoolTime(point.startTime,data.timezone)}</text></g>})}
        <path d={buildAreaPath(data.points,min,range,innerWidth,innerHeight)} fill={`url(#wind-forecast-fill-${variant})`} stroke="none"/>
        <path d={buildLinePath(data.points,min,range,innerWidth,innerHeight)} fill="none" stroke="rgb(var(--color-primary))" strokeLinecap="round" strokeLinejoin="round" strokeWidth="4"/>
        {currentMarker?<><line stroke="rgb(var(--color-secondary) / 0.45)" strokeDasharray="5 7" strokeWidth="2" x1={currentMarker.x} x2={currentMarker.x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP+innerHeight}/><circle cx={currentMarker.x} cy={currentMarker.y} fill="rgb(var(--color-secondary-container))" r="7" stroke="rgb(var(--color-primary))" strokeWidth="3"/></>:null}
        {variant==="expanded"&&activePoint&&activePointCoordinates?<g><line stroke="rgb(204 51 51 / 0.65)" strokeDasharray="4 6" strokeWidth="2" x1={activePointCoordinates.x} x2={activePointCoordinates.x} y1={CHART_PADDING_TOP} y2={CHART_PADDING_TOP+innerHeight}/><circle cx={activePointCoordinates.x} cy={activePointCoordinates.y} fill="rgb(var(--color-surface-container-lowest))" r="8" stroke="rgb(204 51 51)" strokeWidth="3"/><rect fill="rgb(var(--color-surface-container-lowest))" height="42" rx="10" stroke="rgb(var(--color-outline-variant))" width="152" x={activeTooltipX-76} y={activeTooltipY-20}/><text fill="rgb(var(--color-on-surface-variant))" fontSize="11" fontWeight="700" textAnchor="middle" x={activeTooltipX} y={activeTooltipY-3}>{formatNordpoolTime(activePoint.startTime,data.timezone)}</text><text fill="rgb(var(--color-on-surface))" fontSize="14" fontWeight="800" textAnchor="middle" x={activeTooltipX} y={activeTooltipY+14}>{formatMw(activePoint.megawatts)}</text></g>:null}
      </svg>
    </div>
    {onActivate ? <span className="absolute right-5 top-5 rounded-full bg-surface-container-lowest/90 px-3 py-1 text-xs font-bold text-primary shadow-sm">{chartT("openDetailedChart")}</span> : null}
  </div>;
}

export default function WindForecastCard() {
  const { t } = useI18n("windForecast"); const timezone=getCurrentTimezone();
  const [data,setData]=useState<WindForecast|null>(null), [error,setError]=useState<string|null>(null), [open,setOpen]=useState(false), [chartOpen,setChartOpen]=useState(false), [selectedPointIndex,setSelectedPointIndex]=useState<number|null>(null);
  useEffect(()=>{ fetchWindForecast(timezone).then(setData).catch(e=>setError(e instanceof Error?e.message:String(e))); },[timezone]);
  if(error) return <div className="app-card p-6 text-on-error-container">{t("failed")}: {error}</div>;
  if(!data) return <div className="app-card p-6 text-on-surface-variant">{t("loading")}</div>;
  const values=data.points.map(p=>p.megawatts);
  return <article className="app-card overflow-hidden">
    <div className="grid grid-cols-1 gap-6 p-4 sm:p-5 lg:grid-cols-[minmax(0,1fr)_17rem]">
      <div>
        <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-primary">{t("eyebrow")}</p><h3 className="font-headline text-3xl font-black">{t("title")}</h3><p className="mt-2 text-sm text-on-surface-variant">{t("description",{timezone:data.timezone})}</p></div><button className="secondary-action justify-center px-4 py-2 text-sm" onClick={()=>setOpen(true)}>{t("notifications")}</button></div>
        {values.length ? <WindChartSurface chartHeight={COMPACT_CHART_HEIGHT} data={data} onActivate={()=>setChartOpen(true)}/> : <p>{t("empty")}</p>}
      </div>
      <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-1"><Stat label={t("todayAverage")} value={`${number(data.todayAverage)} MW`}/><Stat label={t("tomorrowAverage")} value={`${number(data.tomorrowAverage)} MW`}/><Stat label={t("drop")} value={data.tomorrowDropPercent==null?"–":`${number(data.tomorrowDropPercent,1)} %`}/></div>
    </div>
    <WindDialog open={open} close={()=>setOpen(false)} timezone={timezone}/>
    <AppDialog description={t("expandedDescription",{timezone:data.timezone})} eyebrow={t("eyebrow")} isOpen={chartOpen} maxWidthClassName="max-w-6xl" onClose={()=>{setChartOpen(false);setSelectedPointIndex(null)}} title={t("expandedTitle")}>
      <WindChartSurface chartHeight={EXPANDED_CHART_HEIGHT} data={data} onSelectedPointChange={setSelectedPointIndex} selectedPointIndex={selectedPointIndex} variant="expanded"/>
      <div className="mt-4 grid gap-3 sm:grid-cols-3"><Stat label={t("todayAverage")} value={`${number(data.todayAverage)} MW`}/><Stat label={t("tomorrowAverage")} value={`${number(data.tomorrowAverage)} MW`}/><Stat label={t("drop")} value={data.tomorrowDropPercent==null?"–":`${number(data.tomorrowDropPercent,1)} %`}/></div>
    </AppDialog>
  </article>;
}
function Stat({label,value}:{label:string,value:string}) { return <div className="rounded-3xl bg-surface-container-low p-4"><p className="metric-label mb-2">{label}</p><p className="font-headline text-2xl font-black">{value}</p></div>; }
function WindDialog({open,close,timezone}:{open:boolean;close:()=>void;timezone:string}) {
 const {t}=useI18n("windNotifications"), common=useI18n("common").t;
 const [items,setItems]=useState<WindNotification[]>([]), [editing,setEditing]=useState<WindNotification|null>(null), [name,setName]=useState(""), [description,setDescription]=useState(""), [rule,setRule]=useState<WindRuleType>("TOMORROW_AVERAGE_ABOVE"), [threshold,setThreshold]=useState("3000"), [error,setError]=useState<string|null>(null);
 useEffect(()=>{if(open) fetchWindNotifications().then(setItems).catch(e=>setError(String(e)));},[open]);
 if(!open)return null;
 const reset=()=>{setEditing(null);setName("");setDescription("");setRule("TOMORROW_AVERAGE_ABOVE");setThreshold("3000")};
 const edit=(n:WindNotification)=>{setEditing(n);setName(n.name);setDescription(n.description??"");setRule(n.ruleType);setThreshold(String(n.threshold))};
  const thresholdLabel=rule==="TOMORROW_DROP_PERCENT"?t("percentThreshold"):rule==="TOMORROW_AVERAGE_ABOVE"?t("mwAboveThreshold"):t("mwBelowThreshold");
 async function remove(id:number){try{await deleteWindNotification(id);setItems(await fetchWindNotifications());if(editing?.id===id)reset()}catch(x){setError(String(x))}}
 async function submit(e:FormEvent){e.preventDefault();try{const payload={name:name.trim(),description:description.trim()||null,ruleType:rule,threshold:Number(threshold),timezone,enabled:true};const saved=editing?await updateWindNotification(editing.id,payload):await createWindNotification(payload);setItems(old=>editing?old.map(x=>x.id===saved.id?saved:x):[...old,saved]);reset()}catch(x){setError(String(x))}}
 return <AppDialog isOpen={open} onClose={close} eyebrow={t("eyebrow")} title={t("title")} description={t("description")} maxWidthClassName="max-w-5xl">
  <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
   <div className="space-y-3">{items.length===0?<p className="rounded-xl bg-surface-container p-4 text-sm">{t("empty")}</p>:items.map(n=><article key={n.id} className="rounded-xl bg-surface-container p-4"><h4 className="font-headline text-lg font-bold">{n.name}</h4><p className="mt-1 text-sm text-on-surface-variant">{t(n.ruleType,{threshold:number(n.threshold,1)})}</p><div className="mt-3 flex gap-2"><button className="secondary-action px-3 py-2 text-xs" onClick={()=>edit(n)} type="button">{common("edit")}</button><button className="secondary-action px-3 py-2 text-xs" onClick={()=>remove(n.id)} type="button">{common("remove")}</button></div></article>)}</div>
   <form className="space-y-4 rounded-xl bg-surface-container p-4" onSubmit={submit}>
    <h4 className="font-headline text-xl font-bold">{editing?t("edit"):t("add")}</h4>
    <input required className="w-full rounded-t-lg bg-surface-container-highest px-4 py-3" placeholder={t("name")} value={name} onChange={e=>setName(e.target.value)}/>
    <textarea className="w-full rounded-xl bg-surface-container-highest px-4 py-3" placeholder={t("descriptionLabel")} value={description} onChange={e=>setDescription(e.target.value)}/>
    <select className="w-full rounded-t-lg bg-surface-container-highest px-3 py-3" value={rule} onChange={e=>setRule(e.target.value as WindRuleType)}>
     <option value="TOMORROW_AVERAGE_ABOVE">{t("highAverageRule")}</option>
     <option value="TOMORROW_AVERAGE_BELOW">{t("lowAverageRule")}</option>
     <option value="TOMORROW_DROP_PERCENT">{t("dropRule")}</option>
    </select>
    <label className="block text-sm font-bold">{thresholdLabel}<input min="0" required type="number" step="0.1" className="mt-2 w-full rounded-t-lg bg-surface-container-highest px-4 py-3" value={threshold} onChange={e=>setThreshold(e.target.value)}/></label>
    <button className="primary-action w-full justify-center" type="submit">{common("save")}</button>
    {editing?<button className="secondary-action w-full justify-center" type="button" onClick={reset}>{common("cancel")}</button>:null}
   </form>
  </div>
  {error?<p className="mt-4 text-on-error-container">{error}</p>:null}
 </AppDialog>;
}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */

import { useEffect, useRef, useState } from "react";
import PageHeader from "@/components/PageHeader";
import AppDialog from "@/components/AppDialog";
import {
  fetchMonitor,
  savePlanner,
  type Monitor,
  type PlanPoint,
  type ComfortSchedule,
} from "@/lib/heating-planner";

const actionLabels: Record<string, string> = {
  PREHEAT: "Preheat floor",
  DISCHARGE: "Use stored heat",
  COMFORT_RECOVERY: "Protect room comfort",
  NORMAL: "Maintain normal temperature",
  INACTIVE: "Heating optimization inactive",
};

const inputClass =
  "w-full rounded-lg bg-surface-container-high p-3 text-on-surface";
const series = [
  ["room", "Predicted room", "#e88330"],
  ["floor", "Predicted floor", "#b078dc"],
  ["floorSetpoint", "Planned floor setpoint", "#db5368"],
  ["heatPumpSetpoint", "Planned heat-pump setpoint", "#159c88"],
  ["target", "Comfort target", "#798537"],
  ["outdoor", "Outdoor forecast", "#3989da"],
  ["price", "Price (c/kWh, right axis)", "#89939e"],
  ["wood", "Predicted wood heat effect (right axis)", "#af663d"],
] as const;
function Chart({ data, points }: { data: Monitor; points: PlanPoint[] }) {
  const host = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(940);
  useEffect(() => {
    const observer = new ResizeObserver((entries) =>
      setWidth(Math.max(280, entries[0].contentRect.width)),
    );
    if (host.current) observer.observe(host.current);
    return () => observer.disconnect();
  }, []);
  const plotWidth = width - 100;
  const right = width - 45;
  const ticks = width < 600 ? 5 : 7;
  const [selected, setSelected] = useState<PlanPoint | null>(null);
  const [hidden, setHidden] = useState<string[]>([]);
  const start = Date.parse(data.start),
    end = Date.parse(data.end);
  const x = (time: string | number) =>
    55 +
    ((typeof time === "number" ? time - start : Date.parse(time) - start) /
      (end - start)) *
      plotWidth;
  const temps = points
    .flatMap((p) =>
      series
        .filter(([key]) => key !== "price" && key !== "wood")
        .map(([key]) => p[key]),
    )
    .filter((v): v is number => v !== null);
  const low = Math.floor(Math.min(0, ...temps) / 5) * 5,
    high = Math.ceil(Math.max(30, ...temps) / 5) * 5;
  const prices = points
    .flatMap((p) => [p.price, p.wood])
    .filter((v): v is number => v !== null);
  const priceLow = Math.min(0, ...prices),
    priceHigh = Math.max(1, ...prices);
  const y = (v: number, price: boolean) =>
    265 -
    ((v - (price ? priceLow : low)) /
      (price ? priceHigh - priceLow : high - low)) *
      220;
  const format = (time: string | number) =>
    new Date(time).toLocaleString(undefined, {
      timeZone: data.summary.timezone,
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  return (
    <>
      <div ref={host}>
        <svg
          className="w-full"
          viewBox={`0 0 ${width} 310`}
          role="img"
          aria-label="Today and tomorrow Heating Planner predictions"
        >
          <rect
            x="55"
            y="25"
            width={plotWidth}
            height="240"
            fill="rgb(var(--chart-plot-background))"
          />
          <rect
            x={x(data.tomorrow)}
            y="25"
            width={right - x(data.tomorrow)}
            height="240"
            fill="rgb(var(--color-primary) / 0.09)"
          />
          <text x="65" y="40" fill="currentColor" fontSize="12">
            Today
          </text>
          <text
            x={x(data.tomorrow) + 10}
            y="40"
            fill="currentColor"
            fontSize="12"
          >
            Tomorrow
          </text>
          {Array.from({ length: 5 }, (_, i) => (
            <g key={i}>
              <line
                x1="55"
                x2={right}
                y1={45 + i * 55}
                y2={45 + i * 55}
                stroke="currentColor"
                opacity=".15"
              />
              <text
                x="48"
                y={49 + i * 55}
                textAnchor="end"
                fill="currentColor"
                fontSize="11"
              >
                {(high - ((high - low) * i) / 4).toFixed(0)}°
              </text>
              <text
                x={right + 8}
                y={49 + i * 55}
                fill="currentColor"
                fontSize="11"
              >
                {(priceHigh - ((priceHigh - priceLow) * i) / 4).toFixed(1)}
              </text>
            </g>
          ))}
          {series
            .filter(([key]) => !hidden.includes(key))
            .map(([key, , color]) => {
              let previous: number | null = null;
              const path = points
                .map((p) => {
                  const v = p[key];
                  if (v === null) {
                    previous = null;
                    return "";
                  }
                  const time = Date.parse(p.time);
                  const command =
                    previous === null || time - previous > 900000 ? "M" : "L";
                  previous = time;
                  return `${command}${x(p.time)},${y(v, key === "price" || key === "wood")}`;
                })
                .join(" ");
              return (
                <path
                  key={key}
                  d={path}
                  fill="none"
                  stroke={color}
                  strokeWidth="2"
                  strokeDasharray={key === "target" ? "5 4" : undefined}
                />
              );
            })}
          {Date.now() >= start && Date.now() < end && (
            <line
              x1={x(Date.now())}
              x2={x(Date.now())}
              y1="25"
              y2="265"
              stroke="currentColor"
              strokeDasharray="3 4"
            />
          )}
          {Array.from({ length: ticks }, (_, i) => {
            const time = start + ((end - start) * i) / (ticks - 1);
            return (
              <text
                key={i}
                x={x(time)}
                y="288"
                fill="currentColor"
                fontSize="10"
                textAnchor={
                  i === 0 ? "start" : i === ticks - 1 ? "end" : "middle"
                }
              >
                {new Date(time).toLocaleTimeString(undefined, {
                  timeZone: data.summary.timezone,
                  hour: "2-digit",
                  minute: "2-digit",
                  hour12: false,
                })}
              </text>
            );
          })}
          {points.map((p) => (
            <rect
              key={p.time}
              x={x(p.time)}
              y="25"
              width={Math.max(2, (plotWidth * 900000) / (end - start))}
              height="240"
              fill="transparent"
              tabIndex={0}
              aria-label={format(p.time)}
              onFocus={() => setSelected(p)}
              onMouseEnter={() => setSelected(p)}
              onClick={() => setSelected(p)}
            >
              <title>
                {format(p.time)} — {p.reason}
              </title>
            </rect>
          ))}
        </svg>
      </div>
      <div className="flex flex-wrap gap-3">
        {series.map(([key, label, color]) => (
          <button
            key={key}
            type="button"
            className="text-xs"
            aria-pressed={!hidden.includes(key)}
            onClick={() =>
              setHidden((h) =>
                h.includes(key) ? h.filter((k) => k !== key) : [...h, key],
              )
            }
            style={{ color, opacity: hidden.includes(key) ? 0.4 : 1 }}
          >
            ● {label}
          </button>
        ))}
      </div>
      {selected && (
        <p className="mt-3 text-sm">
          {format(selected.time)} ·{" "}
          {series
            .map(([key, label]) =>
              selected[key] === null ? null : `${label}: ${selected[key]}`,
            )
            .filter(Boolean)
            .join(" · ")}
          <br />
          {selected.reason}
        </p>
      )}
    </>
  );
}
export default function HeatingPlannerView() {
  const [data, setData] = useState<Monitor | null>(null),
    [error, setError] = useState("");
  const [roomId, setRoomId] = useState<number | null>(null),
    [editing, setEditing] = useState(false),
    [saving, setSaving] = useState(false);
  const [target, setTarget] = useState("21"),
    [scheduleId, setScheduleId] = useState("");
  const [schedule, setSchedule] = useState<ComfortSchedule | null>(null),
    [dialogError, setDialogError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    const load = () =>
      fetchMonitor(controller.signal)
        .then((d) => {
          setData(d);
          setError("");
        })
        .catch((e) => {
          if (!controller.signal.aborted) setError(e.message);
        });
    void load();
    const timer = window.setInterval(load, 60000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, []);
  const room = data?.rooms.find((r) => r.id === roomId) ?? data?.rooms[0];
  const time = (value: string) =>
    new Date(value).toLocaleString(undefined, {
      timeZone: data?.summary.timezone,
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  const open = () => {
    if (!room) return;
    setTarget(String(room.target));
    setScheduleId(room.scheduleId === null ? "" : String(room.scheduleId));
    setSchedule(null);
    setDialogError("");
    setEditing(true);
  };
  async function save() {
    if (!room) return;
    setSaving(true);
    setDialogError("");
    try {
      if (schedule) await savePlanner("comfort-schedules", schedule);
      else
        await savePlanner(`rooms/${room.id}/comfort`, {
          target: Number(target),
          scheduleId: scheduleId ? Number(scheduleId) : null,
        });
      setData(await fetchMonitor());
      setEditing(false);
    } catch (e) {
      setDialogError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }
  const actions =
    room?.points.filter(
      (p, i, list) =>
        i === 0 ||
        p.mode !== list[i - 1].mode ||
        p.floorSetpoint !== list[i - 1].floorSetpoint ||
        p.heatPumpSetpoint !== list[i - 1].heatPumpSetpoint ||
        Date.parse(p.time) - Date.parse(list[i - 1].time) > 900000,
    ) ?? [];
  const wood = data?.summary.nextWoodRecommendation;
  return (
    <>
      <PageHeader title="Heating Planner" />
      <main className="mx-auto max-w-7xl space-y-6 px-4 py-6 sm:px-6">
        {error && (
          <p role="alert">
            {error} — Configure Heating Planner in the desktop, then reload to
            retry.
          </p>
        )}
        {!data && !error && <p>Loading Heating Planner…</p>}
        {data && (
          <>
            <div>
              <h1 className="text-2xl font-bold">{data.summary.site.name}</h1>
              <p className="text-sm text-on-surface-variant">
                Default site from the desktop · {data.summary.timezone} ·{" "}
                {data.summary.status.code.replace(/_/g, " ")}
              </p>
            </div>
            {!room ? (
              <p>Configure rooms in the desktop Heating Planner first.</p>
            ) : (
              <>
                <section className="app-card space-y-4 p-4 sm:p-6">
                  <label className="block">
                    Room
                    <select
                      className={inputClass}
                      value={room.id}
                      onChange={(e) => setRoomId(Number(e.target.value))}
                    >
                      {data.rooms.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p className="text-sm text-on-surface-variant">
                    Today and tomorrow · Temperatures are predictions; setpoints
                    are planned requests, not thermostat read-back.
                    {data.summary.latestPlan &&
                      ` Plan generated ${time(data.summary.latestPlan.generatedAt)}.`}
                  </p>
                  {data.summary.coverage.tomorrowAvailability !==
                    "COMPLETE" && (
                    <p>
                      Tomorrow’s plan is{" "}
                      {data.summary.coverage.tomorrowAvailability.toLowerCase()}
                      .
                    </p>
                  )}
                  {data.summary.automation.lastAutomationError && (
                    <p role="status">
                      {data.summary.automation.lastAutomationError}
                    </p>
                  )}
                  {room.points.length ? (
                    <Chart key={room.id} data={data} points={room.points} />
                  ) : (
                    <p>
                      No saved plan available. Calculate a plan in the desktop
                      Heating Planner.
                    </p>
                  )}
                </section>
                <section className="app-card p-4 sm:p-6">
                  <h2 className="mb-4 text-xl font-bold">Planned actions</h2>
                  <div className="divide-y divide-outline-variant">
                    {actions.map((p) => (
                      <article className="py-3" key={p.time}>
                        <p className="font-bold">
                          {time(p.time)} ·{" "}
                          {actionLabels[p.mode] ?? p.mode.replace(/_/g, " ")}
                        </p>
                        <p className="text-sm">
                          {p.floorSetpoint !== null &&
                            `Floor request ${p.floorSetpoint} °C. `}
                          {p.heatPumpSetpoint !== null &&
                            `Heat-pump request ${p.heatPumpSetpoint} °C. `}
                          {p.reason}
                        </p>
                      </article>
                    ))}
                    {wood?.roomId === room.id && (
                      <article className="py-3">
                        <p className="font-bold">
                          {time(wood.notifyAt)} · Light wood stove (advisory)
                        </p>
                        <p>{wood.reason}</p>
                      </article>
                    )}
                    {!actions.length && <p>No planned actions available.</p>}
                  </div>
                </section>
                <button className="primary-action px-5 py-3" onClick={open}>
                  Edit room comfort
                </button>
              </>
            )}
          </>
        )}
        <AppDialog
          title={schedule ? "Edit comfort schedule" : "Room comfort"}
          description="Changes apply on the next manual or automatic plan calculation. Other settings are managed in the desktop."
          isOpen={editing}
          isDismissible={!saving}
          onClose={() => setEditing(false)}
        >
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              void save();
            }}
          >
            {schedule ? (
              <>
                <p>
                  Shared schedule: editing affects every room using it. Times
                  repeat daily in {data?.summary.timezone}; overnight windows
                  are allowed.
                </p>
                <label className="block">
                  Name
                  <input
                    required
                    className={inputClass}
                    value={schedule.name}
                    onChange={(e) =>
                      setSchedule({ ...schedule, name: e.target.value })
                    }
                  />
                </label>
                <label className="block">
                  Default target °C
                  <input
                    required
                    type="number"
                    min="5"
                    max="35"
                    step="0.1"
                    className={inputClass}
                    value={schedule.schedule.defaultTarget}
                    onChange={(e) =>
                      setSchedule({
                        ...schedule,
                        schedule: {
                          ...schedule.schedule,
                          defaultTarget: Number(e.target.value),
                        },
                      })
                    }
                  />
                </label>
                {schedule.schedule.periods.map((p, i) => (
                  <div
                    key={i}
                    className="grid grid-cols-2 gap-2 sm:grid-cols-4"
                  >
                    <label>
                      From
                      <input
                        aria-label={`Window ${i + 1} from`}
                        type="time"
                        required
                        className={inputClass}
                        value={p.from.slice(0, 5)}
                        onChange={(e) =>
                          setSchedule({
                            ...schedule,
                            schedule: {
                              ...schedule.schedule,
                              periods: schedule.schedule.periods.map((v, n) =>
                                n === i ? { ...v, from: e.target.value } : v,
                              ),
                            },
                          })
                        }
                      />
                    </label>
                    <label>
                      To
                      <input
                        type="time"
                        required
                        className={inputClass}
                        value={p.to.slice(0, 5)}
                        onChange={(e) =>
                          setSchedule({
                            ...schedule,
                            schedule: {
                              ...schedule.schedule,
                              periods: schedule.schedule.periods.map((v, n) =>
                                n === i ? { ...v, to: e.target.value } : v,
                              ),
                            },
                          })
                        }
                      />
                    </label>
                    <label>
                      Target °C
                      <input
                        type="number"
                        min="5"
                        max="35"
                        step="0.1"
                        required
                        className={inputClass}
                        value={p.target}
                        onChange={(e) =>
                          setSchedule({
                            ...schedule,
                            schedule: {
                              ...schedule.schedule,
                              periods: schedule.schedule.periods.map((v, n) =>
                                n === i
                                  ? { ...v, target: Number(e.target.value) }
                                  : v,
                              ),
                            },
                          })
                        }
                      />
                    </label>
                    <button
                      type="button"
                      onClick={() =>
                        setSchedule({
                          ...schedule,
                          schedule: {
                            ...schedule.schedule,
                            periods: schedule.schedule.periods.filter(
                              (_, n) => n !== i,
                            ),
                          },
                        })
                      }
                    >
                      Remove
                    </button>
                  </div>
                ))}
                <button
                  className="secondary-action p-3"
                  type="button"
                  onClick={() =>
                    setSchedule({
                      ...schedule,
                      schedule: {
                        ...schedule.schedule,
                        periods: [
                          ...schedule.schedule.periods,
                          { from: "06:00", to: "09:00", target: 22 },
                        ],
                      },
                    })
                  }
                >
                  Add time window
                </button>
              </>
            ) : (
              <>
                <label className="block">
                  Comfort schedule
                  <select
                    className={inputClass}
                    value={scheduleId}
                    onChange={(e) => setScheduleId(e.target.value)}
                  >
                    <option value="">Constant target</option>
                    {data?.schedules.map((s) => (
                      <option key={s.id} value={s.id!}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                </label>
                {!scheduleId && (
                  <label className="block">
                    Room comfort target °C
                    <input
                      className={inputClass}
                      type="number"
                      required
                      min="5"
                      max="35"
                      step="0.1"
                      value={target}
                      onChange={(e) => setTarget(e.target.value)}
                    />
                  </label>
                )}
                {scheduleId && (
                  <button
                    type="button"
                    className="secondary-action p-3"
                    onClick={() =>
                      setSchedule(
                        structuredClone(
                          data!.schedules.find(
                            (s) => s.id === Number(scheduleId),
                          )!,
                        ),
                      )
                    }
                  >
                    Edit selected schedule
                  </button>
                )}
              </>
            )}
            {dialogError && <p role="alert">{dialogError}</p>}
            <div className="flex justify-end gap-3">
              <button
                type="button"
                disabled={saving}
                className="secondary-action p-3"
                onClick={() => setEditing(false)}
              >
                Cancel
              </button>
              <button disabled={saving} className="primary-action p-3">
                {saving ? "Saving…" : "Save"}
              </button>
            </div>
          </form>
        </AppDialog>
      </main>
    </>
  );
}

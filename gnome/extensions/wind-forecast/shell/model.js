/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

export function pointAtTime(chart, timestamp) {
    return chart?.points.find(point => point.timestamp <= timestamp && timestamp < point.end) ?? null;
}

// Match the web chart's linear current marker, but only across contiguous
// forecast intervals. The last sample applies through its declared end.
export function currentForecast(snapshot, now = Date.now()) {
    const point = pointAtTime(snapshot, now);
    if (!point)
        return null;
    const next = snapshot.points.find(candidate => candidate.timestamp === point.end);
    const progress = (now - point.timestamp) / (point.end - point.timestamp);
    return {
        ...point,
        megawatts: next ? point.megawatts + (next.megawatts - point.megawatts) * progress : point.megawatts,
    };
}

export function visibleChart(snapshot, now = Date.now()) {
    const today = snapshot?.days.find(day => day.start <= now && now < day.end);
    if (!today)
        return null;
    const points = snapshot.points.filter(point => point.end > today.start);
    if (!points.length || points[points.length - 1].end <= now)
        return null;
    const start = Math.max(today.start, points[0].timestamp);
    const end = points[points.length - 1].end;
    return {
        points, start, end,
        maximum: Math.max(...points.map(point => point.megawatts), 1),
        days: snapshot.days.filter(day => day.end > start && day.start < end)
            .map(day => ({...day, start: Math.max(day.start, start), end: Math.min(day.end, end)})),
    };
}

export function daySummary(snapshot, now = Date.now()) {
    const today = snapshot?.days.find(day => day.start <= now && now < day.end) ?? null;
    const tomorrow = today ? snapshot.days.find(day => day.start === today.end) ?? null : null;
    const changePercent = Number.isFinite(today?.average) && today.average > 0 && Number.isFinite(tomorrow?.average)
        ? (tomorrow.average - today.average) / today.average * 100 : null;
    return {today, tomorrow, changePercent};
}

export function formatMegawatts(value) {
    return Number.isFinite(value)
        ? new Intl.NumberFormat(undefined, {maximumFractionDigits: 0}).format(value) : '—';
}

export function formatChange(value) {
    return Number.isFinite(value)
        ? `${new Intl.NumberFormat(undefined, {maximumFractionDigits: 1, signDisplay: 'exceptZero'}).format(value)}%` : '—';
}

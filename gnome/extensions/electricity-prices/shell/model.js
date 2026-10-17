/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

// Current prices are always identified by their real half-open interval. In
// particular, the backend's fallback `current` field is deliberately not used.
export function currentPoint(snapshot, now = Date.now()) {
    for (const day of snapshot?.days ?? []) {
        const point = day.points.find(point => point.timestamp <= now && now < point.end);
        if (point)
            return point;
    }
    return null;
}

export function visibleChart(snapshot, now = Date.now()) {
    const days = (snapshot?.days ?? [])
        .filter(day => day.end > now && day.points.length > 0)
        .sort((left, right) => left.start - right.start);
    if (!days.length)
        return null;

    const points = days.flatMap(day => day.points);
    const duration = points.reduce((sum, point) => sum + point.end - point.timestamp, 0);
    return {
        days,
        points,
        start: days[0].start,
        end: days[days.length - 1].end,
        minimum: Math.min(...days.map(day => day.minimum)),
        maximum: Math.max(...days.map(day => day.maximum)),
        average: points.reduce((sum, point) => sum + point.price * (point.end - point.timestamp), 0) / duration,
    };
}

export function pointAtTime(chart, timestamp) {
    return chart?.points.find(point => point.timestamp <= timestamp && timestamp < point.end) ?? null;
}

export function formatPrice(value) {
    return Number.isFinite(value)
        ? new Intl.NumberFormat(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(value)
        : '—';
}

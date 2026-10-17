/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

import Clutter from 'gi://Clutter';
import GObject from 'gi://GObject';
import Pango from 'gi://Pango';
import PangoCairo from 'gi://PangoCairo';
import St from 'gi://St';

import {currentForecast, formatMegawatts, pointAtTime} from './model.js';

const LEFT = 62;
const RIGHT = 14;
const TOP = 30;
const BOTTOM = 30;

export const WindChart = GObject.registerClass({GTypeName: 'PorssiohjainWindChart'},
class WindChart extends St.DrawingArea {
    _init(onInspect) {
        super._init({
            style_class: 'porssiohjain-wind-chart',
            reactive: true,
            can_focus: true,
            track_hover: true,
            x_expand: true,
            accessible_name: 'Finnish wind-power production forecast in megawatts',
        });
        this._chart = null;
        this._snapshot = null;
        this._selected = null;
        this._onInspect = onInspect;
        this.connect('repaint', () => this._paint());
        this.connect('motion-event', (_actor, event) => {
            const [stageX, stageY] = event.get_coords();
            const [ok, x] = this.transform_stage_point(stageX, stageY);
            if (!ok || !this._chart)
                return Clutter.EVENT_PROPAGATE;
            const width = this.get_width();
            const fraction = Math.max(0, Math.min(1, (x - LEFT) / (width - LEFT - RIGHT)));
            const timestamp = this._chart.start + fraction * (this._chart.end - this._chart.start);
            this._selected = pointAtTime(this._chart, Math.min(timestamp, this._chart.end - 1));
            this._onInspect(this._selected);
            this.queue_repaint();
            return Clutter.EVENT_PROPAGATE;
        });
        this.connect('leave-event', () => {
            this._selected = null;
            this._onInspect(null);
            this.queue_repaint();
            return Clutter.EVENT_PROPAGATE;
        });
        this.connect('key-press-event', (_actor, event) => {
            if (!this._chart)
                return Clutter.EVENT_PROPAGATE;
            const key = event.get_key_symbol();
            if (![Clutter.KEY_Left, Clutter.KEY_Right, Clutter.KEY_Home, Clutter.KEY_End].includes(key))
                return Clutter.EVENT_PROPAGATE;
            const points = this._chart.points;
            const active = this._selected ?? currentForecast(this._snapshot);
            const current = Math.max(0, points.findIndex(point => point.timestamp === active?.timestamp));
            const index = key === Clutter.KEY_Home ? 0
                : key === Clutter.KEY_End ? points.length - 1
                : Math.max(0, Math.min(points.length - 1, current + (key === Clutter.KEY_Left ? -1 : 1)));
            this._selected = points[index];
            this._onInspect(this._selected);
            this.queue_repaint();
            return Clutter.EVENT_STOP;
        });
    }

    setData(snapshot, chart) {
        this._snapshot = snapshot;
        this._chart = chart;
        this._selected = chart?.points.find(point => point.timestamp === this._selected?.timestamp) ?? null;
        this.queue_repaint();
    }

    get inspectedPoint() {
        return this._selected;
    }

    _paint() {
        const cr = this.get_context();
        try {
            if (!this._chart)
                return;

            const chart = this._chart;
            const [width, height] = this.get_surface_size();
            const innerWidth = width - LEFT - RIGHT;
            const innerHeight = height - TOP - BOTTOM;
            if (innerWidth <= 0 || innerHeight <= 0)
                return;

            const foreground = this.get_theme_node().get_foreground_color();
            const source = (opacity = 1) => cr.setSourceRGBA(
                foreground.red / 255, foreground.green / 255, foreground.blue / 255, opacity);
            const low = 0;
            const high = chart.maximum * 1.12;
            const x = timestamp => LEFT + (timestamp - chart.start) / (chart.end - chart.start) * innerWidth;
            const y = megawatts => TOP + (high - megawatts) / (high - low) * innerHeight;

            const text = (label, textX, textY, align = 'left') => {
                const layout = PangoCairo.create_layout(cr);
                layout.set_font_description(Pango.FontDescription.from_string('Sans 9'));
                layout.set_text(label, -1);
                const [textWidth] = layout.get_pixel_size();
                cr.moveTo(textX - (align === 'right' ? textWidth : align === 'center' ? textWidth / 2 : 0), textY);
                PangoCairo.show_layout(cr, layout);
            };

            const dateFormat = new Intl.DateTimeFormat(undefined, {
                timeZone: this._snapshot.timezone, weekday: 'short', month: 'short', day: 'numeric',
            });
            for (const [index, day] of chart.days.entries()) {
                if (index % 2 === 1) {
                    source(0.045);
                    cr.rectangle(x(day.start), TOP, x(day.end) - x(day.start), innerHeight);
                    cr.fill();
                }
                source(0.85);
                if (x(day.end) - x(day.start) > 72)
                    text(dateFormat.format(new Date(day.start)), x(day.start) + 6, 8);
                if (index > 0) {
                    source(0.3);
                    cr.setLineWidth(1);
                    cr.setDash([4, 4], 0);
                    cr.moveTo(x(day.start), TOP);
                    cr.lineTo(x(day.start), height - BOTTOM);
                    cr.stroke();
                    cr.setDash([], 0);
                }
            }

            for (let i = 0; i <= 4; i++) {
                const value = low + (high - low) * i / 4;
                source(0.12);
                cr.setLineWidth(1);
                cr.moveTo(LEFT, y(value));
                cr.lineTo(width - RIGHT, y(value));
                cr.stroke();
                source(0.65);
                text(formatMegawatts(value), LEFT - 8, y(value) - 7, 'right');
            }
            const timeFormat = new Intl.DateTimeFormat(undefined, {
                timeZone: this._snapshot.timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
            });
            const tickCount = Math.max(2, Math.floor(innerWidth / 90));
            for (let i = 0; i <= tickCount; i++) {
                const timestamp = chart.start + (chart.end - chart.start) * i / tickCount;
                source(0.65);
                text(timeFormat.format(new Date(timestamp)), x(timestamp), height - BOTTOM + 9,
                    i === 0 ? 'left' : i === tickCount ? 'right' : 'center');
            }

            // Follow the web chart’s linear forecast curve. Gaps stay blank; the
            // last value applies only through its declared interval end.
            for (let i = 0; i < chart.points.length;) {
                const segment = [chart.points[i++]];
                while (i < chart.points.length && segment[segment.length - 1].end === chart.points[i].timestamp)
                    segment.push(chart.points[i++]);
                const trace = () => {
                    cr.moveTo(x(segment[0].timestamp), y(segment[0].megawatts));
                    for (const point of segment)
                        cr.lineTo(x(point.timestamp), y(point.megawatts));
                    const last = segment[segment.length - 1];
                    cr.lineTo(x(last.end), y(last.megawatts));
                };
                trace();
                cr.lineTo(x(segment[segment.length - 1].end), height - BOTTOM);
                cr.lineTo(x(segment[0].timestamp), height - BOTTOM);
                cr.closePath();
                cr.setSourceRGBA(0.34, 0.65, 0.95, 0.14);
                cr.fill();
                trace();
                cr.setSourceRGBA(0.34, 0.65, 0.95, 1);
                cr.setLineWidth(2);
                cr.stroke();
            }

            const now = Date.now();
            const current = currentForecast(this._snapshot, now);
            if (current) {
                const nowX = x(now);
                source(0.5);
                cr.setLineWidth(1);
                cr.setDash([4, 4], 0);
                cr.moveTo(nowX, TOP);
                cr.lineTo(nowX, height - BOTTOM);
                cr.stroke();
                cr.setDash([], 0);
                cr.setSourceRGBA(0.34, 0.65, 0.95, 1);
                cr.arc(nowX, y(current.megawatts), 4, 0, 2 * Math.PI);
                cr.fill();
            }

            if (this._selected) {
                const point = this._selected;
                cr.setSourceRGBA(0.95, 0.64, 0.24, 1);
                cr.setLineWidth(1.5);
                cr.moveTo(x(point.timestamp), TOP);
                cr.lineTo(x(point.timestamp), height - BOTTOM);
                cr.stroke();
                cr.arc(x(point.timestamp), y(point.megawatts), 4, 0, 2 * Math.PI);
                cr.fill();
            }
        } finally {
            cr.$dispose();
        }
    }
});

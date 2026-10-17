/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

import Clutter from 'gi://Clutter';
import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import St from 'gi://St';

import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import * as PanelMenu from 'resource:///org/gnome/shell/ui/panelMenu.js';
import * as PopupMenu from 'resource:///org/gnome/shell/ui/popupMenu.js';

import {WindChart} from './chart.js';
import {currentForecast, daySummary, formatChange, formatMegawatts, visibleChart} from './model.js';

export default class WindForecastExtension extends Extension {
    enable() {
        this._enabled = true;
        this._snapshot = null;
        this._error = false;
        this._lastAttempt = 0;
        this._request = null;
        this._settings = this.getSettings();
        this._indicator = new PanelMenu.Button(0.5, this.metadata.name);

        const panel = new St.BoxLayout({style_class: 'porssiohjain-wind-panel'});
        panel.add_child(new St.Icon({
            gicon: new Gio.FileIcon({
                file: Gio.File.new_for_path(GLib.build_filenamev([this.path, 'icons', 'wind-symbolic.svg'])),
            }),
            style_class: 'system-status-icon',
        }));
        this._panelForecast = new St.Label({
            text: '—', y_align: Clutter.ActorAlign.CENTER, style_class: 'porssiohjain-wind-panel-value',
        });
        panel.add_child(this._panelForecast);
        this._warningIcon = new St.Icon({
            icon_name: 'dialog-warning-symbolic', style_class: 'system-status-icon', visible: false,
        });
        panel.add_child(this._warningIcon);
        this._indicator.add_child(panel);

        const menuItem = new PopupMenu.PopupMenuSection();
        this._content = new St.BoxLayout({vertical: true, style_class: 'porssiohjain-wind-content'});
        menuItem.box.add_child(this._content);
        this._content.add_child(new St.Label({text: 'Wind power forecast', style_class: 'porssiohjain-wind-heading'}));
        this._caption = this._addLabel('', 'porssiohjain-wind-caption');

        const metrics = new St.BoxLayout({style_class: 'porssiohjain-wind-metrics'});
        this._values = {};
        for (const [key, title] of [['current', 'Now · MW'], ['today', 'Today avg · MW'], ['tomorrow', 'Tomorrow avg · MW'], ['change', 'Tomorrow vs today']]) {
            const box = new St.BoxLayout({vertical: true, x_expand: true, style_class: 'porssiohjain-wind-metric'});
            box.add_child(new St.Label({text: title, style_class: 'porssiohjain-wind-caption'}));
            const value = new St.Label({text: '—', style_class: 'porssiohjain-wind-value'});
            box.add_child(value);
            this._values[key] = value;
            metrics.add_child(box);
        }
        this._content.add_child(metrics);
        this._chart = new WindChart(point => this._inspect(point));
        this._content.add_child(this._chart);
        this._inspection = this._addLabel('Point to the graph or use arrow keys to inspect forecasts.', 'porssiohjain-wind-inspection');
        this._status = this._addLabel('Loading forecast…', 'porssiohjain-wind-status');
        this._updated = this._addLabel('', 'porssiohjain-wind-caption');
        this._addLabel('Fingrid · Finnish production forecast · estimates in MW', 'porssiohjain-wind-caption');
        this._indicator.menu.addMenuItem(menuItem);
        this._indicator.menu.addMenuItem(new PopupMenu.PopupSeparatorMenuItem());
        this._refreshItem = this._indicator.menu.addAction('Refresh forecast', () => this._refresh());
        this._indicator.menu.addAction('Open Pörssiohjain', () => this._openWebsite());
        this._indicator.menu.addAction('Settings…', () => this.openPreferences());
        this._indicator.menu.connect('open-state-changed', (_menu, open) => {
            if (!open)
                return;
            const scale = St.ThemeContext.get_for_stage(global.stage).scale_factor;
            const monitor = Main.layoutManager.findMonitorForActor(this._indicator)
                ?? Main.layoutManager.primaryMonitor;
            this._content.width = Math.max(280, Math.min(680, monitor.width / scale - 80));
            this._tick();
            this._render();
        });

        Main.panel.addToStatusArea(this.uuid, this._indicator);
        this._settingsSignal = this._settings.connect('changed', (_settings, key) => {
            if (key === 'show-unit') {
                this._render();
                return;
            }
            this._cancelRequest();
            this._snapshot = null;
            this._error = false;
            this._refresh();
        });
        this._timer = GLib.timeout_add_seconds(GLib.PRIORITY_DEFAULT, 15, () => {
            this._tick();
            return GLib.SOURCE_CONTINUE;
        });
        this._refresh();
    }

    _addLabel(text, styleClass) {
        const label = new St.Label({text, style_class: styleClass});
        label.clutter_text.line_wrap = true;
        label.clutter_text.ellipsize = 0;
        this._content.add_child(label);
        return label;
    }

    _tick() {
        const interval = this._settings.get_int('refresh-minutes') * 60_000;
        if (!this._request && Date.now() - this._lastAttempt >= interval)
            this._refresh();
        else
            this._render();
    }

    _refresh() {
        if (!this._enabled || this._request)
            return;

        this._lastAttempt = Date.now();
        let request;
        try {
            request = Gio.Subprocess.new([
                GLib.build_filenamev([this.path, 'libexec', 'porssiohjain-wind']),
                '--base-url', this._settings.get_string('server-url'),
                '--timezone', this._settings.get_string('timezone'),
            ], Gio.SubprocessFlags.STDOUT_PIPE | Gio.SubprocessFlags.STDERR_PIPE);
        } catch (error) {
            console.error(`[${this.uuid}] Could not start the Rust forecast helper: ${error.message}`);
            this._error = true;
            this._render();
            return;
        }

        this._request = request;
        this._cancellable = new Gio.Cancellable();
        this._render();
        request.communicate_utf8_async(null, this._cancellable, (process, result) => {
            if (!this._enabled || request !== this._request)
                return;
            try {
                const [, stdout, stderr] = process.communicate_utf8_finish(result);
                if (!process.get_successful())
                    throw new Error(stderr.trim() || 'Rust helper failed');
                const snapshot = JSON.parse(stdout);
                if (!Array.isArray(snapshot.days) || !Array.isArray(snapshot.points) || !Number.isFinite(snapshot.fetchedAt))
                    throw new Error('Invalid Rust helper response');
                this._snapshot = snapshot;
                this._error = false;
            } catch (error) {
                this._error = true;
                console.error(`[${this.uuid}] ${error.message}`);
            } finally {
                this._request = null;
                this._cancellable = null;
                this._render();
            }
        });
    }

    _render() {
        const now = Date.now();
        const current = currentForecast(this._snapshot, now);
        const chart = visibleChart(this._snapshot, now);
        const {today, tomorrow, changePercent} = daySummary(this._snapshot, now);
        const tooOld = this._snapshot && now - this._snapshot.fetchedAt > Math.max(
            30 * 60_000, this._settings.get_int('refresh-minutes') * 120_000);
        const stale = Boolean(this._error || tooOld);
        const unit = this._settings.get_boolean('show-unit') ? ' MW' : '';
        this._panelForecast.text = `${formatMegawatts(current?.megawatts)}${unit}`;
        this._warningIcon.visible = stale;
        this._indicator.accessible_name = `Finnish wind-power forecast: ${formatMegawatts(current?.megawatts)} megawatts${stale ? '. Forecast could not be refreshed.' : ''}`;
        this._caption.text = `Finland · ${this._settings.get_string('timezone')} · MW`;
        this._values.current.text = formatMegawatts(current?.megawatts);
        this._values.today.text = formatMegawatts(today?.average);
        this._values.tomorrow.text = formatMegawatts(tomorrow?.average);
        this._values.change.text = formatChange(changePercent);
        this._chart.visible = Boolean(chart);
        this._inspection.visible = Boolean(chart);
        if (this._indicator.menu.isOpen) {
            this._chart.setData(this._snapshot, chart);
            this._inspect(this._chart.inspectedPoint);
        }

        const messages = [];
        if (this._error) {
            messages.push(this._snapshot
                ? 'Refresh failed. Showing previously fetched forecasts.'
                : 'Could not load the forecast. Check Settings and try Refresh.');
        } else if (this._request && !this._snapshot) {
            messages.push('Loading forecast…');
        } else if (this._snapshot) {
            if (tooOld)
                messages.push('Showing previously fetched forecasts.');
            if (!current)
                messages.push('No forecast is available for the current interval.');
            if (!chart)
                messages.push('No unexpired forecast data is available.');
            if (!Number.isFinite(tomorrow?.average))
                messages.push('Tomorrow’s forecast is not available yet.');
        }
        if ([today, tomorrow].some(day => Number.isFinite(day?.average) && !day.complete))
            messages.push('Daily averages use available samples; some intervals are missing.');
        this._status.text = messages.join('\n');
        this._status.visible = messages.length > 0;
        this._status.set_style_class_name(`porssiohjain-wind-status${stale ? ' porssiohjain-wind-warning' : ''}`);
        this._refreshItem.setSensitive(!this._request);
        this._refreshItem.label.text = this._request ? 'Refreshing…' : 'Refresh forecast';

        if (this._snapshot) {
            const formatter = new Intl.DateTimeFormat(undefined, {
                timeZone: this._snapshot.timezone, weekday: 'short', day: 'numeric', month: 'short',
                hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
            });
            this._updated.text = `Fetched ${formatter.format(new Date(this._snapshot.fetchedAt))}`;
        } else {
            this._updated.text = '';
        }
    }

    _inspect(point) {
        if (!point || !this._snapshot) {
            this._inspection.text = 'Point to the graph or use arrow keys to inspect forecasts.';
            return;
        }
        const day = new Intl.DateTimeFormat(undefined, {
            timeZone: this._snapshot.timezone, weekday: 'short', month: 'short', day: 'numeric',
        }).format(new Date(point.timestamp));
        this._inspection.text = `${day} · ${point.timeLabel} · ${formatMegawatts(point.megawatts)} MW forecast`;
    }

    _openWebsite() {
        const url = this._settings.get_string('server-url');
        if (!/^https?:\/\//i.test(url))
            return;
        Gio.AppInfo.launch_default_for_uri_async(url, null, null, (_source, result) => {
            try {
                Gio.AppInfo.launch_default_for_uri_finish(result);
            } catch (error) {
                console.error(`[${this.uuid}] Could not open website: ${error.message}`);
            }
        });
    }

    _cancelRequest() {
        const request = this._request;
        this._request = null;
        this._cancellable?.cancel();
        this._cancellable = null;
        request?.force_exit();
    }

    disable() {
        this._enabled = false;
        if (this._timer) {
            GLib.Source.remove(this._timer);
            this._timer = null;
        }
        this._cancelRequest();
        this._settings.disconnect(this._settingsSignal);
        this._settings = null;
        this._indicator.destroy();
        this._indicator = null;
        this._snapshot = null;
        this._content = null;
        this._chart = null;
        this._values = null;
        this._panelForecast = null;
        this._warningIcon = null;
        this._caption = null;
        this._inspection = null;
        this._status = null;
        this._updated = null;
        this._refreshItem = null;
    }
}

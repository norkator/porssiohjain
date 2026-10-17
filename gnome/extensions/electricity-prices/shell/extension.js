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

import {PriceChart} from './chart.js';
import {currentPoint, formatPrice, visibleChart} from './model.js';

export default class ElectricityPricesExtension extends Extension {
    enable() {
        this._enabled = true;
        this._snapshot = null;
        this._error = false;
        this._lastAttempt = 0;
        this._request = null;
        this._settings = this.getSettings();
        this._indicator = new PanelMenu.Button(0.5, this.metadata.name);

        const panel = new St.BoxLayout({style_class: 'porssiohjain-panel'});
        panel.add_child(new St.Icon({
            gicon: new Gio.FileIcon({
                file: Gio.File.new_for_path(GLib.build_filenamev([this.path, 'icons', 'electricity-symbolic.svg'])),
            }),
            style_class: 'system-status-icon',
        }));
        this._panelPrice = new St.Label({
            text: '—', y_align: Clutter.ActorAlign.CENTER, style_class: 'porssiohjain-panel-price',
        });
        panel.add_child(this._panelPrice);
        this._warningIcon = new St.Icon({
            icon_name: 'dialog-warning-symbolic', style_class: 'system-status-icon', visible: false,
        });
        panel.add_child(this._warningIcon);
        this._indicator.add_child(panel);

        const menuItem = new PopupMenu.PopupMenuSection();
        this._content = new St.BoxLayout({vertical: true, style_class: 'porssiohjain-content'});
        menuItem.box.add_child(this._content);
        this._content.add_child(new St.Label({text: 'Electricity prices', style_class: 'porssiohjain-heading'}));
        this._caption = this._addLabel('', 'porssiohjain-caption');

        const metrics = new St.BoxLayout({style_class: 'porssiohjain-metrics'});
        this._values = {};
        for (const [key, title] of [['current', 'Now'], ['minimum', 'Minimum'], ['average', 'Average'], ['maximum', 'Maximum']]) {
            const box = new St.BoxLayout({vertical: true, x_expand: true, style_class: 'porssiohjain-metric'});
            box.add_child(new St.Label({text: title, style_class: 'porssiohjain-caption'}));
            const value = new St.Label({text: '—', style_class: 'porssiohjain-value'});
            box.add_child(value);
            this._values[key] = value;
            metrics.add_child(box);
        }
        this._content.add_child(metrics);
        this._chart = new PriceChart(point => this._inspect(point));
        this._content.add_child(this._chart);
        this._inspection = this._addLabel('Point to the graph or use arrow keys to inspect prices.', 'porssiohjain-inspection');
        this._status = this._addLabel('Loading prices…', 'porssiohjain-status');
        this._updated = this._addLabel('', 'porssiohjain-caption');
        this._addLabel('Spot price incl. VAT · transfer and retailer fees excluded', 'porssiohjain-caption');
        this._indicator.menu.addMenuItem(menuItem);
        this._indicator.menu.addMenuItem(new PopupMenu.PopupSeparatorMenuItem());
        this._refreshItem = this._indicator.menu.addAction('Refresh prices', () => this._refresh());
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
                GLib.build_filenamev([this.path, 'libexec', 'porssiohjain-prices']),
                '--base-url', this._settings.get_string('server-url'),
                '--timezone', this._settings.get_string('timezone'),
                '--market', this._settings.get_string('market-index'),
            ], Gio.SubprocessFlags.STDOUT_PIPE | Gio.SubprocessFlags.STDERR_PIPE);
        } catch (error) {
            console.error(`[${this.uuid}] Could not start the Rust price helper: ${error.message}`);
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
                if (!Array.isArray(snapshot.days) || !Number.isFinite(snapshot.fetchedAt))
                    throw new Error('Invalid Rust helper response');
                this._snapshot = snapshot;
                this._error = false;
                for (const error of snapshot.errors)
                    console.warn(`[${this.uuid}] ${error.day}: ${error.message}`);
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
        const current = currentPoint(this._snapshot, now);
        const chart = visibleChart(this._snapshot, now);
        const tooOld = this._snapshot && now - this._snapshot.fetchedAt > Math.max(
            10 * 60_000, this._settings.get_int('refresh-minutes') * 120_000);
        const stale = Boolean(this._error || tooOld || this._snapshot?.errors.some(error => error.day === 'today'));
        const unit = this._settings.get_boolean('show-unit') ? ' ¢/kWh' : '';
        this._panelPrice.text = `${formatPrice(current?.price)}${unit}`;
        this._warningIcon.visible = stale;
        this._indicator.accessible_name = `Electricity price: ${formatPrice(current?.price)} cents per kWh${stale ? '. Prices could not be refreshed.' : ''}`;
        this._caption.text = `${this._settings.get_string('market-index')} · ${this._settings.get_string('timezone')} · ¢/kWh`;
        this._values.current.text = formatPrice(current?.price);
        for (const key of ['minimum', 'average', 'maximum'])
            this._values[key].text = formatPrice(chart?.[key]);
        this._chart.visible = Boolean(chart);
        this._inspection.visible = Boolean(chart);
        if (this._indicator.menu.isOpen) {
            this._chart.setData(this._snapshot, chart);
            this._inspect(this._chart.inspectedPoint ?? current);
        }

        const messages = [];
        if (this._error) {
            messages.push(this._snapshot
                ? 'Refresh failed. Showing previously fetched prices.'
                : 'Could not load prices. Check Settings and try Refresh.');
        } else if (this._request && !this._snapshot) {
            messages.push('Loading prices…');
        } else if (this._snapshot) {
            if (tooOld)
                messages.push('Showing previously fetched prices.');
            if (!current)
                messages.push('No price is available for the current interval.');
            if (this._snapshot.errors.some(error => error.day === 'tomorrow'))
                messages.push('Could not load tomorrow’s prices.');
            else if (!chart?.days.some(day => day.start > now))
                messages.push('Tomorrow’s prices have not been published yet.');
            if (chart?.days.some(day => !day.complete))
                messages.push('Some price intervals are missing.');
        }
        this._status.text = messages.join('\n');
        this._status.visible = messages.length > 0;
        this._status.set_style_class_name(`porssiohjain-status${stale ? ' porssiohjain-warning' : ''}`);
        this._refreshItem.setSensitive(!this._request);
        this._refreshItem.label.text = this._request ? 'Refreshing…' : 'Refresh prices';

        if (this._snapshot) {
            const formatter = new Intl.DateTimeFormat(undefined, {
                timeZone: this._snapshot.timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
            });
            const resolutions = [...new Set(chart?.days.map(day => day.resolutionMinutes) ?? [])];
            this._updated.text = `Updated ${formatter.format(new Date(this._snapshot.fetchedAt))}${resolutions.length ? ` · ${resolutions.join('/')} min intervals` : ''}`;
        } else {
            this._updated.text = '';
        }
    }

    _inspect(point) {
        if (!point || !this._snapshot) {
            this._inspection.text = 'Point to the graph or use arrow keys to inspect prices.';
            return;
        }
        const day = new Intl.DateTimeFormat(undefined, {
            timeZone: this._snapshot.timezone, weekday: 'short', month: 'short', day: 'numeric',
        }).format(new Date(point.timestamp));
        this._inspection.text = `${day} · ${point.timeLabel} · ${formatPrice(point.price)} ¢/kWh`;
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
        this._panelPrice = null;
        this._warningIcon = null;
        this._caption = null;
        this._inspection = null;
        this._status = null;
        this._updated = null;
        this._refreshItem = null;
    }
}

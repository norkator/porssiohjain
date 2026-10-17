/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

import Adw from 'gi://Adw';
import Gio from 'gi://Gio';
import Gtk from 'gi://Gtk';

import {ExtensionPreferences} from 'resource:///org/gnome/Shell/Extensions/js/extensions/prefs.js';

export default class WindForecastPreferences extends ExtensionPreferences {
    fillPreferencesWindow(window) {
        const settings = this.getSettings();
        window._settings = settings;
        const page = new Adw.PreferencesPage({title: 'Wind forecast', icon_name: 'preferences-system-symbolic'});
        window.add(page);
        const source = new Adw.PreferencesGroup({
            title: 'Forecast source',
            description: 'Shows Fingrid’s Finnish wind-power production forecast from your Pörssiohjain server. No account or API token is needed.',
        });
        page.add(source);
        for (const [key, title] of [['server-url', 'Pörssiohjain server URL'], ['timezone', 'Timezone (for example Europe/Helsinki)']]) {
            const row = new Adw.EntryRow({title, text: settings.get_string(key), show_apply_button: true});
            row.connect('apply', () => settings.set_string(key, row.text.trim()));
            source.add(row);
        }
        const display = new Adw.PreferencesGroup({title: 'Display and refresh'});
        page.add(display);
        const interval = new Adw.SpinRow({
            title: 'Refresh interval (minutes)',
            subtitle: 'The current estimate also updates every 15 seconds using the fetched forecast.',
            adjustment: new Gtk.Adjustment({lower: 1, upper: 60, step_increment: 1, page_increment: 5}),
        });
        settings.bind('refresh-minutes', interval, 'value', Gio.SettingsBindFlags.DEFAULT);
        display.add(interval);
        const unit = new Adw.SwitchRow({title: 'Show MW in the top bar'});
        settings.bind('show-unit', unit, 'active', Gio.SettingsBindFlags.DEFAULT);
        display.add(unit);
        page.add(new Adw.PreferencesGroup({
            title: 'About this forecast',
            description: 'Estimated Finnish wind-power production in megawatts, not local wind speed or measured production. Daily averages use the available forecast samples; a negative tomorrow change means lower production.',
        }));
    }
}

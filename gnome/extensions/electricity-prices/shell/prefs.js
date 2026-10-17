/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

import Adw from 'gi://Adw';
import Gio from 'gi://Gio';
import Gtk from 'gi://Gtk';

import {ExtensionPreferences} from 'resource:///org/gnome/Shell/Extensions/js/extensions/prefs.js';

const MARKETS = [
    'EE', 'LT', 'LV', 'AT', 'BE', 'FR', 'GER', 'NL', 'PL', 'DK1', 'DK2', 'FI',
    'NO1', 'NO2', 'NO3', 'NO4', 'NO5', 'SE1', 'SE2', 'SE3', 'SE4', 'BG', 'TEL',
];

export default class ElectricityPricesPreferences extends ExtensionPreferences {
    fillPreferencesWindow(window) {
        const settings = this.getSettings();
        window._settings = settings;
        const page = new Adw.PreferencesPage({title: 'Electricity prices', icon_name: 'preferences-system-symbolic'});
        window.add(page);
        const source = new Adw.PreferencesGroup({
            title: 'Price source',
            description: 'Shows Nord Pool spot prices from your Pörssiohjain server. No account or API token is needed.',
        });
        page.add(source);
        for (const [key, title] of [['server-url', 'Pörssiohjain server URL'], ['timezone', 'Timezone (for example Europe/Helsinki)']]) {
            const row = new Adw.EntryRow({title, text: settings.get_string(key), show_apply_button: true});
            row.connect('apply', () => settings.set_string(key, row.text.trim()));
            source.add(row);
        }
        const market = new Adw.ComboRow({
            title: 'Nord Pool market',
            model: Gtk.StringList.new(MARKETS),
            selected: Math.max(0, MARKETS.indexOf(settings.get_string('market-index'))),
        });
        market.connect('notify::selected', () => settings.set_string('market-index', MARKETS[market.selected]));
        source.add(market);

        const display = new Adw.PreferencesGroup({title: 'Display and refresh'});
        page.add(display);
        const interval = new Adw.SpinRow({
            title: 'Refresh interval (minutes)',
            subtitle: 'Current price changes also follow the intervals in the fetched data.',
            adjustment: new Gtk.Adjustment({lower: 1, upper: 60, step_increment: 1, page_increment: 5}),
        });
        settings.bind('refresh-minutes', interval, 'value', Gio.SettingsBindFlags.DEFAULT);
        display.add(interval);
        const unit = new Adw.SwitchRow({title: 'Show ¢/kWh in the top bar'});
        settings.bind('show-unit', unit, 'active', Gio.SettingsBindFlags.DEFAULT);
        display.add(unit);
        page.add(new Adw.PreferencesGroup({
            title: 'About these prices',
            description: 'Prices include the VAT applied by the public API (currently 25.5%). Transfer charges and retailer fees are excluded. Your controls can use a different combined price.',
        }));
    }
}

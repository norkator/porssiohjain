// Loaded only by tools/smoke.py in a private headless Shell.
import Clutter from 'gi://Clutter';
import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import St from 'gi://St';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import * as Scripting from 'resource:///org/gnome/shell/ui/scripting.js';

export const METRICS = {};
const UUIDS = ['electricity-prices@porssiohjain.fi', 'wind-forecast@porssiohjain.fi'];
const statePath = GLib.getenv('PORS_SHELL_SMOKE_STATE');
const devices = [];

function assert(condition, message) {
    if (!condition)
        throw new Error(message);
}

async function until(test, description) {
    for (let i = 0; i < 150; i++) {
        if (test())
            return;
        await Scripting.sleep(100);
    }
    throw new Error(`Timed out: ${description}`);
}

function mode(value) {
    GLib.file_set_contents(`${statePath}/mode`, value);
}

export async function run() {
    Main.overview.hide();
    const shellSettings = new Gio.Settings({schema_id: 'org.gnome.shell'});
    const extensions = UUIDS.map(uuid => {
        const extension = Main.extensionManager.lookup(uuid);
        assert(extension, `Extension not discovered: ${uuid}`);
        const schema = Gio.SettingsSchemaSource.new_from_directory(
            `${extension.path}/schemas`, Gio.SettingsSchemaSource.get_default(), false)
            .lookup(extension.metadata['settings-schema'], false);
        const settings = new Gio.Settings({settings_schema: schema});
        settings.set_string('server-url', GLib.getenv('PORS_SHELL_SMOKE_URL'));
        return extension;
    });
    // Record chart handlers so pointer coordinates can be supplied without a
    // physical seat. Headless virtual pointer warping differs between Mutter
    // 46 and 50; actor picking and coordinate transforms still use the real UI.
    const motionHandlers = new WeakMap();
    const connect = St.DrawingArea.prototype.connect;
    St.DrawingArea.prototype.connect = function (signal, handler) {
        if (signal === 'motion-event')
            motionHandlers.set(this, handler);
        return connect.call(this, signal, handler);
    };
    try {
        shellSettings.set_strv('enabled-extensions', UUIDS);
        await until(() => extensions.every(extension => extension.stateObj?._snapshot), 'initial helper snapshots');
    } finally {
        St.DrawingArea.prototype.connect = connect;
    }
    assert(extensions[0].stateObj._settings !== extensions[1].stateObj._settings, 'Independent settings');

    for (const extension of extensions) {
        const ui = extension.stateObj;
        assert(!extension.error, `${extension.uuid}: ${extension.error}`);
        assert(ui._content.get_children().length > 5, 'Menu content exists');
        let paints = 0;
        const paint = ui._chart._paint.bind(ui._chart);
        ui._chart._paint = () => {
            paint();
            paints++;
        };
        ui._indicator.menu.open();
        await until(() => paints > 0 && ui._chart.get_width() > 0, 'chart repaint');
        assert(ui._chart._chart.points.length > 1, 'Chart has fixture intervals');
        const chart = ui._chart;
        const seat = Clutter.get_default_backend().get_default_seat();
        const [x, y] = chart.get_transformed_position();
        const coords = [x + chart.width / 2, y + chart.height / 2];
        assert(global.stage.get_actor_at_pos(Clutter.PickMode.REACTIVE, ...coords) === chart, 'Pointer can reach chart');
        motionHandlers.get(chart)(chart, {get_coords: () => coords});
        assert(chart.inspectedPoint !== null, 'Pointer handler inspects chart midpoint');
        // GJS cannot construct Clutter key events on every supported Shell.
        // Use virtual input so keyboard/pointer handlers run through Clutter.
        const keyboard = seat.create_virtual_device(Clutter.InputDeviceType.KEYBOARD_DEVICE);
        devices.push(keyboard);
        chart.grab_key_focus();
        keyboard.notify_keyval(0, Clutter.KEY_End, Clutter.KeyState.PRESSED);
        keyboard.notify_keyval(0, Clutter.KEY_End, Clutter.KeyState.RELEASED);
        await until(() => chart.inspectedPoint === chart._chart.points.at(-1), 'End inspects last point');
        keyboard.notify_keyval(0, Clutter.KEY_Home, Clutter.KeyState.PRESSED);
        keyboard.notify_keyval(0, Clutter.KEY_Home, Clutter.KeyState.RELEASED);
        await until(() => chart.inspectedPoint === chart._chart.points[0], 'Home inspects first point');
        ui._indicator.menu.close();

        ui.openPreferences();
        await until(() => global.get_window_actors().some(actor =>
            actor.meta_window.get_title()?.includes(extension.metadata.name)), 'preferences window');
        for (const actor of global.get_window_actors()) {
            if (actor.meta_window.get_title()?.includes(extension.metadata.name))
                actor.meta_window.delete(0);
        }
        print(`SMOKE: ${extension.uuid}: menu, chart, keyboard, pointer handler, preferences passed`);
    }

    for (const extension of extensions) {
        const ui = extension.stateObj;
        await until(() => ui._snapshot && !ui._request, 'settled helper before failure test');
        const snapshot = ui._snapshot;
        mode('error');
        ui._refresh();
        await until(() => !ui._request && ui._error, 'failed refresh');
        assert(ui._snapshot === snapshot && ui._warningIcon.visible, 'Retain data and warn after failure');
        mode('ok');
        ui._refresh();
        await until(() => !ui._request && !ui._error, 'successful retry');
        mode('slow');
        ui._refresh();
        const request = ui._request;
        assert(request, 'Helper in flight');
        Main.extensionManager.disableExtension(extension.uuid);
        await until(() => !ui._enabled, 'disable');
        assert(!ui._timer && !ui._request && !ui._indicator, 'Clear timer, helper, indicator');
        await new Promise(resolve => request.wait_async(null, (process, result) => {
            process.wait_finish(result);
            resolve();
        }));
        assert(!request.get_successful(), 'Canceled helper terminated');
        mode('ok');
        Main.extensionManager.enableExtension(extension.uuid);
        await until(() => extension.stateObj?._snapshot, 're-enable');
        Main.extensionManager.disableExtension(extension.uuid);
        print(`SMOKE: ${extension.uuid}: failed refresh, cancellation, disable/re-enable passed`);
    }
    GLib.file_set_contents(`${statePath}/passed`, 'ok');
}

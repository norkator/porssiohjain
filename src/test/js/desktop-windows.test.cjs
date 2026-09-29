const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');

function setup(saved, mobile = false, brokenStorage = false) {
    const window = new EventTarget();
    let dirty = false;
    const values = new Map(saved === undefined ? [] : [['porssiohjain:window:v1:42:controls', saved]]);
    const title = new EventTarget();
    const handle = new EventTarget();
    const frame = new EventTarget();
    const classes = new Set(['retro-window', 'retro-window-maximized']);
    frame.classList = {
        contains: key => classes.has(key), add: key => classes.add(key), remove: key => classes.delete(key),
        toggle: (key, enabled) => enabled ? classes.add(key) : classes.delete(key)
    };
    frame.style = {};
    frame.dataset = { windowKey: '42:controls' };
    frame.isConnected = true;
    frame.querySelector = selector => selector.includes('titlebar') ? title : handle;
    const desktop = { clientWidth: 1000, clientHeight: 800, querySelectorAll: () => [frame] };
    frame.closest = () => desktop;
    for (const [key, fallback] of [['Width', 600], ['Height', 400], ['Left', 10], ['Top', 10]]) {
        Object.defineProperty(frame, 'offset' + key, { get: () => parseFloat(frame.style[key.toLowerCase()]) || fallback });
    }
    const context = {
        window, document: { querySelector: () => dirty ? {} : null },
        matchMedia: () => ({ matches: mobile }),
        localStorage: {
            getItem: key => { if (brokenStorage) throw new Error('denied'); return values.get(key) ?? null; },
            setItem: (key, value) => { if (brokenStorage) throw new Error('denied'); values.set(key, value); }
        },
        CustomEvent: class extends Event { constructor(type, options) { super(type); this.detail = options?.detail; } },
        ResizeObserver: class { observe() {} disconnect() {} },
        MutationObserver: class { observe() {} disconnect() {} }
    };
    vm.runInNewContext(readFileSync('src/main/frontend/desktop-windows.js', 'utf8'), context);
    return { window, frame, handle, values, init: () => window.initDesktopWindow(frame), setDirty: value => { dirty = value; } };
}

test('restores and clamps saved desktop geometry, notifying server of maximized state', () => {
    const env = setup(JSON.stringify({ maximized: false, left: 900, top: 900, width: 600, height: 400 }));
    let restored;
    env.frame.addEventListener('desktop-restore-geometry', event => restored = event.detail.maximized);
    env.init();
    assert.equal(restored, false);
    assert.equal(env.frame.style.left, '400px');
    assert.equal(env.frame.style.top, '362px');
    env.frame.classList.add('retro-window-maximized');
    env.frame.dispatchEvent(new Event('desktop-save-geometry'));
    const saved = JSON.parse(env.values.get('porssiohjain:window:v1:42:controls'));
    assert.equal(saved.maximized, true);
    assert.equal(saved.width, 600);
});

test('mobile keeps full-screen defaults without overwriting desktop geometry', () => {
    const saved = JSON.stringify({ maximized: false, left: 50, top: 70, width: 600, height: 400 });
    const env = setup(saved, true);
    env.init();
    env.frame.dispatchEvent(new Event('desktop-save-geometry'));
    assert.equal(env.frame.classList.contains('retro-window-maximized'), true);
    assert.equal(env.frame.style.width, undefined);
    assert.equal(env.values.get('porssiohjain:window:v1:42:controls'), saved);
});

test('malformed or unavailable local storage leaves windows usable', () => {
    for (const env of [setup('invalid'), setup(undefined, false, true)]) {
        assert.doesNotThrow(env.init);
        assert.doesNotThrow(() => env.frame.dispatchEvent(new Event('desktop-save-geometry')));
    }
});

test('browser reload and closing tab are guarded only when drafts exist', () => {
    const env = setup();
    const clean = new Event('beforeunload', { cancelable: true });
    env.window.dispatchEvent(clean);
    assert.equal(clean.defaultPrevented, false);
    env.setDirty(true);
    const dirty = new Event('beforeunload', { cancelable: true });
    env.window.dispatchEvent(dirty);
    assert.equal(dirty.defaultPrevented, true);
});

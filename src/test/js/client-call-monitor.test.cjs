const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');

test('refresh countdown waits for response, pauses when hidden, and cleans up on detach', () => {
    let now = 0;
    let tick;
    let detached;
    let cleared = 0;
    let disconnected = 0;
    let calls = 0;
    let visible = true;
    const status = {
        isConnected: true,
        getClientRects: () => visible ? [{}] : [],
        dispatchEvent: event => {
            assert.equal(event.type, 'client-call-refresh');
            calls++;
        }
    };
    const context = {
        window: {}, document: { hidden: false, body: {} },
        Date: { now: () => now },
        setInterval: callback => { tick = callback; return 1; },
        clearInterval: () => cleared++,
        MutationObserver: class {
            constructor(callback) { detached = callback; }
            observe() {}
            disconnect() { disconnected++; }
        },
        CustomEvent: class { constructor(type) { this.type = type; } }
    };
    vm.runInNewContext(readFileSync('src/main/frontend/client-call-monitor.js', 'utf8'), context);
    const init = () => context.window.initClientCallMonitor(status, 'Next refresh in {seconds} s', 'Refreshing…');
    init();
    assert.equal(status.textContent, 'Next refresh in 10 s');
    now = 3000;
    tick();
    assert.equal(status.textContent, 'Next refresh in 7 s');
    now = 10000;
    tick();
    assert.equal(calls, 1);
    assert.equal(status.textContent, 'Refreshing…');
    now = 20000;
    tick();
    assert.equal(calls, 1);
    status.clientCallRefreshComplete();
    assert.equal(status.textContent, 'Next refresh in 10 s');
    visible = false;
    now = 31000;
    tick();
    assert.equal(calls, 1);
    visible = true;
    tick();
    assert.equal(calls, 2);
    status.isConnected = false;
    detached();
    assert.equal(cleared, 1);
    assert.equal(disconnected, 1);
    status.isConnected = true;
    init();
    now += 10000;
    tick();
    assert.equal(calls, 3);
});

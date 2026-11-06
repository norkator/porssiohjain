const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');

function setup() {
    let now = Date.parse('2026-10-25T00:20:00Z');
    const timers = new Map();
    const observers = [];
    const current = { textContent: '' };
    const container = new EventTarget();
    container.isConnected = true;
    container.visible = true;
    container.getClientRects = () => container.visible ? [{}] : [];
    container.closest = () => ({ querySelector: () => current });
    const document = new EventTarget();
    document.hidden = false;
    const window = {
        setInterval: callback => { const id = timers.size + 1; timers.set(id, callback); return id; },
        clearInterval: id => timers.delete(id)
    };
    const context = {
        window, document, Intl, Date: { now: () => now },
        CustomEvent: class extends Event {},
        MutationObserver: class {
            constructor(callback) { this.callback = callback; observers.push(this); }
            observe() {}
            disconnect() { this.disconnected = true; }
        },
        ApexCharts: class {
            constructor(element, options) { this.options = options; }
            render() { return Promise.resolve(); }
            updateOptions(options) { this.updated = options; }
            destroy() { this.destroyed = true; }
        }
    };
    vm.runInNewContext(readFileSync('src/main/frontend/forecast-chart.js', 'utf8'), context);
    const config = {
        times: ['2026-10-25T00:00:00Z', '2026-10-25T00:15:00Z', '2026-10-25T01:00:00Z'].map(Date.parse),
        values: [3, -2, 5], end: Date.parse('2026-10-25T01:15:00Z'), zone: 'Europe/Helsinki',
        locale: 'en', prices: true, name: 'Prices', unit: 'c/kWh', nowLabel: 'Now',
        days: [{ start: Date.parse('2026-10-24T21:00:00Z'), end: Date.parse('2026-10-25T22:00:00Z'), label: 'Sun 25.10.' }]
    };
    return { window, container, document, current, timers, observers, config,
        setNow: value => now = Date.parse(value), tick: () => [...timers.values()].forEach(callback => callback()) };
}

test('market chart preserves negative prices, missing quarters, and the final quarter', async () => {
    const env = setup();
    env.window.renderForecastChart(env.container, env.config);
    await Promise.resolve();
    const chart = env.container.chartInstance;
    assert.equal(chart.options.yaxis.min, undefined);
    assert.equal(chart.options.series[0].data[1][1], -2);
    assert.equal(chart.options.series[0].data[2][1], null);
    assert.equal(chart.options.series[0].data.at(-1)[0], env.config.end);
    assert.equal(chart.options.responsive[0].options.yaxis.labels.formatter(-2), '-2.00');
    assert.equal(env.current.textContent, '-2.00');
    env.setNow('2026-10-25T00:40:00Z');
    env.tick();
    assert.equal(env.current.textContent, '–');
    assert.equal(chart.updated.annotations.xaxis.at(-1).x, Date.parse('2026-10-25T00:40:00Z'));
});

test('tooltips distinguish the repeated local clock hour during autumn DST', () => {
    const env = setup();
    env.window.renderForecastChart(env.container, env.config);
    const format = env.container.chartInstance.options.tooltip.x.formatter;
    const before = format(Date.parse('2026-10-25T00:00:00Z'));
    const after = format(Date.parse('2026-10-25T01:00:00Z'));
    assert.ok(before.includes('03:00'));
    assert.ok(after.includes('03:00'));
    assert.notEqual(before, after);
});

test('wind chart keeps the full horizon and uses a zero baseline', () => {
    const env = setup();
    env.config.prices = false;
    env.config.end = Date.parse('2026-10-28T01:15:00Z');
    env.window.renderForecastChart(env.container, env.config);
    const chart = env.container.chartInstance;
    assert.equal(chart.options.xaxis.max, env.config.end);
    assert.equal(chart.options.yaxis.min, 0);
    assert.equal(chart.options.series[0].data.at(-1)[0], env.config.end);
});

test('closing and replacing charts destroys instances and releases their timers', async () => {
    const env = setup();
    env.window.renderForecastChart(env.container, env.config);
    await Promise.resolve();
    const first = env.container.chartInstance;
    env.window.renderForecastChart(env.container, env.config);
    await Promise.resolve();
    assert.equal(first.destroyed, true);
    assert.equal(env.timers.size, 1);
    const second = env.container.chartInstance;
    env.container.isConnected = false;
    env.observers.at(-1).callback();
    assert.equal(second.destroyed, true);
    assert.equal(env.timers.size, 0);
});

test('quarterly refresh waits for a visible window and resumes after minimizing', () => {
    const env = setup();
    let refreshes = 0;
    env.container.addEventListener('forecast-refresh', () => refreshes++);
    env.window.initForecastsView(env.container);
    env.tick();
    assert.equal(refreshes, 0);
    env.container.visible = false;
    env.setNow('2026-10-25T00:30:00Z');
    env.tick();
    assert.equal(refreshes, 0);
    env.container.visible = true;
    env.tick();
    assert.equal(refreshes, 1);
    env.tick();
    assert.equal(refreshes, 1);
    env.container.isConnected = false;
    env.observers.at(-1).callback();
    assert.equal(env.timers.size, 0);
});

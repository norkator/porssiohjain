/* Pörssiohjain Personal Use License v1.0. See LICENSE. */
window.renderHeatPumpControlPreview = function (container, data) {
    container.disposeHeatPumpPreview?.();
    const labels = data.labels;
    const locale = document.documentElement.lang || 'en';
    const time = new Intl.DateTimeFormat(locale, {
        timeZone: data.timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZoneName: 'shortOffset'
    });
    const axisTime = new Intl.DateTimeFormat(locale, {
        timeZone: data.timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
    });
    const formatTime = value => time.format(new Date(value));
    const periods = data.periods.filter(p => p.end > data.start && p.start < data.end);
    const prices = data.prices.filter(p => p[1] > data.start && p[0] < data.end);
    const color = period => period.power == null ? '#94a3b8' : period.power ? '#16a34a' : '#475569';
    const state = period => period.power == null ? 'unknown' : period.power ? 'on' : 'off';
    const clamp = value => Math.max(data.start, Math.min(data.end, value));
    const mode = period => {
        if (period.power !== true || !period.mode) return null;
        const value = period.mode.toLowerCase();
        const key = ['heat', 'cool', 'fan', 'dry', 'auto'].find(candidate => value.includes(candidate));
        return key ? labels[key] : period.mode;
    };
    let selected = periods.findIndex(period => period.start <= data.now && data.now < period.end);
    if (selected < 0) selected = periods.length ? 0 : -1;
    let disposed = false;
    let lastWidth = 0;
    let svg;
    const legend = document.createElement('div');
    legend.className = 'heat-pump-preview-legend';
    Object.assign(legend.style, { display: 'flex', flexWrap: 'wrap', gap: '8px 16px', fontSize: '12px' });
    for (const [key, fill] of [['on', '#16a34a'], ['off', '#475569'], ['unknown', '#94a3b8'], ['assumed', '#94a3b8']]) {
        const item = document.createElement('span');
        Object.assign(item.style, { display: 'inline-flex', alignItems: 'center', gap: '5px' });
        const swatch = document.createElement('span');
        Object.assign(swatch.style, { display: 'inline-block', flexShrink: '0', width: '13px', height: '10px', background: fill });
        if (key === 'assumed') {
            swatch.style.opacity = '0.4';
            swatch.style.outline = '1px dashed currentColor';
        }
        item.append(swatch, document.createTextNode(labels[key]));
        legend.append(item);
    }
    const details = document.createElement('div');
    details.className = 'heat-pump-preview-details';
    details.setAttribute('role', 'status');
    Object.assign(details.style, {
        border: '1px solid var(--lumo-contrast-20pct, #cbd5e1)', borderRadius: '6px', padding: '8px 10px',
        fontSize: '12px', lineHeight: '1.5', overflowWrap: 'anywhere', minHeight: '36px', boxSizing: 'border-box'
    });
    const intervalText = period => [
        formatTime(clamp(period.start)) + ' – ' + formatTime(clamp(period.end)),
        period.summary || labels[state(period)], mode(period), period.reason, period.assumed ? labels.assumed : null
    ].filter(Boolean).join(' · ');
    const updateSelection = index => {
        selected = index;
        details.textContent = periods[index] ? intervalText(periods[index]) : labels.unknown;
        svg?.querySelectorAll('.heat-pump-operation-segment').forEach((node, i) => {
            node.setAttribute('stroke', i === selected ? '#d97706' : 'none');
            node.setAttribute('aria-pressed', String(i === selected));
        });
    };
    const element = (tag, attributes = {}, text) => {
        const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
        for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, String(value));
        if (text != null) node.textContent = text;
        return node;
    };
    const draw = () => {
        if (disposed) return;
        const width = Math.max(280, container.clientWidth || 640);
        lastWidth = container.clientWidth;
        const left = 48;
        const right = width - 16;
        const x = value => left + (clamp(value) - data.start) / (data.end - data.start) * (right - left);
        const priceValues = prices.map(price => price[2]).filter(Number.isFinite);
        const minPrice = Math.min(0, ...priceValues);
        const maxPrice = Math.max(minPrice + 1, ...priceValues);
        const py = value => 135 - (value - minPrice) / (maxPrice - minPrice) * 95;
        const temperatures = periods.filter(period => period.power === true).map(period => period.temperature).filter(Number.isFinite);
        const minTemp = Math.min(16, ...temperatures);
        const maxTemp = Math.max(26, minTemp + 1, ...temperatures);
        const ty = value => 263 - (value - minTemp) / (maxTemp - minTemp) * 60;
        svg = element('svg', {
            viewBox: `0 0 ${width} 310`, role: 'group', 'aria-label': labels.power,
            class: 'heat-pump-preview-plot'
        });
        Object.assign(svg.style, {
            display: 'block', width: '100%', height: '310px', overflow: 'hidden',
            background: 'var(--lumo-contrast-5pct, #f1f5f9)', borderRadius: '8px'
        });
        const text = (label, tx, y, size = 13, anchor = 'start') => svg.append(element('text', {
            x: tx, y, 'font-size': size, 'text-anchor': anchor, fill: 'currentColor'
        }, label));
        text(labels.price, left, 22);
        for (const value of [minPrice, (minPrice + maxPrice) / 2, maxPrice]) {
            svg.append(element('line', { x1: left, x2: right, y1: py(value), y2: py(value), stroke: 'currentColor', opacity: '0.12' }));
            if (priceValues.length) text(value.toFixed(1), left - 8, py(value) + 4, 12, 'end');
        }
        let previousPrice;
        for (const price of prices) {
            if (!Number.isFinite(price[2])) { previousPrice = null; continue; }
            if (previousPrice && previousPrice[1] === price[0]) svg.append(element('line', {
                x1: x(price[0]), x2: x(price[0]), y1: py(previousPrice[2]), y2: py(price[2]), stroke: '#64748b', 'stroke-width': 2
            }));
            const line = element('line', {
                x1: x(price[0]), x2: x(price[1]), y1: py(price[2]), y2: py(price[2]),
                stroke: '#64748b', 'stroke-width': 2.5, class: 'heat-pump-price-segment'
            });
            line.append(element('title', {}, formatTime(clamp(price[0])) + ' – ' + formatTime(clamp(price[1]))
                + ' · ' + Number(price[2]).toFixed(2) + ' ' + labels.priceUnit));
            svg.append(line);
            previousPrice = price;
        }
        text(labels.power, left, 158);
        text(labels.temperature, left, 197);
        // Every state occupies the same horizontal lane. Its width represents time,
        // with the same ON/OFF/unknown colors used by hybrid-web.
        periods.forEach((period, index) => {
            const bar = element('rect', {
                x: x(period.start), y: 164, width: x(period.end) - x(period.start), height: 18,
                fill: color(period), opacity: period.assumed ? 0.35 : 0.9,
                stroke: index === selected ? '#d97706' : 'none', 'stroke-width': 2,
                class: 'heat-pump-operation-segment', 'data-state': state(period), 'data-assumed': !!period.assumed,
                role: 'button', tabindex: 0, 'aria-label': intervalText(period), 'aria-pressed': index === selected
            });
            bar.style.cursor = 'pointer';
            bar.append(element('title', {}, intervalText(period)));
            bar.addEventListener('mouseenter', () => updateSelection(index));
            bar.addEventListener('focus', () => updateSelection(index));
            bar.addEventListener('click', () => updateSelection(index));
            bar.addEventListener('keydown', event => {
                if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); updateSelection(index); }
            });
            svg.append(bar);
            if (period.power === true && Number.isFinite(period.temperature)) {
                svg.append(element('line', {
                    x1: x(period.start), x2: x(period.end), y1: ty(period.temperature), y2: ty(period.temperature),
                    stroke: '#ea580c', 'stroke-width': 3, 'stroke-dasharray': period.assumed ? '5 4' : 'none',
                    class: 'heat-pump-temperature-segment'
                }));
            }
        });
        for (const value of [minTemp, maxTemp]) text(value + '°', left - 8, ty(value) + 4, 12, 'end');
        const ticks = width < 520 ? 3 : 5;
        for (let i = 0; i < ticks; i++) {
            const timestamp = data.start + (data.end - data.start) * i / (ticks - 1);
            text(axisTime.format(new Date(timestamp)), x(timestamp), 292, 12, i === 0 ? 'start' : i === ticks - 1 ? 'end' : 'middle');
        }
        if (data.now >= data.start && data.now < data.end) svg.append(element('line', {
            x1: x(data.now), x2: x(data.now), y1: 32, y2: 271, stroke: '#dc2626', 'stroke-dasharray': '4 4',
            class: 'heat-pump-preview-now', 'aria-hidden': true, 'pointer-events': 'none'
        }));
        container.replaceChildren(svg, legend, details);
        updateSelection(selected);
    };
    Object.assign(container.style, { display: 'grid', gap: '8px', color: 'var(--lumo-body-text-color, #1e293b)' });
    const resize = new ResizeObserver(() => {
        if (container.clientWidth > 0 && container.clientWidth !== lastWidth) draw();
    });
    const removed = new MutationObserver(() => { if (!container.isConnected) dispose(); });
    const dispose = () => {
        if (disposed) return;
        disposed = true;
        resize.disconnect();
        removed.disconnect();
        if (container.disposeHeatPumpPreview === dispose) delete container.disposeHeatPumpPreview;
    };
    container.disposeHeatPumpPreview = dispose;
    draw();
    resize.observe(container);
    let root = container.getRootNode();
    while (root) {
        removed.observe(root, { childList: true, subtree: true });
        root = root.host ? root.host.getRootNode() : null;
    }
};

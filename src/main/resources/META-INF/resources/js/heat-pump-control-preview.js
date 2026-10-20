/* Pörssiohjain Personal Use License v1.0. See LICENSE. */
window.renderHeatPumpControlPreview = function (container, data) {
    const labels = data.labels;
    const time = new Intl.DateTimeFormat(document.documentElement.lang || 'en', {
        timeZone: data.timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZoneName: 'shortOffset'
    });
    const formatTime = value => time.format(new Date(Number(value)));
    const escape = value => String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[ch]);
    const periods = data.periods.filter(p => p.end > data.start && p.start < data.end);
    const prices = data.prices.filter(p => p[1] > data.start && p[0] < data.end);
    const points = (rows, value, start, end) => {
        const result = [];
        rows.forEach(row => {
            result.push([Math.max(data.start, start(row)), value(row)]);
            result.push([Math.min(data.end, end(row)), value(row)]);
        });
        return result.length ? result : [[data.start, null], [data.end, null]];
    };
    const pricePoints = points(prices, p => p[2], p => p[0], p => p[1]);
    const temperaturePoints = points(periods, p => p.power === true ? p.temperature : null, p => p.start, p => p.end);
    const pricesWithValues = pricePoints.map(point => point[1]).filter(Number.isFinite);
    const temperatures = temperaturePoints.map(point => point[1]).filter(Number.isFinite);
    const pricePadding = pricesWithValues.length
        ? Math.max(0.5, (Math.max(...pricesWithValues) - Math.min(...pricesWithValues)) * 0.08) : 0;
    const stateKey = period => {
        if (period.power == null) return null;
        if (period.assumed) return 'assumed';
        if (!period.power) return 'off';
        const mode = String(period.mode || '').toLowerCase();
        if (mode.includes('heat')) return 'heat';
        if (mode.includes('cool')) return 'cool';
        if (mode.includes('fan')) return 'fan';
        if (mode.includes('dry')) return 'dry';
        if (mode.includes('auto')) return 'auto';
        return 'on';
    };
    const stateLine = key => {
        const result = [];
        periods.forEach((period, index) => {
            const start = Math.max(data.start, period.start);
            const end = Math.min(data.end, period.end);
            if (stateKey(period) !== key) {
                result.push([start, null], [end, null]);
                return;
            }
            const previous = periods[index - 1];
            // The incoming state's color also draws its vertical ON/OFF transition.
            if (previous && previous.end === period.start && start === period.start
                    && previous.power != null && previous.power !== period.power) {
                result.push([start, previous.power ? 1 : 0]);
            }
            result.push([start, period.power ? 1 : 0], [end, period.power ? 1 : 0]);
        });
        return result.length ? result : [[data.start, null], [data.end, null]];
    };
    const modes = [
        { key: 'heat', color: '#15803d' }, { key: 'cool', color: '#0891b2' },
        { key: 'fan', color: '#7c3aed' }, { key: 'dry', color: '#b45309' },
        { key: 'auto', color: '#0d9488' }, { key: 'on', color: '#16a34a' },
        { key: 'off', color: '#64748b' }, { key: 'assumed', color: '#d97706' }
    ].filter(mode => periods.some(period => stateKey(period) === mode.key));
    const series = [
        { name: labels.price, data: pricePoints },
        { name: labels.temperature, data: temperaturePoints },
        ...modes.map(mode => ({ name: labels[mode.key], data: stateLine(mode.key) }))
    ];
    const plot = document.createElement('div');
    plot.style.width = '100%';
    container.replaceChildren(plot);
    const chart = new ApexCharts(plot, {
        chart: {
            id: data.id, type: 'line', height: container.clientHeight || 420,
            width: container.clientWidth || '100%', animations: { enabled: false },
            zoom: { enabled: false }, toolbar: { show: false }, fontFamily: 'inherit', parentHeightOffset: 0
        },
        series, colors: ['#2563eb', '#dc2626', ...modes.map(mode => mode.color)],
        stroke: { curve: 'stepline', width: [2, 3, ...modes.map(() => 3)],
            dashArray: [0, 0, ...modes.map(mode => mode.key === 'assumed' ? 6 : 0)] },
        // Reserve room for the price axis explicitly: shared state axes otherwise
        // let ApexCharts place the left labels outside its SVG viewport.
        markers: { size: 0 }, grid: { padding: { top: 20, bottom: 8, left: 64, right: 8 } },
        legend: { position: 'top', fontSize: '12px', showForNullSeries: false,
            itemMargin: { horizontal: 8, vertical: 4 } },
        xaxis: {
            type: 'datetime', min: data.start, max: data.end, tickAmount: 6,
            labels: { formatter: (value, timestamp) => formatTime(timestamp ?? value), hideOverlappingLabels: true },
            tooltip: { enabled: false }, title: { text: labels.time + ' · ' + data.timezone }
        },
        yaxis: [
            { seriesName: labels.price, title: { text: labels.priceUnit },
                min: pricesWithValues.length ? Math.min(...pricesWithValues) - pricePadding : 0,
                max: pricesWithValues.length ? Math.max(...pricesWithValues) + pricePadding : 1,
                labels: { minWidth: 36, maxWidth: 44, offsetX: 58,
                    formatter: value => pricesWithValues.length && Number.isFinite(value) ? value.toFixed(1) : '' } },
            { seriesName: labels.temperature, opposite: true, title: { text: '°C' },
                min: temperatures.length ? Math.floor(Math.min(...temperatures) - 1) : 0,
                max: temperatures.length ? Math.ceil(Math.max(...temperatures) + 1) : 30,
                labels: { minWidth: 28, maxWidth: 36,
                    formatter: value => temperatures.length && Number.isFinite(value) ? value.toFixed(1) : '' } },
            ...(modes.length ? [{
                seriesName: modes.map(mode => labels[mode.key]), showAlways: true, opposite: true,
                min: -0.5, max: 1.5, tickAmount: 4, forceNiceScale: false,
                labels: { minWidth: 32, maxWidth: 48,
                    formatter: value => Math.abs(value) < 0.001 ? labels.off : Math.abs(value - 1) < 0.001 ? labels.on : '' }
            }] : [])
        ],
        tooltip: {
            shared: false, intersect: false,
            custom: ({ seriesIndex, dataPointIndex, w }) => {
                const timestamp = w.config.series[seriesIndex]?.data[dataPointIndex]?.[0];
                if (timestamp == null) return '';
                const period = periods.find(p => timestamp >= p.start && timestamp < p.end);
                const price = prices.find(p => timestamp >= p[0] && timestamp < p[1]);
                const text = [formatTime(timestamp),
                    price?.[2] == null ? null : labels.price + ': ' + Number(price[2]).toFixed(2) + ' ' + labels.priceUnit,
                    period?.summary, period?.reason].filter(value => value != null);
                return '<div style="padding:8px;max-width:300px;white-space:normal">' + text.map(escape).join('<br>') + '</div>';
            }
        },
        responsive: [{ breakpoint: 620, options: {
            xaxis: { tickAmount: 3, labels: { style: { fontSize: '10px' } } },
            legend: { fontSize: '11px', itemMargin: { horizontal: 4, vertical: 3 } }
        } }]
    });
    let disposed = false;
    let lastWidth = container.clientWidth;
    let lastHeight = container.clientHeight;
    const resize = new ResizeObserver(() => {
        const width = container.clientWidth;
        const height = container.clientHeight;
        if (disposed || width <= 0 || height <= 0 || (width === lastWidth && height === lastHeight)) return;
        lastWidth = width;
        lastHeight = height;
        chart.updateOptions({ chart: { width, height } }, false, false, false);
    });
    const removed = new MutationObserver(() => { if (!container.isConnected) dispose(); });
    const dispose = () => {
        if (disposed) return;
        disposed = true;
        resize.disconnect();
        removed.disconnect();
        chart.destroy();
    };
    chart.render().then(() => {
        if (!container.isConnected) { dispose(); return; }
        resize.observe(container);
        let root = container.getRootNode();
        while (root) {
            removed.observe(root, { childList: true, subtree: true });
            root = root.host ? root.host.getRootNode() : null;
        }
    });
};

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
    const powerPoints = points(periods, p => p.power == null ? null : p.power ? 1 : 0, p => p.start, p => p.end);
    const ruleStarts = periods.map((period, index) => ({ period, index }))
        .filter(({ period }) => !period.assumed && period.start >= data.start && period.start < data.end);
    const temperatures = temperaturePoints.map(point => point[1]).filter(Number.isFinite);
    const temperatureMin = temperatures.length ? Math.floor(Math.min(...temperatures) - 1) : 0;
    const temperatureMax = temperatures.length ? Math.ceil(Math.max(...temperatures) + 1) : 30;
    const modeColor = p => {
        if (p.assumed) return '#d69e2e';
        if (p.power === false) return '#94a3b8';
        if (p.power == null) return '#cbd5e1';
        const mode = String(p.mode || '').toLowerCase();
        if (mode.includes('heat')) return '#ef6c00';
        if (mode.includes('cool')) return '#0288d1';
        return '#388e3c';
    };
    const now = data.now >= data.start && data.now < data.end ? [{
        x: data.now, borderColor: '#b91c1c', strokeDashArray: 4,
        label: { text: labels.now, style: { color: '#fff', background: '#b91c1c' } }
    }] : [];
    const xaxis = {
        type: 'datetime', min: data.start, max: data.end, tickAmount: 6,
        labels: { formatter: (value, timestamp) => formatTime(timestamp ?? value), hideOverlappingLabels: true },
        tooltip: { enabled: false }, title: { text: labels.time + ' · ' + data.timezone }
    };
    const base = {
        chart: {
            type: 'line', group: data.id, height: 250, width: '100%',
            animations: { enabled: false }, zoom: { enabled: false }, toolbar: { show: false },
            fontFamily: 'inherit', parentHeightOffset: 0
        },
        xaxis, stroke: { curve: 'stepline', width: 2 }, markers: { size: 0 },
        legend: { position: 'top' }, grid: { padding: { left: 0, right: 0 } }, noData: { text: '—' },
        responsive: [{ breakpoint: 620, options: { xaxis: { tickAmount: 3, labels: { style: { fontSize: '10px' } } } } }]
    };
    const priceDiv = document.createElement('div');
    const stateDiv = document.createElement('div');
    container.replaceChildren(priceDiv, stateDiv);
    const priceChart = new ApexCharts(priceDiv, {
        ...base, chart: { ...base.chart, id: data.id + '-price' },
        series: [{ name: labels.price, data: pricePoints }], colors: ['#2563eb'],
        yaxis: { title: { text: labels.price }, labels: {
            minWidth: 48, maxWidth: 48, formatter: value => Number.isFinite(value) ? value.toFixed(1) : ''
        } },
        annotations: { xaxis: now },
        tooltip: { x: { formatter: formatTime }, y: { formatter: value => value == null ? '—' : value.toFixed(2) + ' c/kWh' } }
    });
    const stateChart = new ApexCharts(stateDiv, {
        ...base, chart: { ...base.chart, id: data.id + '-state', height: 290 },
        series: [{ name: labels.temperature, data: temperaturePoints }, { name: labels.power, data: powerPoints }],
        colors: ['#dc2626', '#15803d'], stroke: { curve: 'stepline', width: [3, 2] },
        markers: { size: 0, discrete: ruleStarts.filter(({ period }) => period.power != null).map(({ period, index }) => ({
            seriesIndex: period.power && Number.isFinite(period.temperature) ? 0 : 1,
            dataPointIndex: index * 2, size: 4,
            fillColor: period.power && Number.isFinite(period.temperature) ? '#dc2626' : '#15803d', strokeColor: '#fff'
        })) },
        yaxis: [
            { seriesName: labels.temperature, min: temperatureMin, max: temperatureMax,
                title: { text: labels.temperature }, labels: {
                minWidth: 48, maxWidth: 48,
                formatter: value => temperatures.length && Number.isFinite(value) ? value.toFixed(1) : ''
            } },
            { seriesName: labels.power, opposite: true, min: 0, max: 1, tickAmount: 1,
                labels: { minWidth: 48, maxWidth: 48, formatter: value => value === 0 ? labels.off : value === 1 ? labels.on : '' } }
        ],
        annotations: { xaxis: [...periods.map(p => ({
            x: Math.max(p.start, data.start), x2: Math.min(p.end, data.end),
            fillColor: modeColor(p), opacity: p.assumed ? 0.12 : 0.18, borderWidth: 0
        })), ...ruleStarts.filter(({ period }) => period.start !== data.now).map(({ period }) => ({
            x: period.start, borderColor: '#475569', strokeDashArray: 3, opacity: 0.7
        })), ...now] },
        tooltip: {
            shared: true, intersect: false,
            custom: ({ seriesIndex, dataPointIndex, w }) => {
                const timestamp = w.config.series[seriesIndex].data[dataPointIndex]?.[0];
                const period = periods.find(p => timestamp >= p.start && timestamp < p.end);
                if (!period) return '';
                return '<div style="padding:8px;max-width:320px;white-space:normal">' +
                    '<strong>' + escape(formatTime(timestamp)) + '</strong><br>' +
                    escape(period.summary) + '<br>' + escape(period.reason) + '</div>';
            }
        }
    });
    const charts = [priceChart, stateChart];
    let disposed = false;
    // Different price/temperature/power axes reserve different margins. Align the actual
    // plot areas so a price change and a requested state change share the same pixel.
    const alignPlots = async () => {
        if (disposed) return;
        const margins = charts.map(chart => ({
            left: chart.w.globals.translateX,
            right: container.clientWidth - chart.w.globals.translateX - chart.w.globals.gridWidth
        }));
        const left = Math.max(...margins.map(margin => margin.left));
        const right = Math.max(...margins.map(margin => margin.right));
        await Promise.all(charts.map((chart, index) => {
            const extraLeft = left - margins[index].left;
            const extraRight = right - margins[index].right;
            if (extraLeft < 0.5 && extraRight < 0.5) return;
            return chart.updateOptions({ grid: { padding: {
                left: chart.w.config.grid.padding.left + extraLeft,
                right: chart.w.config.grid.padding.right + extraRight
            } } }, false, false, false);
        }));
    };
    const dispose = () => {
        if (disposed) return;
        disposed = true;
        resize.disconnect();
        removed.disconnect();
        charts.forEach(chart => chart.destroy());
    };
    let lastWidth = container.clientWidth;
    const resize = new ResizeObserver(() => {
        const width = container.clientWidth;
        if (disposed || width <= 0 || width === lastWidth) return;
        lastWidth = width;
        Promise.all(charts.map(chart => chart.updateOptions({ chart: { width } }, false, false, false)))
            .then(alignPlots);
    });
    const removed = new MutationObserver(() => { if (!container.isConnected) dispose(); });
    Promise.all(charts.map(chart => chart.render())).then(alignPlots).then(() => {
        if (!container.isConnected) { dispose(); return; }
        resize.observe(container);
        // Dialog content can live inside shadow DOM. Watch each containing root so
        // changing the selected pump/day also releases the previous chart instances.
        let root = container.getRootNode();
        while (root) {
            removed.observe(root, { childList: true, subtree: true });
            root = root.host ? root.host.getRootNode() : null;
        }
    });
};

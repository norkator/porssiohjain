/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */

window.renderForecastChart = (container, config) => {
    container.forecastCleanup?.();
    const { times, values, end, zone, locale, prices } = config;
    const numbers = new Intl.NumberFormat(locale, { minimumFractionDigits: prices ? 2 : 0, maximumFractionDigits: prices ? 2 : 0 });
    const time = new Intl.DateTimeFormat(locale, { timeZone: zone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    const timestamp = new Intl.DateTimeFormat(locale, {
        timeZone: zone, weekday: 'short', day: 'numeric', month: 'short',
        hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZoneName: 'short'
    });
    const dayAnnotations = config.days.map((day, index) => ({
        x: day.start, x2: day.end, fillColor: index % 2 ? '#e2e8f0' : '#ffffff',
        opacity: 0.3, borderColor: '#94a3b8', strokeDashArray: 4,
        label: { text: day.label, orientation: 'horizontal', position: 'top', offsetY: 22,
            style: { color: '#334155', background: '#f1f5f9', fontSize: '11px' } }
    }));
    const annotations = now => ({ xaxis: [
        ...dayAnnotations,
        ...(now >= times[0] && now < end ? [{
            x: now, borderColor: '#c62828', borderWidth: 2, strokeDashArray: 4,
            label: { text: `${config.nowLabel} ${time.format(now)}`, orientation: 'horizontal',
                style: { color: '#fff', background: '#c62828' } }
        }] : [])
    ] });
    const data = [];
    times.forEach((x, index) => {
        // Leave missing market quarters empty instead of implying an uninterrupted price.
        if (prices && index > 0 && x > times[index - 1] + 15 * 60000) data.push([times[index - 1] + 15 * 60000, null]);
        data.push([x, values[index]]);
    });
    data.push([end, values[values.length - 1]]);
    const color = prices ? '#1d4ed8' : '#007c89';
    const chart = new ApexCharts(container, {
        chart: { type: 'area', height: 340, fontFamily: 'Tahoma, Arial, sans-serif',
            animations: { enabled: false }, toolbar: { show: true }, zoom: { enabled: true, autoScaleYaxis: true } },
        series: [{ name: config.name, data }],
        colors: [color],
        stroke: { width: 2, curve: prices ? 'stepline' : 'straight' },
        fill: { type: 'gradient', gradient: { opacityFrom: 0.3, opacityTo: 0.04 } },
        dataLabels: { enabled: false },
        markers: { size: 0, hover: { size: 5 } },
        grid: { borderColor: '#d1d5db', padding: { top: 12, right: 12 } },
        xaxis: { type: 'datetime', min: times[0], max: end, tickAmount: 8,
            title: { text: zone },
            labels: { hideOverlappingLabels: true, formatter: (value, millis) => time.format(millis ?? Number(value)) } },
        yaxis: { ...(prices ? {} : { min: 0 }), forceNiceScale: true, title: { text: config.unit },
            labels: { minWidth: 40, formatter: value => Number.isFinite(value) ? numbers.format(value) : '' } },
        tooltip: { x: { formatter: value => timestamp.format(value) },
            y: { formatter: value => value == null ? '–' : `${numbers.format(value)} ${config.unit}` } },
        annotations: annotations(Date.now()),
        responsive: [{ breakpoint: 620, options: {
            chart: { toolbar: { show: false } }, xaxis: { tickAmount: 4 },
            yaxis: { title: { text: '' }, labels: { minWidth: 32, maxWidth: 54,
                formatter: value => Number.isFinite(value) ? numbers.format(value) : '' } }
        } }]
    });
    container.chartInstance = chart;
    let timer;
    let disposed = false;
    const observer = new MutationObserver(() => { if (!container.isConnected) cleanup(); });
    const cleanup = () => {
        if (disposed) return;
        disposed = true;
        window.clearInterval(timer);
        observer.disconnect();
        chart.destroy();
        if (container.chartInstance === chart) container.chartInstance = null;
    };
    container.forecastCleanup = cleanup;
    const updateCurrent = () => {
        if (!prices) return;
        const now = Date.now();
        const index = times.findIndex(start => start <= now && now < start + 15 * 60000);
        const current = container.closest('.forecast-card')?.querySelector('[data-forecast-current]');
        if (current) current.textContent = index < 0 ? '–' : numbers.format(values[index]);
    };
    chart.render().then(() => {
        if (disposed) return;
        if (!container.isConnected) { cleanup(); return; }
        updateCurrent();
        timer = window.setInterval(() => {
            if (container.getClientRects().length === 0) return;
            chart.updateOptions({ annotations: annotations(Date.now()) }, false, false);
            updateCurrent();
        }, 60000);
        observer.observe(document, { childList: true, subtree: true });
    });
};

window.initForecastsView = container => {
    container.forecastsCleanup?.();
    let refreshedQuarter = Math.floor(Date.now() / 900000);
    const refresh = () => {
        const quarter = Math.floor(Date.now() / 900000);
        if (quarter === refreshedQuarter || document.hidden || container.getClientRects().length === 0) return;
        refreshedQuarter = quarter;
        container.dispatchEvent(new CustomEvent('forecast-refresh'));
    };
    const timer = window.setInterval(refresh, 60000);
    document.addEventListener('visibilitychange', refresh);
    const observer = new MutationObserver(() => { if (!container.isConnected) cleanup(); });
    const cleanup = () => {
        window.clearInterval(timer);
        document.removeEventListener('visibilitychange', refresh);
        observer.disconnect();
    };
    container.forecastsCleanup = cleanup;
    observer.observe(document, { childList: true, subtree: true });
};

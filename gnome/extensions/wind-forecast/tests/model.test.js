import {currentForecast, daySummary, formatChange, formatMegawatts, pointAtTime, visibleChart} from '../shell/model.js';

let checks = 0;
function equal(actual, expected, message) {
    checks++;
    if (actual !== expected)
        throw new Error(`${message}: expected ${expected}, got ${actual}`);
}
const first = {timestamp: 0, end: 900_000, megawatts: 1000};
const second = {timestamp: 900_000, end: 1_800_000, megawatts: 2000};
const tomorrowPoint = {timestamp: 86_400_000, end: 87_300_000, megawatts: 3000};
const snapshot = {
    points: [first, second, tomorrowPoint],
    days: [
        {start: 0, end: 86_400_000, average: 1500, complete: false},
        {start: 86_400_000, end: 172_800_000, average: 3000, complete: false},
    ],
};
equal(currentForecast(snapshot, 0).megawatts, 1000, 'Includes the exact start');
equal(currentForecast(snapshot, 450_000).megawatts, 1500, 'Interpolates the current estimate like the web chart');
equal(currentForecast(snapshot, 900_000).megawatts, 2000, 'Switches exactly at a boundary');
equal(currentForecast(snapshot, 1_799_999).megawatts, 2000, 'Uses the declared final interval');
equal(currentForecast(snapshot, 1_800_000), null, 'No forecast beyond the declared end');
equal(currentForecast(snapshot, -1), null, 'No estimate before forecast starts');
equal(currentForecast(null, 0), null, 'No estimate during loading');
equal(pointAtTime(snapshot, 3_600_000), null, 'Inspection does not bridge gaps');
equal(pointAtTime(snapshot, 0), first, 'Inspection uses the actual sample');
equal(daySummary(snapshot, 0).today.average, 1500, 'Today average follows the local day');
equal(daySummary(snapshot, 0).tomorrow.average, 3000, 'Tomorrow average follows the local day');
equal(daySummary(snapshot, 0).changePercent, 100, 'Increase is positive');
const drop = {...snapshot, days: snapshot.days.map((day, index) => ({...day, average: index ? 1000 : 2000}))};
equal(daySummary(drop, 0).changePercent, -50, 'Drop is negative');
equal(daySummary({...snapshot, days: snapshot.days.map(day => ({...day, average: 0}))}, 0).changePercent, null, 'No percentage division by zero');
equal(daySummary(snapshot, 86_400_000).today.average, 3000, 'Cached tomorrow becomes today at midnight');
equal(daySummary(snapshot, 86_400_000).tomorrow, null, 'Expired summaries never carry into another day');
equal(visibleChart(snapshot, 0).end, tomorrowPoint.end, 'Chart shows the available forecast horizon');
equal(visibleChart(snapshot, 86_400_000).points.length, 1, 'Yesterday drops out at midnight');
equal(visibleChart(snapshot, 87_300_000), null, 'Expired chart is unavailable');
equal(visibleChart({days: snapshot.days, points: []}, 0), null, 'Empty forecasts have no chart');
equal(visibleChart(null, 0), null, 'Loading has no chart');
equal(formatMegawatts(null), '—', 'Missing forecast is not zero');
equal(formatMegawatts(NaN), '—', 'Invalid forecast is not displayed');
equal(formatMegawatts(0), '0', 'Genuine zero production is displayed');
equal(formatChange(null), '—', 'Missing comparison stays unavailable');
const dst = {points: [], days: [
    {start: 0, end: 23 * 3_600_000, average: 10},
    {start: 23 * 3_600_000, end: 47 * 3_600_000, average: 20},
]};
equal(daySummary(dst, 23 * 3_600_000).today.average, 20, 'Rollover uses the DST day end, not a fixed 24 hours');
print(`Wind menu model: ${checks} checks passed`);

import {currentPoint, formatPrice, pointAtTime, visibleChart} from '../shell/model.js';

let checks = 0;
function equal(actual, expected, message) {
    checks++;
    if (actual !== expected)
        throw new Error(`${message}: expected ${expected}, got ${actual}`);
}

const first = {timestamp: 0, end: 900_000, price: -2};
const second = {timestamp: 900_000, end: 1_800_000, price: 4};
const day = {
    start: 0, end: 86_400_000, minimum: -2, maximum: 4,
    points: [first, second],
};
const snapshot = {days: [day]};
equal(currentPoint(snapshot, 0), first, 'Includes exactly midnight');
equal(currentPoint(snapshot, 899_999), first, 'Includes interval before its end');
equal(currentPoint(snapshot, 900_000), second, 'Switches exactly at interval boundary');
equal(currentPoint(snapshot, 1_800_000), null, 'Never carries the final price into a missing interval');
equal(currentPoint(snapshot, -1), null, 'Future prices are not current');
equal(currentPoint(null, 0), null, 'Loading state has no price');

const tomorrow = {
    start: 86_400_000, end: 172_800_000, minimum: 1, maximum: 1,
    points: [{timestamp: 86_400_000, end: 87_300_000, price: 1}],
};
const combined = {days: [tomorrow, day]};
const chart = visibleChart(combined, 0);
equal(chart.start, 0, 'Days are ordered by timestamp');
equal(chart.end, tomorrow.end, 'Today and tomorrow share one continuous axis');
equal(chart.minimum, -2, 'Negative prices are preserved');
equal(chart.average, 1, 'Displayed horizon average includes both days');
equal(pointAtTime(chart, 3_600_000), null, 'Inspection keeps gaps empty');
equal(pointAtTime(chart, 0), first, 'Inspection includes local midnight');
equal(visibleChart(combined, 86_400_000).days.length, 1, 'Drops yesterday at local midnight');
equal(currentPoint(combined, 86_400_000), tomorrow.points[0], 'Cached tomorrow becomes today');
equal(visibleChart(combined, 172_800_000), null, 'Expired charts are not displayed as current');
equal(visibleChart({days: [{...day, points: []}]}, 0), null, 'Empty days have no graph');
equal(formatPrice(null), '—', 'Missing price is not zero');
equal(formatPrice(undefined), '—', 'Loading price is not zero');
equal(formatPrice(NaN), '—', 'Invalid price is not displayed');
print(`Price menu model: ${checks} checks passed`);

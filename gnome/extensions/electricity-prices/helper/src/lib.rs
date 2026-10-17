/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

use chrono::{DateTime, Days, Duration, NaiveDate, TimeZone, Utc};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

pub mod api;

pub const MARKETS: &[&str] = &[
    "EE", "LT", "LV", "AT", "BE", "FR", "GER", "NL", "PL", "DK1", "DK2", "FI", "NO1", "NO2", "NO3",
    "NO4", "NO5", "SE1", "SE2", "SE3", "SE4", "BG", "TEL",
];

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ApiChart {
    pub date: NaiveDate,
    pub timezone: String,
    pub resolution_minutes: u32,
    pub points: Vec<ApiPoint>,
}

#[derive(Debug, Deserialize)]
pub struct ApiPoint {
    pub timestamp: DateTime<Utc>,
    pub price: f64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub timezone: String,
    pub market: String,
    pub fetched_at: i64,
    pub days: Vec<ChartDay>,
    pub errors: Vec<DayError>,
}

#[derive(Debug, Serialize)]
pub struct DayError {
    pub day: &'static str,
    pub message: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartDay {
    pub date: NaiveDate,
    pub start: i64,
    pub end: i64,
    pub resolution_minutes: u32,
    pub complete: bool,
    pub minimum: Option<f64>,
    pub average: Option<f64>,
    pub maximum: Option<f64>,
    pub points: Vec<ChartPoint>,
    pub ticks: Vec<TimeTick>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartPoint {
    pub timestamp: i64,
    pub end: i64,
    pub price: f64,
    pub time_label: String,
}

#[derive(Debug, Serialize)]
pub struct TimeTick {
    pub timestamp: i64,
    pub label: String,
}

// Resolve the start of the local date, including zones that advance at midnight.
fn day_start(date: NaiveDate, zone: Tz) -> Result<DateTime<Tz>, String> {
    let midnight = date.and_hms_opt(0, 0, 0).ok_or("Invalid date")?;
    for minute in 0..180 {
        if let Some(start) = zone
            .from_local_datetime(&(midnight + Duration::minutes(minute)))
            .earliest()
        {
            return Ok(start);
        }
    }
    Err(format!("No local start of day for {date} in {zone}"))
}

pub fn normalize_chart(
    chart: ApiChart,
    zone: Tz,
    expected_date: NaiveDate,
) -> Result<ChartDay, String> {
    if chart.date != expected_date || chart.timezone.parse::<Tz>().ok() != Some(zone) {
        return Err("API returned a different date or timezone".into());
    }
    if !(1..=60).contains(&chart.resolution_minutes) || chart.points.len() > 1500 {
        return Err("Unsupported price resolution or chart size".into());
    }

    let start = day_start(expected_date, zone)?.timestamp_millis();
    let next_date = expected_date
        .checked_add_days(Days::new(1))
        .ok_or("Invalid next date")?;
    let end = day_start(next_date, zone)?.timestamp_millis();
    let duration = i64::from(chart.resolution_minutes) * 60_000;
    let mut unique = BTreeMap::new();
    for point in chart.points {
        let timestamp = point.timestamp.timestamp_millis();
        if !point.price.is_finite() || timestamp < start || timestamp >= end {
            return Err("Invalid price or timestamp outside the local day".into());
        }
        if let Some(previous) = unique.insert(timestamp, point.price) {
            if previous != point.price {
                return Err("Conflicting prices for the same timestamp".into());
            }
        }
    }

    let mut points = Vec::with_capacity(unique.len());
    let mut previous_end = start;
    let mut complete = !unique.is_empty();
    let mut total = 0.0;
    let mut covered_millis = 0;
    for (timestamp, price) in unique {
        if timestamp < previous_end {
            return Err("Overlapping price intervals".into());
        }
        complete &= timestamp == previous_end;
        let point_end = (timestamp + duration).min(end);
        let local = DateTime::from_timestamp_millis(timestamp)
            .ok_or("Invalid timestamp")?
            .with_timezone(&zone);
        total += price * (point_end - timestamp) as f64;
        covered_millis += point_end - timestamp;
        points.push(ChartPoint {
            timestamp,
            end: point_end,
            price,
            time_label: local.format("%H:%M %Z").to_string(),
        });
        previous_end = point_end;
    }
    complete &= previous_end == end;
    let minimum = points.iter().map(|p| p.price).reduce(f64::min);
    let maximum = points.iter().map(|p| p.price).reduce(f64::max);
    let average = (covered_millis > 0).then(|| total / covered_millis as f64);
    let mut ticks = Vec::new();
    let mut tick = start;
    while tick < end {
        let local = DateTime::from_timestamp_millis(tick)
            .ok_or("Invalid tick timestamp")?
            .with_timezone(&zone);
        if local.format("%H").to_string().parse::<u32>().unwrap_or(0) % 6 == 0 {
            ticks.push(TimeTick {
                timestamp: tick,
                label: local.format("%H:%M").to_string(),
            });
        }
        tick += 3_600_000;
    }

    Ok(ChartDay {
        date: expected_date,
        start,
        end,
        resolution_minutes: chart.resolution_minutes,
        complete,
        minimum,
        average,
        maximum,
        points,
        ticks,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn full_day(date: &str, zone: Tz) -> ApiChart {
        let date = date.parse::<NaiveDate>().unwrap();
        let start = day_start(date, zone).unwrap().with_timezone(&Utc);
        let end = day_start(date.succ_opt().unwrap(), zone)
            .unwrap()
            .with_timezone(&Utc);
        let count = (end - start).num_minutes() / 15;
        ApiChart {
            date,
            timezone: zone.to_string(),
            resolution_minutes: 15,
            points: (0..count)
                .map(|i| ApiPoint {
                    timestamp: start + Duration::minutes(i * 15),
                    price: i as f64 - 10.0,
                })
                .collect(),
        }
    }

    #[test]
    fn helsinki_midnight_and_dst_days_use_authoritative_instants() {
        let zone = chrono_tz::Europe::Helsinki;
        for (date, expected_count, utc_start) in [
            ("2026-07-01", 96, "2026-06-30T21:00:00Z"),
            ("2026-01-01", 96, "2025-12-31T22:00:00Z"),
            ("2026-03-29", 92, "2026-03-28T22:00:00Z"),
            ("2026-10-25", 100, "2026-10-24T21:00:00Z"),
        ] {
            let chart = full_day(date, zone);
            let day = normalize_chart(chart, zone, date.parse().unwrap()).unwrap();
            assert!(day.complete);
            assert_eq!(day.points.len(), expected_count);
            assert_eq!(
                day.start,
                utc_start
                    .parse::<DateTime<Utc>>()
                    .unwrap()
                    .timestamp_millis()
            );
            assert_eq!(day.points.first().unwrap().timestamp, day.start);
            assert_eq!(day.points.last().unwrap().end, day.end);
            assert_eq!(day.minimum, Some(-10.0));
        }
    }

    #[test]
    fn autumn_repeated_hour_keeps_both_offsets() {
        let zone = chrono_tz::Europe::Helsinki;
        let chart = full_day("2026-10-25", zone);
        let day = normalize_chart(chart, zone, "2026-10-25".parse().unwrap()).unwrap();
        let repeated: Vec<_> = day
            .points
            .iter()
            .filter(|p| p.time_label.starts_with("03:00"))
            .collect();
        assert_eq!(repeated.len(), 2);
        assert_ne!(repeated[0].time_label, repeated[1].time_label);
    }

    #[test]
    fn gaps_are_explicit_and_do_not_extend_previous_price() {
        let zone = chrono_tz::Europe::Helsinki;
        let mut chart = full_day("2026-07-01", zone);
        let removed = chart.points.remove(20);
        chart.points.reverse();
        let day = normalize_chart(chart, zone, "2026-07-01".parse().unwrap()).unwrap();
        assert!(!day.complete);
        assert_eq!(day.points[19].end, removed.timestamp.timestamp_millis());
        assert_eq!(day.points.len(), 95);
    }

    #[test]
    fn empty_day_is_unavailable_rather_than_zero_price() {
        let zone = chrono_tz::Europe::Helsinki;
        let mut chart = full_day("2026-07-01", zone);
        chart.points.clear();
        let day = normalize_chart(chart, zone, "2026-07-01".parse().unwrap()).unwrap();
        assert!(!day.complete);
        assert_eq!(day.minimum, None);
        assert_eq!(day.average, None);
        assert_eq!(day.maximum, None);
    }

    #[test]
    fn conflicting_duplicates_wrong_dates_and_nonfinite_prices_are_rejected() {
        let zone = chrono_tz::Europe::Helsinki;
        let date = "2026-07-01".parse().unwrap();
        let mut duplicate = full_day("2026-07-01", zone);
        duplicate.points.push(ApiPoint {
            timestamp: duplicate.points[0].timestamp,
            price: 99.0,
        });
        assert!(normalize_chart(duplicate, zone, date).is_err());
        assert!(normalize_chart(full_day("2026-07-02", zone), zone, date).is_err());
        let mut invalid = full_day("2026-07-01", zone);
        invalid.points[0].price = f64::NAN;
        assert!(normalize_chart(invalid, zone, date).is_err());
        let mut outside = full_day("2026-07-01", zone);
        outside.points[0].timestamp -= Duration::minutes(15);
        assert!(normalize_chart(outside, zone, date).is_err());
    }
}

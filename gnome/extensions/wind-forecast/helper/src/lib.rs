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

#[derive(Debug, Deserialize)]
pub struct ApiForecast {
    pub timezone: String,
    pub points: Vec<ApiPoint>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ApiPoint {
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub megawatts: f64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub timezone: String,
    pub fetched_at: i64,
    pub days: Vec<ForecastDay>,
    pub points: Vec<ForecastPoint>,
}

#[derive(Debug, Serialize)]
pub struct ForecastDay {
    pub date: NaiveDate,
    pub start: i64,
    pub end: i64,
    pub average: Option<f64>,
    pub complete: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ForecastPoint {
    pub timestamp: i64,
    pub end: i64,
    pub megawatts: f64,
    pub time_label: String,
}

// Find the first valid local minute, including zones that advance at midnight.
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

pub fn normalize_forecast(
    forecast: ApiForecast,
    zone: Tz,
    now: DateTime<Utc>,
) -> Result<Snapshot, String> {
    if forecast.timezone.parse::<Tz>().ok() != Some(zone) {
        return Err("API returned a different timezone".into());
    }
    if forecast.points.len() > 10_000 {
        return Err("Too many forecast points".into());
    }
    let today = now.with_timezone(&zone).date_naive();
    let today_start = day_start(today, zone)?.timestamp_millis();
    let limit_date = today
        .checked_add_days(Days::new(14))
        .ok_or("Invalid horizon")?;
    let limit = day_start(limit_date, zone)?.timestamp_millis();
    let mut ordered = BTreeMap::<i64, ForecastPoint>::new();
    for point in forecast.points {
        let timestamp = point.start_time.timestamp_millis();
        let end = point.end_time.timestamp_millis();
        if end <= timestamp || !point.megawatts.is_finite() || point.megawatts < 0.0 {
            return Err("Invalid forecast interval or megawatt value".into());
        }
        if end <= today_start {
            continue;
        }
        if end > limit {
            return Err("Forecast exceeds the 14-day horizon".into());
        }
        let normalized = ForecastPoint {
            timestamp,
            end,
            megawatts: point.megawatts,
            time_label: point
                .start_time
                .with_timezone(&zone)
                .format("%H:%M %Z")
                .to_string(),
        };
        if ordered
            .insert(timestamp, normalized)
            .is_some_and(|previous| previous.end != end || previous.megawatts != point.megawatts)
        {
            return Err("Conflicting forecast points".into());
        }
    }
    let points: Vec<_> = ordered.into_values().collect();
    if points
        .windows(2)
        .any(|pair| pair[0].end > pair[1].timestamp)
    {
        return Err("Overlapping forecast intervals".into());
    }

    // Calculate each date separately, so cached tomorrow summaries can become
    // today at midnight. Match the web API's mean of the available samples;
    // partial days are identified separately, never padded with zeroes.
    let tomorrow = today
        .checked_add_days(Days::new(1))
        .ok_or("Invalid tomorrow")?;
    let last_date = points.last().map_or(tomorrow, |point| {
        DateTime::from_timestamp_millis(point.end - 1)
            .unwrap()
            .with_timezone(&zone)
            .date_naive()
            .max(tomorrow)
    });
    let mut days = Vec::new();
    let mut date = today;
    while date <= last_date {
        let next = date
            .checked_add_days(Days::new(1))
            .ok_or("Invalid next date")?;
        let start = day_start(date, zone)?.timestamp_millis();
        let end = day_start(next, zone)?.timestamp_millis();
        let samples: Vec<_> = points
            .iter()
            .filter(|point| start <= point.timestamp && point.timestamp < end)
            .collect();
        let average = if samples.is_empty() {
            None
        } else {
            Some(samples.iter().map(|point| point.megawatts).sum::<f64>() / samples.len() as f64)
        };
        let mut covered_until = start;
        let mut complete = true;
        for point in points
            .iter()
            .filter(|point| point.end > start && point.timestamp < end)
        {
            if point.timestamp > covered_until {
                complete = false;
            }
            covered_until = point.end.min(end);
        }
        complete &= covered_until == end;
        days.push(ForecastDay {
            date,
            start,
            end,
            average,
            complete,
        });
        date = next;
    }
    Ok(Snapshot {
        timezone: zone.to_string(),
        fetched_at: now.timestamp_millis(),
        days,
        points,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instant(value: &str) -> DateTime<Utc> {
        value.parse().unwrap()
    }
    fn point(start: &str, end: &str, megawatts: f64) -> ApiPoint {
        ApiPoint {
            start_time: instant(start),
            end_time: instant(end),
            megawatts,
        }
    }
    fn normalize(points: Vec<ApiPoint>, now: &str) -> Result<Snapshot, String> {
        normalize_forecast(
            ApiForecast {
                timezone: "Europe/Helsinki".into(),
                points,
            },
            chrono_tz::Europe::Helsinki,
            instant(now),
        )
    }

    #[test]
    fn local_midnight_sorts_samples_and_keeps_real_zero() {
        let result = normalize(
            vec![
                point("2026-07-01T21:00:00Z", "2026-07-01T21:15:00Z", 1000.0),
                point("2026-06-30T21:00:00Z", "2026-06-30T21:15:00Z", 0.0),
                point("2026-06-30T21:15:00Z", "2026-06-30T21:30:00Z", 2000.0),
            ],
            "2026-07-01T10:00:00Z",
        )
        .unwrap();
        assert_eq!(
            result.days[0].start,
            instant("2026-06-30T21:00:00Z").timestamp_millis()
        );
        assert_eq!(result.days[0].average, Some(1000.0));
        assert_eq!(result.days[1].average, Some(1000.0));
        assert_eq!(result.points[0].megawatts, 0.0);
        assert!(!result.days[0].complete);
    }

    #[test]
    fn dst_days_use_local_boundaries_and_repeated_hours_keep_offsets() {
        for (now, expected_hours) in [("2026-03-29T10:00:00Z", 23), ("2026-10-25T10:00:00Z", 25)] {
            let snapshot = normalize(vec![], now).unwrap();
            assert_eq!(
                snapshot.days[0].end - snapshot.days[0].start,
                expected_hours * 3_600_000
            );
        }
        let snapshot = normalize(
            vec![
                point("2026-10-25T00:00:00Z", "2026-10-25T00:15:00Z", 1.0),
                point("2026-10-25T01:00:00Z", "2026-10-25T01:15:00Z", 2.0),
            ],
            "2026-10-25T00:00:00Z",
        )
        .unwrap();
        assert_ne!(snapshot.points[0].time_label, snapshot.points[1].time_label);
    }

    #[test]
    fn gaps_and_missing_days_are_not_complete_or_zero() {
        let snapshot = normalize(
            vec![
                point("2026-06-30T21:00:00Z", "2026-07-01T09:00:00Z", 1000.0),
                point("2026-07-01T10:00:00Z", "2026-07-01T21:00:00Z", 3000.0),
            ],
            "2026-07-01T10:00:00Z",
        )
        .unwrap();
        assert!(!snapshot.days[0].complete);
        assert_eq!(snapshot.days[0].average, Some(2000.0));
        assert_eq!(snapshot.days[1].average, None);
        assert!(!snapshot.days[1].complete);
    }

    #[test]
    fn complete_day_covers_authoritative_intervals() {
        let snapshot = normalize(
            vec![
                point("2026-06-30T21:00:00Z", "2026-07-01T09:00:00Z", 1000.0),
                point("2026-07-01T09:00:00Z", "2026-07-01T21:00:00Z", 3000.0),
            ],
            "2026-07-01T10:00:00Z",
        )
        .unwrap();
        assert!(snapshot.days[0].complete);
    }

    #[test]
    fn stale_history_is_discarded_and_empty_response_stays_empty() {
        let snapshot = normalize(
            vec![point("2026-06-29T21:00:00Z", "2026-06-29T21:15:00Z", 5.0)],
            "2026-07-01T10:00:00Z",
        )
        .unwrap();
        assert!(snapshot.points.is_empty());
        assert_eq!(snapshot.days.len(), 2);
        assert!(snapshot.days.iter().all(|day| day.average.is_none()));
    }

    #[test]
    fn invalid_values_conflicts_overlap_and_timezone_are_rejected() {
        for value in [-1.0, f64::NAN, f64::INFINITY] {
            assert!(
                normalize(
                    vec![point("2026-07-01T00:00:00Z", "2026-07-01T00:15:00Z", value)],
                    "2026-07-01T10:00:00Z"
                )
                .is_err()
            );
        }
        for points in [
            vec![point("2026-07-01T00:00:00Z", "2026-07-01T00:00:00Z", 1.0)],
            vec![
                point("2026-07-01T00:00:00Z", "2026-07-01T01:00:00Z", 1.0),
                point("2026-07-01T00:15:00Z", "2026-07-01T00:30:00Z", 2.0),
            ],
            vec![
                point("2026-07-01T00:00:00Z", "2026-07-01T00:15:00Z", 1.0),
                point("2026-07-01T00:00:00Z", "2026-07-01T00:15:00Z", 2.0),
            ],
        ] {
            assert!(normalize(points, "2026-07-01T10:00:00Z").is_err());
        }
        assert!(
            normalize_forecast(
                ApiForecast {
                    timezone: "UTC".into(),
                    points: vec![]
                },
                chrono_tz::Europe::Helsinki,
                instant("2026-07-01T10:00:00Z")
            )
            .is_err()
        );
    }
}

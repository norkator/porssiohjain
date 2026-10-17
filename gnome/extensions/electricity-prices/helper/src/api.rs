/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

use crate::{ApiChart, ChartDay, DayError, MARKETS, Snapshot, normalize_chart};
use chrono::{Days, Utc};
use chrono_tz::Tz;
use reqwest::{Url, blocking::Client, redirect::Policy};
use std::{io::Read, time::Duration};

#[derive(Clone)]
pub struct Config {
    pub base_url: Url,
    pub timezone: Tz,
    pub market: String,
}

impl Config {
    pub fn new(base_url: &str, timezone: &str, market: &str) -> Result<Self, String> {
        let mut base_url = Url::parse(base_url.trim()).map_err(|_| "Invalid server URL")?;
        if !matches!(base_url.scheme(), "https" | "http")
            || base_url.host_str().is_none()
            || !base_url.username().is_empty()
            || base_url.password().is_some()
            || base_url.query().is_some()
            || base_url.fragment().is_some()
        {
            return Err("Use an HTTP(S) server URL without credentials, query or fragment".into());
        }
        if !base_url.path().ends_with('/') {
            base_url.set_path(&format!("{}/", base_url.path()));
        }
        let timezone = timezone
            .trim()
            .parse::<Tz>()
            .map_err(|_| "Invalid IANA timezone")?;
        let market = market.trim().to_uppercase();
        if !MARKETS.contains(&market.as_str()) {
            return Err("Unsupported Nord Pool market".into());
        }
        Ok(Self {
            base_url,
            timezone,
            market,
        })
    }

    pub fn chart_url(&self, day: &str) -> Result<Url, String> {
        let mut url = self
            .base_url
            .join(&format!("nordpool/{day}-chart"))
            .map_err(|_| "Invalid chart URL")?;
        url.query_pairs_mut()
            .append_pair("timezone", self.timezone.name())
            .append_pair("marketIndexName", &self.market);
        Ok(url)
    }
}

fn fetch_day(client: &Client, config: &Config, day: &str) -> Result<ApiChart, String> {
    let response = client
        .get(config.chart_url(day)?)
        .header("Accept", "application/json")
        .send()
        .map_err(|error| format!("Price request failed: {error}"))?;
    let status = response.status();
    if !status.is_success() {
        return Err(format!("Price server returned HTTP {status}"));
    }
    let mut body = Vec::new();
    response
        .take(1_048_577)
        .read_to_end(&mut body)
        .map_err(|_| "Could not read price response")?;
    if body.len() > 1_048_576 {
        return Err("Price response exceeds 1 MiB".into());
    }
    serde_json::from_slice(&body).map_err(|_| "Price server returned invalid chart JSON".into())
}

pub fn fetch_snapshot(config: &Config) -> Result<Snapshot, String> {
    let client = Client::builder()
        .user_agent(concat!("porssiohjain-gnome/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(Duration::from_secs(5))
        .timeout(Duration::from_secs(15))
        .redirect(Policy::limited(3))
        .build()
        .map_err(|error| format!("Could not create HTTP client: {error}"))?;
    let now = Utc::now();
    let today = now.with_timezone(&config.timezone).date_naive();
    let tomorrow = today
        .checked_add_days(Days::new(1))
        .ok_or("Invalid tomorrow date")?;
    let (today_result, tomorrow_result) = std::thread::scope(|scope| {
        let today_task = scope.spawn(|| fetch_day(&client, config, "today"));
        let tomorrow_result = fetch_day(&client, config, "tomorrow");
        let today_result = today_task
            .join()
            .unwrap_or_else(|_| Err("Price worker failed".into()));
        (today_result, tomorrow_result)
    });

    let mut days: Vec<ChartDay> = Vec::new();
    let mut errors = Vec::new();
    for (day, date, result) in [
        ("today", today, today_result),
        ("tomorrow", tomorrow, tomorrow_result),
    ] {
        match result.and_then(|chart| normalize_chart(chart, config.timezone, date)) {
            Ok(chart) => days.push(chart),
            Err(message) => errors.push(DayError { day, message }),
        }
    }
    if days.is_empty() {
        return Err(errors
            .iter()
            .map(|error| format!("{}: {}", error.day, error.message))
            .collect::<Vec<_>>()
            .join("; "));
    }
    Ok(Snapshot {
        timezone: config.timezone.to_string(),
        market: config.market.clone(),
        fetched_at: Utc::now().timestamp_millis(),
        days,
        errors,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{BufRead, BufReader, Write};
    use std::net::TcpListener;

    fn request_mock(today_response: String, tomorrow_response: String) -> Result<Snapshot, String> {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = std::thread::spawn(move || {
            for connection in listener.incoming().take(2) {
                let mut stream = connection.unwrap();
                let mut reader = BufReader::new(stream.try_clone().unwrap());
                let mut request = String::new();
                reader.read_line(&mut request).unwrap();
                assert!(request.contains("timezone=Europe%2FHelsinki"));
                assert!(request.contains("marketIndexName=FI"));
                loop {
                    let mut line = String::new();
                    reader.read_line(&mut line).unwrap();
                    if line.trim().is_empty() {
                        break;
                    }
                }
                let response = if request.contains("/today-chart?") {
                    &today_response
                } else {
                    &tomorrow_response
                };
                stream.write_all(response.as_bytes()).unwrap();
            }
        });
        let config = Config::new(&format!("http://{address}"), "Europe/Helsinki", "FI").unwrap();
        let result = fetch_snapshot(&config);
        server.join().unwrap();
        result
    }

    fn response(status: u16, body: &str) -> String {
        format!(
            "HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len()
        )
    }

    fn empty_chart(day_offset: u64) -> String {
        let date = Utc::now()
            .with_timezone(&chrono_tz::Europe::Helsinki)
            .date_naive()
            .checked_add_days(Days::new(day_offset))
            .unwrap();
        serde_json::json!({
            "date": date, "timezone": "Europe/Helsinki", "resolutionMinutes": 15, "points": []
        })
        .to_string()
    }

    #[test]
    fn unavailable_tomorrow_does_not_discard_today() {
        let snapshot =
            request_mock(response(200, &empty_chart(0)), response(503, "unavailable")).unwrap();
        assert_eq!(snapshot.days.len(), 1);
        assert_eq!(snapshot.errors.len(), 1);
        assert_eq!(snapshot.errors[0].day, "tomorrow");
    }

    #[test]
    fn unpublished_tomorrow_is_a_valid_empty_day() {
        let snapshot = request_mock(
            response(200, &empty_chart(0)),
            response(200, &empty_chart(1)),
        )
        .unwrap();
        assert_eq!(snapshot.days.len(), 2);
        assert!(snapshot.errors.is_empty());
        assert!(snapshot.days[1].points.is_empty());
        assert!(!snapshot.days[1].complete);
    }

    #[test]
    fn total_failure_and_login_html_are_reported_as_failures() {
        let result = request_mock(
            response(200, "<html>Login</html>"),
            response(502, "unavailable"),
        );
        let error = result.unwrap_err();
        assert!(error.contains("invalid chart JSON"));
        assert!(error.contains("502"));
    }

    #[test]
    fn urls_preserve_self_hosted_path_and_encode_query_parameters() {
        let config = Config::new("http://localhost:8088/energy", "Europe/Helsinki", "fi").unwrap();
        let url = config.chart_url("today").unwrap();
        assert_eq!(url.path(), "/energy/nordpool/today-chart");
        assert_eq!(
            url.query_pairs().collect::<Vec<_>>(),
            vec![
                ("timezone".into(), "Europe/Helsinki".into()),
                ("marketIndexName".into(), "FI".into()),
            ]
        );
    }

    #[test]
    fn invalid_configuration_is_rejected_before_network_access() {
        for url in [
            "file:///etc/passwd",
            "https://user:password@example.com",
            "https://example.com?x=1",
        ] {
            assert!(Config::new(url, "Europe/Helsinki", "FI").is_err());
        }
        assert!(Config::new("https://example.com", "Helsinki", "FI").is_err());
        assert!(Config::new("https://example.com", "Europe/Helsinki", "XX").is_err());
    }
}

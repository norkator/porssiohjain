/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

use crate::{ApiForecast, Snapshot, normalize_forecast};
use chrono::Utc;
use chrono_tz::Tz;
use reqwest::{Url, blocking::Client, redirect::Policy};
use std::{io::Read, time::Duration};

pub struct Config {
    pub base_url: Url,
    pub timezone: Tz,
}

impl Config {
    pub fn new(base_url: &str, timezone: &str) -> Result<Self, String> {
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
        Ok(Self { base_url, timezone })
    }

    pub fn forecast_url(&self) -> Result<Url, String> {
        let mut url = self
            .base_url
            .join("fingrid/wind-forecast")
            .map_err(|_| "Invalid forecast URL")?;
        url.query_pairs_mut()
            .append_pair("timezone", self.timezone.name());
        Ok(url)
    }
}

pub fn fetch_snapshot(config: &Config) -> Result<Snapshot, String> {
    let client = Client::builder()
        .user_agent(concat!(
            "porssiohjain-gnome-wind/",
            env!("CARGO_PKG_VERSION")
        ))
        .connect_timeout(Duration::from_secs(5))
        .timeout(Duration::from_secs(15))
        .redirect(Policy::limited(3))
        .build()
        .map_err(|error| format!("Could not create HTTP client: {error}"))?;
    let response = client
        .get(config.forecast_url()?)
        .header("Accept", "application/json")
        .send()
        .map_err(|error| format!("Forecast request failed: {error}"))?;
    let status = response.status();
    if !status.is_success() {
        return Err(format!("Forecast server returned HTTP {status}"));
    }
    let mut body = Vec::new();
    response
        .take(1_048_577)
        .read_to_end(&mut body)
        .map_err(|_| "Could not read forecast response")?;
    if body.len() > 1_048_576 {
        return Err("Forecast response exceeds 1 MiB".into());
    }
    let forecast: ApiForecast =
        serde_json::from_slice(&body).map_err(|_| "Forecast server returned invalid chart JSON")?;
    normalize_forecast(forecast, config.timezone, Utc::now())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{BufRead, BufReader, Write};
    use std::net::TcpListener;

    fn request_mock(status: u16, body: &str) -> Result<Snapshot, String> {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let response = format!(
            "HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len()
        );
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut reader = BufReader::new(stream.try_clone().unwrap());
            let mut request = String::new();
            reader.read_line(&mut request).unwrap();
            assert!(request.contains("/fingrid/wind-forecast?timezone=Europe%2FHelsinki"));
            loop {
                let mut line = String::new();
                reader.read_line(&mut line).unwrap();
                if line.trim().is_empty() {
                    break;
                }
            }
            let _ = stream.write_all(response.as_bytes());
        });
        let config = Config::new(&format!("http://{address}"), "Europe/Helsinki").unwrap();
        let result = fetch_snapshot(&config);
        server.join().unwrap();
        result
    }

    #[test]
    fn public_endpoint_accepts_empty_forecasts_without_credentials() {
        let snapshot = request_mock(
            200,
            r#"{"timezone":"Europe/Helsinki","points":[],"todayAverage":null}"#,
        )
        .unwrap();
        assert!(snapshot.points.is_empty());
    }

    #[test]
    fn http_html_and_oversized_responses_fail() {
        assert!(
            request_mock(503, "Unavailable")
                .unwrap_err()
                .contains("503")
        );
        assert!(
            request_mock(200, "<html>Login</html>")
                .unwrap_err()
                .contains("JSON")
        );
        assert!(
            request_mock(200, &" ".repeat(1_048_577))
                .unwrap_err()
                .contains("1 MiB")
        );
    }

    #[test]
    fn urls_preserve_self_hosted_paths_and_encode_timezone() {
        let config = Config::new("http://localhost:8088/energy", "Europe/Helsinki").unwrap();
        let url = config.forecast_url().unwrap();
        assert_eq!(url.path(), "/energy/fingrid/wind-forecast");
        assert_eq!(url.query(), Some("timezone=Europe%2FHelsinki"));
    }

    #[test]
    fn invalid_configuration_fails_before_network_access() {
        for url in [
            "file:///etc/passwd",
            "https://user:password@example.com",
            "https://example.com?x=1",
            "https://example.com#x",
        ] {
            assert!(Config::new(url, "Europe/Helsinki").is_err());
        }
        assert!(Config::new("https://example.com", "Helsinki").is_err());
    }
}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

use porssiohjain_prices::api::{Config, fetch_snapshot};
use std::{env, process::ExitCode};

fn run() -> Result<(), String> {
    let mut base_url = "https://app.porssiohjain.fi".to_string();
    let mut timezone = "Europe/Helsinki".to_string();
    let mut market = "FI".to_string();
    let mut args = env::args().skip(1);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--help" | "-h" => {
                println!(
                    "porssiohjain-prices [--base-url URL] [--timezone IANA_ZONE] [--market INDEX]\n\nFetch today and tomorrow prices and write one JSON snapshot to stdout."
                );
                return Ok(());
            }
            "--version" | "-V" => {
                println!("porssiohjain-prices {}", env!("CARGO_PKG_VERSION"));
                return Ok(());
            }
            "--base-url" => base_url = args.next().ok_or("Missing --base-url value")?,
            "--timezone" => timezone = args.next().ok_or("Missing --timezone value")?,
            "--market" => market = args.next().ok_or("Missing --market value")?,
            _ => return Err(format!("Unknown option: {arg}")),
        }
    }
    let config = Config::new(&base_url, &timezone, &market)?;
    let snapshot = fetch_snapshot(&config)?;
    println!(
        "{}",
        serde_json::to_string(&snapshot).map_err(|_| "Could not serialize prices")?
    );
    Ok(())
}

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("{error}");
            ExitCode::FAILURE
        }
    }
}

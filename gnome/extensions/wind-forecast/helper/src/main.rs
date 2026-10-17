/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */

use porssiohjain_wind::api::{Config, fetch_snapshot};
use std::{env, process::ExitCode};

fn run() -> Result<(), String> {
    let mut base_url = "https://app.porssiohjain.fi".to_string();
    let mut timezone = "Europe/Helsinki".to_string();
    let mut args = env::args().skip(1);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--help" | "-h" => {
                println!(
                    "porssiohjain-wind [--base-url URL] [--timezone IANA_ZONE]\n\nFetch the Finnish wind-power forecast and write one JSON snapshot to stdout."
                );
                return Ok(());
            }
            "--version" | "-V" => {
                println!("porssiohjain-wind {}", env!("CARGO_PKG_VERSION"));
                return Ok(());
            }
            "--base-url" => base_url = args.next().ok_or("Missing --base-url value")?,
            "--timezone" => timezone = args.next().ok_or("Missing --timezone value")?,
            _ => return Err(format!("Unknown option: {arg}")),
        }
    }
    let snapshot = fetch_snapshot(&Config::new(&base_url, &timezone)?)?;
    println!(
        "{}",
        serde_json::to_string(&snapshot).map_err(|_| "Could not serialize forecast")?
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

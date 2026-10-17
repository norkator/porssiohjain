# Electricity prices

A GNOME top-bar indicator with the current electricity price and an interactive
graph in its dropdown menu, based on the chart in `hybrid-web`.

- Displays prices in cents/kWh, including the public API's VAT.
- Shows today and tomorrow on the same axis when tomorrow's prices are published.
- Shows the current price and the visible range's minimum, average, and maximum.
- Move the pointer across the chart to inspect each interval, or focus the chart
  and use Left/Right, Home, and End. A marker shows now.
- Retains already fetched prices during a refresh failure and marks them as stale.
- Shows missing intervals as gaps; missing prices are never displayed as zero.
- Uses configured local dates, including 23/25-hour DST days. Repeated clock times
  include their timezone abbreviation in the interval details.

The Rust helper fetches and validates prices, resolves local-day boundaries, and
calculates daily statistics. The small GJS layer renders the native Shell menu,
draws the graph, runs the helper asynchronously, and manages GNOME settings. There
is no background daemon or local web server.

## Install

Target: GNOME Shell 46, as shipped in Ubuntu 24.04. Other Shell versions are not
declared compatible until their menu, chart, and preference APIs are verified.

### Automatic installation

From a terminal in your GNOME desktop, run this from the repository root:

```sh
./gnome/install.sh --install-deps
```

The script installs missing Ubuntu packages using `sudo`, installs Rust for your
user via the official rustup installer when needed, builds the extension, installs
it for your user, and configures activation for the next login. Run the script
without `sudo`. Log out and back in when it finishes; no separate enable command
is needed. Existing extension preferences and other extensions are preserved.

If prerequisites are already installed, use `./gnome/install.sh`. To install the
existing archive without Rust or a build:

```sh
./gnome/install.sh --archive "gnome/dist/$(uname -m)/electricity-prices@porssiohjain.fi.shell-extension.zip"
```

Use `--no-enable` to install without changing activation settings. The script
checks GNOME compatibility and the helper's CPU architecture before installing.
Run `./gnome/install.sh --help` for all options. Re-run the same command to update,
then log out and back in to load the new code.

### Manual installation

If building from source, install a current stable Rust toolchain (Rust 1.85 or
later) using [rustup](https://rustup.rs/), and install the build tools:

```sh
sudo apt update
sudo apt install build-essential python3 make gjs libglib2.0-bin
```

From the repository root, build and install:

```sh
cd gnome
make check
make build
gnome-extensions install --force "dist/$(uname -m)/electricity-prices@porssiohjain.fi.shell-extension.zip"
```

If you already have a built archive for your CPU architecture, only the
`gnome-extensions install --force /path/to/archive.shell-extension.zip` command is
needed; Rust and build tools are not required on the installation machine.

On first installation, log out and back in so GNOME discovers the extension, then:

```sh
gnome-extensions enable electricity-prices@porssiohjain.fi
gnome-extensions prefs electricity-prices@porssiohjain.fi
```

When replacing a loaded extension on Wayland, log out and back in to reload its
JavaScript. Disabling and re-enabling alone does not clear GNOME's module cache.

To disable or remove:

```sh
gnome-extensions disable electricity-prices@porssiohjain.fi
gnome-extensions uninstall electricity-prices@porssiohjain.fi
```

## Settings

Open **Settings…** from the indicator menu. Apply the server and timezone text
fields using their apply buttons (or Enter).

| Setting | Default |
| --- | --- |
| Server URL | `https://app.porssiohjain.fi` |
| Timezone | `Europe/Helsinki` |
| Nord Pool market | `FI` |
| Refresh interval | 5 minutes |
| Top-bar unit | `¢/kWh` shown |

No account, password, or API token is required. The extension reads:

```text
GET /nordpool/today-chart?timezone=Europe%2FHelsinki&marketIndexName=FI
GET /nordpool/tomorrow-chart?timezone=Europe%2FHelsinki&marketIndexName=FI
```

The public API currently applies 25.5% VAT, including when selecting another
market. This extension preserves the returned price without converting VAT.
Transfer charges and retailer fees are excluded. A control's scheduling price can
differ because it uses the control's tax and applicable transfer price.

The menu refreshes the displayed current interval every 15 seconds and whenever
opened. Network refreshes use the configured interval; **Refresh prices** triggers
one immediately. Each of the two HTTP requests has a 15-second timeout and a 1 MiB
response limit. Requests are canceled and the helper is stopped on disable or a
price-source settings change. Cached data stays in memory only.

## Development

Run the helper without GNOME, from `gnome/`:

```sh
cargo run --locked -p porssiohjain-prices -- \
  --base-url https://app.porssiohjain.fi \
  --timezone Europe/Helsinki --market FI
```

It emits one JSON snapshot on stdout. Diagnostics go to stderr and a complete
request failure returns a nonzero exit status. Empty tomorrow data is a valid
unpublished day. Failure to fetch tomorrow does not discard valid today prices.

Rust tests cover Helsinki midnight, spring/autumn DST, repeated hours, negative
prices, gaps, empty responses, conflicting points, and invalid configuration. GJS
tests cover exact interval boundaries, midnight rollover, expiry, gaps, and merging
days for the menu.

For manual GNOME verification, check the indicator, open the graph, inspect a
point, open preferences, change the market/timezone, simulate an unreachable
server, and disable the extension during a refresh. Confirm no helper remains
running after disabling. Also check a narrow display and light/dark Shell themes.
The [GNOME debugging guide](https://gjs.guide/extensions/development/debugging.html)
describes running a separate nested Shell for testing without restarting the
current desktop.

Logs:

```sh
journalctl --user -f -o cat /usr/bin/gnome-shell
```

This extension follows the repository's [license](../../../LICENSE).

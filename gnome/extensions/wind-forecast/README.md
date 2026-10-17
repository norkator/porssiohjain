# Wind forecast

A GNOME top-bar indicator for **Finnish wind-power production forecasts in MW**,
using the same Fingrid data as the `hybrid-web` main-menu chart.

- Shows the current forecast estimate in the top bar.
- Opens a line/area graph covering all available forecast days, with local-date
  bands and a marker for now. Point to the graph to inspect forecast samples, or
  focus it and use Left/Right, Home, and End.
- Shows today’s and tomorrow’s average and tomorrow’s percentage change. A
  negative change means lower forecast production. Partial-day averages use only
  available samples and are identified in the menu.
- Keeps the last fetched data visible if refresh fails, with a warning. Missing
  intervals remain gaps; an unavailable forecast displays `—`, never a fake zero.
- Works alongside the electricity-price extension with independent settings.

These are national production estimates, not local wind speeds or measured
production. The Rust helper fetches, validates, and groups forecasts by local
calendar day. GJS renders the native GNOME menu and graph asynchronously; no
background daemon or web server is needed.

## Install

Target: **GNOME Shell 46 / Ubuntu 24.04**. Run installers as your normal desktop
user, without `sudo`. Log out and back in after installing or updating.

### Automatic installation from this repository

From the repository root:

```sh
./gnome/install.sh --extension wind-forecast --install-deps
```

This installs missing Ubuntu dependencies and Rust when needed, builds the
extension, installs it for your user, and configures activation at the next login.
Existing preferences and other extensions are preserved. Omit `--install-deps`
when prerequisites are already installed. Add `--no-enable` to leave activation
settings unchanged.

To install an existing build without compiling:

```sh
./gnome/install.sh --extension wind-forecast \
  --archive "gnome/dist/$(uname -m)/wind-forecast@porssiohjain.fi.shell-extension.zip"
```

### Ready release packages

After a successful workflow run on the `extensions` branch, download the matching
bundle from the [GNOME extensions prerelease](https://github.com/norkator/porssiohjain/releases/tag/gnome-extensions-latest).
Choose `x86_64` for Intel/AMD or `aarch64` for ARM64; check with `uname -m`.

```sh
tar -xzf "porssiohjain-wind-forecast-linux-$(uname -m).tar.gz"
cd "porssiohjain-wind-forecast-linux-$(uname -m)"
./install.sh
```

The standalone bundle contains the compiled helper and installer; Rust and a
repository checkout are not required. Use `./install.sh --install-deps` if runtime
tools are missing. Checksums accompany the release packages.

### Build and install manually

Install Rust 1.85 or later with Cargo, plus `build-essential`, `python3`, `make`,
`gjs`, and `libglib2.0-bin`. From the repository root:

```sh
cd gnome
make check
make build EXTENSION=wind-forecast
gnome-extensions install --force "dist/$(uname -m)/wind-forecast@porssiohjain.fi.shell-extension.zip"
```

After the next login, enable it:

```sh
gnome-extensions enable wind-forecast@porssiohjain.fi
gnome-extensions prefs wind-forecast@porssiohjain.fi
```

To disable or uninstall:

```sh
gnome-extensions disable wind-forecast@porssiohjain.fi
gnome-extensions uninstall wind-forecast@porssiohjain.fi
```

## Settings and data

Open **Settings…** in the indicator menu. Apply text fields with Enter or their
apply buttons.

| Setting | Default |
| --- | --- |
| Server URL | `https://app.porssiohjain.fi` |
| Timezone | `Europe/Helsinki` |
| Refresh interval | 15 minutes |
| Top-bar unit | `MW` shown |

No account or API token is needed. The helper reads the public endpoint:

```text
GET /fingrid/wind-forecast?timezone=Europe%2FHelsinki
```

The geography is always Finland; the timezone changes date boundaries and labels.
Local dates include 23/25-hour DST days, and sample details distinguish repeated
hours with timezone abbreviations. Averages match the web API’s mean of available
samples. Percent change is unavailable when today’s average is zero or either
day lacks samples.

The current estimate follows the web chart’s linear interpolation between
contiguous samples. The final sample applies until its declared end; values are
never extrapolated across missing intervals or beyond the forecast horizon.
The menu updates every 15 seconds and when opened. **Refresh forecast** fetches
immediately. HTTP requests have a 15-second timeout and a 1 MiB response limit.
Disabling the extension or changing its source settings stops the helper and
cancels the request. Cached data stays in memory only. The fetched timestamp is
the time of the request, not Fingrid’s forecast issue time.

## Development

From `gnome/`:

```sh
make check
make release EXTENSION=wind-forecast
cargo run --locked -p porssiohjain-wind -- \
  --base-url https://app.porssiohjain.fi --timezone Europe/Helsinki
```

The helper emits one JSON snapshot on stdout and diagnostics on stderr. A request
failure returns a nonzero status; an empty forecast is valid unavailable data.
Packages go to ignored `dist/` folders. The existing release workflow discovers
this extension automatically and only runs on pushes to `extensions`.

Checks cover HTTP errors, invalid/overlapping data, gaps, zero production, local
midnight, DST, expiry, percentage change, and standalone bundle installation.
For native GNOME verification, enable both Pörssiohjain extensions, open the wind
graph and preferences, inspect samples with pointer/keyboard, test an unreachable
server, and disable during a refresh. Check the Shell log with:

```sh
journalctl --user -f -o cat /usr/bin/gnome-shell
```

This extension follows the repository’s [license](../../../LICENSE).

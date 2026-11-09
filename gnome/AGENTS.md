# GNOME extensions handoff

## Current state and user decision

Checkpoint: 2026-11-09. The user upgraded to Ubuntu 26.04.1 / GNOME Shell 50.1
and resumed compatibility work, explicitly retaining Ubuntu 24.04 support.
Both extensions' version 2 metadata declares `"46"` and `"50"`.

Preserve GNOME Shell 46 / Ubuntu 24.04 and Shell 50 / Ubuntu 26.04 support.
Porting guides 47 through 50 were reviewed. Menus use the newer BoxLayout
`orientation` property when available, falling back to `vertical` on Shell 46.
The installed Shell 50.1 still exposes both properties, but `vertical` is
deprecated. The Rust helpers are independent of the Shell UI API. Adding another
Shell version requires native menu, chart, preferences and lifecycle verification
before changing metadata, installer expectations, release coverage or support claims.

References: [Ubuntu 26.04 release notes](https://documentation.ubuntu.com/release-notes/26.04/changes-since-previous-interim/),
[GNOME porting guides](https://gjs.guide/extensions/upgrading/gnome-shell-50.html).

## Layout and implementation

| Extension directory | UUID | Rust package / executable | GSettings schema |
| --- | --- | --- | --- |
| `extensions/electricity-prices` | `electricity-prices@porssiohjain.fi` | `porssiohjain-prices` | `org.gnome.shell.extensions.porssiohjain-prices` |
| `extensions/wind-forecast` | `wind-forecast@porssiohjain.fi` | `porssiohjain-wind` | `org.gnome.shell.extensions.porssiohjain-wind` |

Each extension has `extension.json` for packaging, a `helper/` Rust crate,
`shell/` for GJS, styles, icons, and schemas, `tests/` for GJS model checks, and
its own installation README. This folder is a shared Cargo workspace with one
committed lockfile; additional extensions belong under `extensions/`.

Rust performs public HTTP requests, validation, local-date grouping, and
statistics. Helpers emit one JSON snapshot on stdout and diagnostics on stderr;
failures return a nonzero status. GJS launches helpers with asynchronous
`Gio.Subprocess` calls and draws the native Shell menus. There is no background
daemon or local web server. GTK/libadwaita imports belong in `prefs.js`, which
runs separately from Shell.

Defaults are `https://app.porssiohjain.fi` and `Europe/Helsinki`. No account or
API token is needed. Settings are independent, so both indicators can run at
the same time. Preserve unique schemas, CSS classes, and registered GObject
names when adding extensions.

## Data and chart behavior

- Electricity uses `/nordpool/today-chart` and `/nordpool/tomorrow-chart` with
  `timezone` and `marketIndexName`. It shows spot prices in cents/kWh with the
  public API's VAT, excluding transfer and retailer fees. The chart is a step
  graph of actual intervals, and the horizon summary includes the visible days.
  Failure or unpublished data for tomorrow must not discard valid today data.
- Wind uses `/fingrid/wind-forecast?timezone=...`, matching
  `hybrid-web/src/components/WindForecastCard.tsx` and `hybrid-web/src/lib/wind.ts`.
  It is a **Finnish national production forecast in MW**, not local wind speed
  or measured production. Show the full available forecast horizon and
  today/tomorrow sample averages. Tomorrow change is signed: negative means
  less forecast production. Partial averages must be identified. Interpolate
  the current estimate only between contiguous forecast samples; the last
  sample applies until its declared end.
- Resolve local calendar dates with the configured IANA timezone, including
  23/25-hour DST days. Keep authoritative timestamps and half-open intervals.
  Cached tomorrow summaries must become today correctly after local midnight.
  Repeated clock times are distinguished by timezone abbreviations in details.
- Never display missing data as zero or bridge missing intervals with a curve.
  Retain previous snapshots with a warning after refresh failures; expire
  current values at the end of the available data. Cache is in memory only.
- Both charts support pointer inspection and Left/Right/Home/End keyboard
  navigation. Stop timers, cancel requests, and terminate helpers on disable;
  changing source settings also cancels and clears the old source's snapshot.

Native drawing details already verified on GNOME 46: call
`St.DrawingArea.get_surface_size()` only during repaint, use actor dimensions
for pointer handling, draw text with Pango/PangoCairo, and dispose Cairo
contexts. The graph content uses `PopupMenuSection` to avoid insensitive-menu
text dimming.

## Installer, packaging, and releases

From the repository root:

```sh
./gnome/install.sh --install-deps
./gnome/install.sh --extension wind-forecast --install-deps
```

Run as the normal desktop user. The installer optionally installs prerequisites,
builds and installs the chosen extension, and queues activation for the next
login. Keep `--archive` and `--no-enable` working. Preserve existing preferences
and other enabled extensions, check Shell version and helper architecture, and
require logout/login to load new or replaced modules on Wayland.

The rustup bootstrap previously failed with `unexpected argument '-y'` because
an extra `--` was passed after the installer script filename. Preserve the
correct form: `sh "$installer_script" -y --profile minimal ...`. Installer
regression tests cover this.

From `gnome/`:

```sh
make list
make check
make build EXTENSION=electricity-prices
make release EXTENSION=wind-forecast
make smoke
```

`tools/package.py` discovers registered extension folders and builds native
ZIPs under `dist/<architecture>/`. `tools/release.py` produces installable ZIPs,
standalone tarballs, and SHA256 checksum files in `dist/release/`. Standalone
bundles include an installer and the prebuilt helper; they need neither Cargo
nor a source checkout. Keep generated files under the existing `.gitignore`
rules for `dist/`, `target/`, compiled schemas, and Python caches.

The root `.github/workflows/gnome-extensions.yml` runs **only on pushes to the
`extensions` branch**. It discovers every registered extension, checks and
builds on native Ubuntu 24.04 x86_64 and ARM64 runners, then runs installer and
headless Shell checks on x86_64 Ubuntu 24.04 and 26.04 containers. It updates the
rolling `gnome-extensions-latest` prerelease only after both architecture builds
and both compatibility jobs succeed. Preserve the
branch restriction, serialized publication, source-head check, and checksum
verification. A new registered extension is included automatically. Building
locally does not publish a release.

## Verification and documentation

`make check` runs rustfmt, Clippy, Rust tests, Python installer/release tests,
strict GSettings schema validation, and GJS syntax/model checks for all
registered extensions. Use Rust 1.85+ with Cargo, rustfmt, and Clippy, plus a C
linker, Make, Python 3, GJS, and GLib tools. Tests use temporary settings/data
directories and mock HTTP servers; live backend access is not required.

Previous implementation verification passed: `make check`, native x86_64 release
packaging and checksum verification, standalone installation with the real
compiled wind helper, and an isolated GNOME 46 session with both extensions
active. Native checks covered independent preferences, keyboard/pointer graph
inspection, opening preferences, retaining data with a warning after a failed
refresh, and cleanup on disable during a request. ARM64 is built by CI; local
native verification was x86_64. This records local checks, not confirmation of
a remotely published release.

`make smoke` checks both built archives using real Rust helpers against a local
HTTP fixture, menus, Cairo chart repaints, pointer handlers with native actor
picking/transforms, virtual keyboard input, opening preferences, cached-data
warnings after HTTP failure, helper cancellation and disable/re-enable cleanup.
These checks pass on isolated Shell 46.0 and 50.1 sessions. Shell 46 also passed
virtual pointer movement; headless pointer warping on Shell 50 did not deliver
coordinates reliably, so the repeatable test supplies coordinates to the recorded
motion handler. Physical pointer use on the user's desktop remains a manual check.
Version 2 was installed locally for both indicators with settings preserved and
activation queued for the next login.

For future native checks, use a separate nested/headless Shell with isolated
XDG data/config/cache/state directories and a private D-Bus session. Routine
verification must not change the user's running desktop. Do not rely on old
`/tmp` toolchains or smoke scripts surviving a fresh session.

Read [README.md](README.md) and the per-extension READMEs for current commands
and behavior. Screenshots are user-provided files at
`../doc/gnome_electricity_prices.png` and `../doc/gnome_wind_forecast.png`, linked
from the corresponding extension READMEs. The repository root README also
introduces both extensions and links to installation/release documentation.

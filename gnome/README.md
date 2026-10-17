# GNOME extensions

This workspace holds Pörssiohjain GNOME Shell extensions. Each extension has its
own folder under `extensions/`, while Rust helper crates share the Cargo workspace
and lockfile. Building does not install or enable anything in your desktop session.

| Extension | Folder | GNOME Shell |
| --- | --- | --- |
| Electricity prices | [extensions/electricity-prices](extensions/electricity-prices) | 46 (Ubuntu 24.04) |

## Build and verify

Install Rust with Cargo, a C linker (`build-essential` on Ubuntu), Python 3, Make,
GJS, and GLib tools (`libglib2.0-bin`). The native helper uses Rustls, so OpenSSL
development packages are not needed. Ubuntu's GNOME desktop already supplies the
Shell, GJS, GTK 4, and libadwaita runtime.

From this folder:

```sh
make list
make check
make build EXTENSION=electricity-prices
```

The output is `dist/<architecture>/electricity-prices@porssiohjain.fi.shell-extension.zip`.
It contains the compiled Rust helper, the GNOME UI, the settings schema, and the
repository license. Build on the target architecture and an Ubuntu version no
newer than the installation target; the bundled helper is a native Linux executable.

`make check` runs Rust formatting, Clippy, Rust tests, GJS syntax/model checks, and
strict schema validation. It needs no live backend and does not touch GNOME settings.

See the [electricity prices README](extensions/electricity-prices/README.md) for
installation, settings, behavior, and manual verification.

## Adding another extension

1. Create `extensions/<name>/shell/` with its own `metadata.json`, unique UUID,
   `extension.js`, and optional `prefs.js`, stylesheet, and schemas.
2. Add `extensions/<name>/extension.json`. Use `{}` for an extension without a
   helper, or set `cargo-package` and `binary` for a Rust helper.
3. Put the helper crate in `extensions/<name>/helper/` and add it to the members in
   the root `Cargo.toml`. Keep the helper's stdout as JSON and diagnostics on stderr.
4. Put GJS tests in `extensions/<name>/tests/*.test.js`; `make check` discovers them.
5. Run `make build EXTENSION=<name>` to package that extension independently.

GNOME Shell's menu and drawing APIs use GJS. Rust helpers run as asynchronous
subprocesses; extension UI modules must not block the Shell or import GTK. GTK and
libadwaita belong in `prefs.js`, which runs in a separate process. See the
[GNOME extension guide](https://gjs.guide/extensions/development/creating.html) and
[GIO subprocess guide](https://gjs.guide/guides/gio/subprocesses.html).

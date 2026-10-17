# GNOME extensions

This workspace holds Pörssiohjain GNOME Shell extensions. Each extension has its
own folder under `extensions/`, while Rust helper crates share the Cargo workspace
and lockfile. Building does not install or enable anything in your desktop session.

| Extension | Folder | GNOME Shell |
| --- | --- | --- |
| Electricity prices | [extensions/electricity-prices](extensions/electricity-prices) | 46 (Ubuntu 24.04) |
| Wind forecast | [extensions/wind-forecast](extensions/wind-forecast) | 46 (Ubuntu 24.04) |

## Compatibility and development handoff

GNOME Shell 46 / Ubuntu 24.04 remains the tested and supported target. Ubuntu
26.04 / GNOME 50 support is deferred until the user installs Ubuntu 26.04 and
resumes compatibility testing. Both extensions currently declare only Shell 46;
an upgrade requires a compatibility update before they can be enabled there.

For a fresh development session, read [AGENTS.md](AGENTS.md) for the implementation
map, data contracts, installer details, release constraints, previous verification,
and the deferred compatibility work.

## Download a ready package

After the first successful workflow run, downloads are available from the
[GNOME extensions prerelease](https://github.com/norkator/porssiohjain/releases/tag/gnome-extensions-latest).
Choose the `x86_64` or `aarch64` package matching `uname -m`.

Each extension has a `.shell-extension.zip` for direct GNOME installation and a
`.tar.gz` bundle containing the prebuilt extension and an installer. Extract the
bundle and run `./install.sh` inside it, then log out and back in. Use
`./install.sh --install-deps` if Ubuntu runtime tools are missing. These bundles
install without a Rust toolchain or repository checkout.

## Install

From the repository root, run:

```sh
./gnome/install.sh --install-deps
```

This installs missing Ubuntu dependencies and Rust when needed, builds the
electricity-price extension, installs it for your user, and configures activation
at the next login. Run without `sudo`, then log out and back in. Use `--extension
<name>` for another extension, `--archive <path>` for an existing build, or
`--no-enable` to leave activation settings unchanged. Omit `--install-deps` when
prerequisites are already available. See `./gnome/install.sh --help` for details.

To install the Finnish wind-power forecast indicator instead:

```sh
./gnome/install.sh --extension wind-forecast --install-deps
```

Both indicators can be installed and enabled together. Their settings are independent.

## Build and verify

Install Rust with Cargo, a C linker (`build-essential` on Ubuntu), Python 3, Make,
GJS, and GLib tools (`libglib2.0-bin`). The native helper uses Rustls, so OpenSSL
development packages are not needed. Ubuntu's GNOME desktop already supplies the
Shell, GJS, GTK 4, and libadwaita runtime.

For `make check`, also install the Rust developer components with
`rustup component add rustfmt clippy` if using a minimal Rust toolchain.

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
strict schema validation. Installer tests use temporary GNOME data and settings;
no live backend is needed.

See the [electricity prices README](extensions/electricity-prices/README.md) and
[wind forecast README](extensions/wind-forecast/README.md) for installation,
settings, behavior, and manual verification.

## Release workflow

[gnome-extensions.yml](../.github/workflows/gnome-extensions.yml) runs only on pushes
to the `extensions` branch. Merge or commit the desired changes there and push
that branch. It checks and builds every registered extension on native Ubuntu
24.04 x86_64 and ARM64 runners. After both builds succeed, it updates the dedicated
`gnome-extensions-latest` prerelease with ZIPs, standalone installation bundles,
SHA256 checksums, and the source commit. Packages are also kept as workflow
artifacts for 30 days. The last published packages remain available if a build fails.

The workflow uses the repository's built-in `GITHUB_TOKEN` with release write
permission in the publishing job. No additional secrets are needed. Publication
updates the dedicated rolling tag and replaces assets with the same filenames.

To prepare the same packages locally, from `gnome/`:

```sh
make release EXTENSION=electricity-prices
```

Downloads are generated in `dist/release/`, which is covered by `.gitignore`.

## Adding another extension

1. Create `extensions/<name>/shell/` with its own `metadata.json`, unique UUID,
   `extension.js`, and optional `prefs.js`, stylesheet, and schemas.
2. Add `extensions/<name>/extension.json`. Use `{}` for an extension without a
   helper, or set `cargo-package` and `binary` for a Rust helper.
3. Put the helper crate in `extensions/<name>/helper/` and add it to the members in
   the root `Cargo.toml`. Keep the helper's stdout as JSON and diagnostics on stderr.
4. Put GJS tests in `extensions/<name>/tests/*.test.js`; `make check` discovers them.
5. Run `make build EXTENSION=<name>` to package that extension independently.
   Registered extensions are also included automatically in the release workflow.

GNOME Shell's menu and drawing APIs use GJS. Rust helpers run as asynchronous
subprocesses; extension UI modules must not block the Shell or import GTK. GTK and
libadwaita belong in `prefs.js`, which runs in a separate process. See the
[GNOME extension guide](https://gjs.guide/extensions/development/creating.html) and
[GIO subprocess guide](https://gjs.guide/guides/gio/subprocesses.html).

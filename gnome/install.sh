#!/bin/sh
# Pörssiohjain Personal Use License v1.0. See the repository LICENSE.
set -eu

workspace_dir=$(CDPATH= cd -P "$(dirname "$0")" && pwd)
extension=electricity-prices
archive=
install_deps=false
enable_extension=true
installer_tmp=

usage() {
    cat <<'EOF'
Usage: ./gnome/install.sh [options]

Build and install a GNOME extension for the current desktop user.

  --extension NAME   Extension project (default: electricity-prices)
  --archive FILE     Install an existing ZIP instead of building from source
  --install-deps     Install missing Ubuntu build tools and Rust when needed
  --no-enable        Install files without changing extension activation settings
  -h, --help         Show this help

Run without sudo, from a terminal in your GNOME desktop session.
--install-deps uses sudo only for missing Ubuntu packages. Rust is installed
for your user with the official rustup installer when needed.
EOF
}

die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

cleanup() {
    if [ -n "$installer_tmp" ]; then
        rm -rf -- "$installer_tmp"
    fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

while [ "$#" -gt 0 ]; do
    case "$1" in
        --extension|--archive)
            option=$1
            [ "$#" -ge 2 ] || die "Missing value for $option"
            case "$2" in ''|--*) die "Missing value for $option" ;; esac
            if [ "$option" = --extension ]; then extension=$2; else archive=$2; fi
            shift 2
            ;;
        --install-deps) install_deps=true; shift ;;
        --no-enable) enable_extension=false; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown option: $1 (use --help)" ;;
    esac
done

[ "$(id -u)" -ne 0 ] || die 'Run as your desktop user without sudo; use --install-deps for package setup.'
[ "$(uname -s)" = Linux ] || die 'This installer requires Linux and GNOME Shell.'
command -v gnome-shell >/dev/null 2>&1 || die 'GNOME Shell is not installed.'
command -v gnome-extensions >/dev/null 2>&1 || die 'The gnome-extensions command is not installed.'
case "$extension" in ''|*[!a-zA-Z0-9_-]*) die 'Invalid extension project name.' ;; esac
project_dir=$workspace_dir/extensions/$extension
[ -f "$project_dir/extension.json" ] || die "Unknown extension project: $extension"
if [ -n "$archive" ]; then
    [ -f "$archive" ] || die "Archive not found: $archive"
fi

apt_updated=false
ensure_packages() {
    if [ -z "$missing_packages" ]; then
        return
    fi
    [ "$install_deps" = true ] || die "Missing Ubuntu packages:$missing_packages. Rerun with --install-deps."
    command -v apt-get >/dev/null 2>&1 || die 'Install the missing dependencies with your distribution package manager.'
    command -v sudo >/dev/null 2>&1 || die 'sudo is required for --install-deps.'
    if [ "$apt_updated" = false ]; then
        sudo apt-get update
        apt_updated=true
    fi
    # Package names below are fixed strings, intentionally split into arguments.
    sudo apt-get install -y $missing_packages
}

missing_packages=
for dependency in python3 gjs glib-compile-schemas; do
    if ! command -v "$dependency" >/dev/null 2>&1; then
        case "$dependency" in
            python3) missing_packages="$missing_packages python3" ;;
            gjs) missing_packages="$missing_packages gjs" ;;
            glib-compile-schemas) missing_packages="$missing_packages libglib2.0-bin" ;;
        esac
    fi
done
ensure_packages

shell_version=$(gnome-shell --version)
uuid=$(python3 - "$project_dir/shell/metadata.json" "$shell_version" <<'PY'
import json
import re
import sys

metadata = json.load(open(sys.argv[1]))
version = re.search(r"GNOME Shell (\d+)", sys.argv[2])
if not version or version[1] not in metadata["shell-version"]:
    sys.exit(f"Unsupported {sys.argv[2]}. This extension supports GNOME " + ", ".join(metadata["shell-version"]) + ".")
uuid = metadata["uuid"]
if not re.fullmatch(r"[a-zA-Z0-9._@-]+", uuid):
    sys.exit("Invalid extension UUID")
print(uuid)
PY
)

needs_rust=$(python3 -c 'import json, sys; print("yes" if json.load(open(sys.argv[1])).get("cargo-package") else "no")' "$project_dir/extension.json")
if [ -z "$archive" ] && [ "$needs_rust" = yes ]; then
    missing_packages=
    if ! command -v cc >/dev/null 2>&1; then
        missing_packages="$missing_packages build-essential"
    fi
    if [ "$install_deps" = true ] && ! command -v curl >/dev/null 2>&1; then
        missing_packages="$missing_packages curl ca-certificates"
    fi
    ensure_packages
    cargo_bin=${CARGO_HOME:-"$HOME/.cargo"}/bin
    if ! command -v cargo >/dev/null 2>&1 || ! command -v rustc >/dev/null 2>&1; then
        PATH=$cargo_bin:$PATH
        export PATH
    fi
    rust_ready() {
        command -v cargo >/dev/null 2>&1 && command -v rustc >/dev/null 2>&1 &&
            rustc --version | python3 -c 'import re, sys; v = re.search(r"rustc (\d+)\.(\d+)", sys.stdin.read()); sys.exit(0 if v and tuple(map(int, v.groups())) >= (1, 85) else 1)'
    }
    if ! rust_ready; then
        [ "$install_deps" = true ] || die 'Rust 1.85+ and Cargo are required. Rerun with --install-deps, or use --archive FILE.'
        if command -v rustup >/dev/null 2>&1; then
            rustup toolchain install stable --profile minimal
        elif [ -x "$cargo_bin/rustup" ]; then
            "$cargo_bin/rustup" toolchain install stable --profile minimal
            PATH=$cargo_bin:$PATH
            export PATH
        else
            installer_tmp=$(mktemp -d "${TMPDIR:-/tmp}/porssiohjain-gnome-install.XXXXXX")
            curl --proto '=https' --tlsv1.2 --fail --silent --show-error --location \
                https://sh.rustup.rs -o "$installer_tmp/rustup-init.sh"
            sh "$installer_tmp/rustup-init.sh" -y --profile minimal --no-modify-path --default-toolchain stable
            PATH=$cargo_bin:$PATH
            export PATH
        fi
        RUSTUP_TOOLCHAIN=stable
        export RUSTUP_TOOLCHAIN
        rust_ready || die 'Rust setup did not provide Rust 1.85+ and Cargo.'
    fi
fi
if [ -z "$archive" ]; then
    printf 'Building %s…\n' "$extension"
    python3 "$workspace_dir/tools/package.py" "$extension"
    archive=$workspace_dir/dist/$(uname -m)/$uuid.shell-extension.zip
fi

# Check the bundle before the GNOME installer changes any installed files.
python3 - "$archive" "$uuid" "$project_dir/extension.json" "$shell_version" "$(uname -m)" <<'PY'
import json
import re
import sys
import zipfile

path, uuid, config_path, shell_version, architecture = sys.argv[1:]
try:
    with zipfile.ZipFile(path) as bundle:
        metadata = json.loads(bundle.read("metadata.json"))
        if metadata["uuid"] != uuid:
            sys.exit("The archive belongs to a different extension.")
        if re.search(r"GNOME Shell (\d+)", shell_version)[1] not in metadata["shell-version"]:
            sys.exit("The archive does not support this GNOME Shell version.")
        bundle.getinfo("extension.js")
        binary = json.load(open(config_path)).get("binary")
        if binary:
            with bundle.open("libexec/" + binary) as helper:
                header = helper.read(64)
            machines = {"x86_64": 62, "aarch64": 183, "armv7l": 40, "i686": 3, "ppc64le": 21, "riscv64": 243}
            if len(header) < 20 or header[:4] != b"\x7fELF" or header[5] not in (1, 2):
                sys.exit("The archive does not contain a Linux helper executable.")
            machine = int.from_bytes(header[18:20], "little" if header[5] == 1 else "big")
            if machines.get(architecture) != machine:
                sys.exit(f"The archive's native helper does not match {architecture}. Build on this machine instead.")
        if bundle.testzip():
            sys.exit("The extension archive is corrupt.")
except (OSError, KeyError, ValueError, zipfile.BadZipFile) as error:
    sys.exit(f"Invalid extension archive: {error}")
PY

printf 'Installing %s for the current user…\n' "$uuid"
gnome-extensions install --force "$archive"

if [ "$enable_extension" = true ]; then
    # New extensions are not discoverable via `gnome-extensions enable` until
    # Shell restarts. Persist activation directly, preserving all other UUIDs.
    gjs -c '
        const Gio = imports.gi.Gio;
        const settings = new Gio.Settings({schema_id: "org.gnome.shell"});
        const uuid = ARGV[0];
        const enabled = settings.get_strv("enabled-extensions");
        const disabled = settings.get_strv("disabled-extensions").filter(value => value !== uuid);
        if (!enabled.includes(uuid))
            enabled.push(uuid);
        settings.delay();
        if (!settings.set_strv("disabled-extensions", disabled) || !settings.set_strv("enabled-extensions", enabled))
            throw new Error("GNOME extension activation settings are not writable");
        settings.apply();
        Gio.Settings.sync();
        print("Configured to enable at the next login.");
        if (settings.get_boolean("disable-user-extensions"))
            print("User extensions are disabled globally. Turn them on in the GNOME Extensions app.");
    ' "$uuid"
else
    printf 'Activation settings were left unchanged (--no-enable).\n'
fi

printf '\nInstalled %s. Log out and back in to load the new code.\n' "$uuid"
printf 'Settings: gnome-extensions prefs %s\n' "$uuid"

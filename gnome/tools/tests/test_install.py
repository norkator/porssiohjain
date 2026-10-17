"""Exercise the installer against a real GNOME CLI using isolated user data."""

import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest
import zipfile

WORKSPACE = Path(__file__).resolve().parents[2]
SHELL = WORKSPACE / "extensions/electricity-prices/shell"
UUID = "electricity-prices@porssiohjain.fi"
BINARY = "porssiohjain-prices"


@unittest.skipUnless(shutil.which("gnome-extensions") and shutil.which("gnome-shell"), "GNOME tools required")
class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="gnome installer test ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.environment = dict(os.environ)
        self.environment.update({
            "XDG_DATA_HOME": str(self.root / "data"),
            "XDG_CONFIG_HOME": str(self.root / "config"),
            "XDG_CACHE_HOME": str(self.root / "cache"),
            "XDG_STATE_HOME": str(self.root / "state"),
            "GSETTINGS_BACKEND": "keyfile",
        })
        self.installed = self.root / "data/gnome-shell/extensions" / UUID
        self.metadata = json.loads((SHELL / "metadata.json").read_text())
        self.machine = {
            "x86_64": 62, "aarch64": 183, "armv7l": 40, "i686": 3,
            "ppc64le": 21, "riscv64": 243,
        }[platform.machine()]
        self.stage = self.root / "bundle source"
        shutil.copytree(SHELL, self.stage)
        subprocess.run(["glib-compile-schemas", "--strict", str(self.stage / "schemas")], check=True)
        (self.stage / "libexec").mkdir()
        self.archive = self.root / "extension with spaces.shell-extension.zip"
        self.bundle()

    def bundle(self, metadata=None, machine=None):
        (self.stage / "metadata.json").write_text(json.dumps(metadata or self.metadata))
        # Only the ELF header is needed to test CPU validation. This fixture is
        # never executed; no Shell session is started by these tests.
        header = bytearray(64)
        header[:6] = b"\x7fELF\x02\x01"
        header[18:20] = (self.machine if machine is None else machine).to_bytes(2, "little")
        helper = self.stage / "libexec" / BINARY
        helper.write_bytes(header)
        helper.chmod(0o755)
        with zipfile.ZipFile(self.archive, "w") as archive:
            for source in self.stage.rglob("*"):
                if source.is_file():
                    archive.write(source, source.relative_to(self.stage))

    def run_installer(self, *options):
        return subprocess.run([
            "sh", str(WORKSPACE / "install.sh"), "--archive", str(self.archive), *options,
        ], cwd=self.root, env=self.environment, text=True, capture_output=True, timeout=20)

    def settings(self, program):
        result = subprocess.run([
            "gjs", "-c", 'const Gio = imports.gi.Gio; const s = new Gio.Settings({schema_id: "org.gnome.shell"});' + program,
        ], env=self.environment, text=True, capture_output=True, check=True)
        return result.stdout.strip()

    def read_settings(self):
        return json.loads(self.settings('print(JSON.stringify({enabled: s.get_strv("enabled-extensions"), disabled: s.get_strv("disabled-extensions"), globallyDisabled: s.get_boolean("disable-user-extensions")}));'))

    def test_archive_installs_with_executable_helper_and_no_activation_changes(self):
        result = self.run_installer("--no-enable")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue((self.installed / "extension.js").is_file())
        self.assertTrue((self.installed / "schemas/gschemas.compiled").is_file())
        self.assertTrue(os.access(self.installed / "libexec" / BINARY, os.X_OK))
        self.assertEqual(self.read_settings()["enabled"], [])
        self.assertIn("Log out and back in", result.stdout)

    def test_activation_preserves_other_settings_and_is_idempotent(self):
        self.settings('s.set_strv("enabled-extensions", ["other@example.com"]);'
                      f's.set_strv("disabled-extensions", ["{UUID}", "disabled@example.com"]);'
                      's.set_boolean("disable-user-extensions", true); Gio.Settings.sync();')
        for _ in range(2):
            result = self.run_installer()
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("disabled globally", result.stdout)
            self.assertEqual(self.read_settings(), {
                "enabled": ["other@example.com", UUID],
                "disabled": ["disabled@example.com"],
                "globallyDisabled": True,
            })

    def test_wrong_uuid_version_or_cpu_is_rejected_before_installing(self):
        for metadata, machine, message in [
            ({**self.metadata, "uuid": "other@example.com"}, self.machine, "different extension"),
            ({**self.metadata, "shell-version": ["99"]}, self.machine, "does not support"),
            (self.metadata, 0, "does not match"),
        ]:
            with self.subTest(message=message):
                self.bundle(metadata, machine)
                result = self.run_installer()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr)
                self.assertFalse(self.installed.exists())
                self.assertEqual(self.read_settings()["enabled"], [])

    def test_failed_install_does_not_register_activation(self):
        binaries = self.root / "bin"
        binaries.mkdir()
        command = binaries / "gnome-extensions"
        command.write_text("#!/bin/sh\nprintf 'Simulated installation failure\\n' >&2\nexit 12\n")
        command.chmod(0o755)
        self.environment["PATH"] = str(binaries) + os.pathsep + self.environment["PATH"]
        result = self.run_installer()
        self.assertEqual(result.returncode, 12)
        self.assertFalse(self.installed.exists())
        self.assertEqual(self.read_settings()["enabled"], [])

    def test_source_build_supports_extensions_without_a_rust_helper(self):
        checkout = self.root / "source checkout"
        workspace = checkout / "gnome"
        source = workspace / "extensions/example/shell"
        source.mkdir(parents=True)
        tools = workspace / "tools"
        tools.mkdir()
        shutil.copy2(WORKSPACE / "install.sh", workspace / "install.sh")
        shutil.copy2(WORKSPACE / "tools/package.py", tools / "package.py")
        shutil.copy2(WORKSPACE.parent / "LICENSE", checkout / "LICENSE")
        (source.parent / "extension.json").write_text("{}")
        (source / "metadata.json").write_text(json.dumps({**self.metadata, "uuid": "example@example.com"}))
        shutil.copy2(SHELL / "extension.js", source / "extension.js")
        result = subprocess.run([
            "sh", str(workspace / "install.sh"), "--extension", "example", "--no-enable",
        ], cwd=self.root, env=self.environment, text=True, capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Building example", result.stdout)
        self.assertTrue((self.installed.parent / "example@example.com/extension.js").is_file())
        self.assertTrue(list((workspace / "dist").rglob("*.shell-extension.zip")))

    def test_missing_rust_does_not_install_dependencies_without_opt_in(self):
        binaries = self.root / "bin"
        binaries.mkdir()
        for name in ["cargo", "rustc", "sudo"]:
            command = binaries / name
            command.write_text("#!/bin/sh\nexit 99\n")
            command.chmod(0o755)
        self.environment["PATH"] = str(binaries) + os.pathsep + self.environment["PATH"]
        result = subprocess.run([
            "sh", str(WORKSPACE / "install.sh"),
        ], env=self.environment, text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 1)
        self.assertIn("Rust 1.85+", result.stderr)
        self.assertFalse(self.installed.exists())

    def test_fresh_rust_setup_builds_and_installs_the_extension(self):
        checkout = self.root / "fresh checkout"
        workspace = checkout / "gnome"
        source = workspace / "extensions/electricity-prices"
        source.mkdir(parents=True)
        shutil.copytree(SHELL, source / "shell")
        shutil.copy2(SHELL.parent / "extension.json", source / "extension.json")
        tools = workspace / "tools"
        tools.mkdir()
        shutil.copy2(WORKSPACE / "install.sh", workspace / "install.sh")
        shutil.copy2(WORKSPACE / "tools/package.py", tools / "package.py")
        shutil.copy2(WORKSPACE.parent / "LICENSE", checkout / "LICENSE")

        # Isolate PATH so even a developer's existing Rust installation cannot
        # bypass the bootstrap path. Downloads/toolchain builds are fixtures;
        # packaging and the per-user GNOME installation use the real tools.
        binaries = self.root / "bootstrap commands"
        binaries.mkdir()
        for name in ["id", "uname", "dirname", "python3", "gnome-shell", "gnome-extensions",
                     "gjs", "glib-compile-schemas", "cc", "mktemp", "sh", "rm", "mkdir", "cp", "chmod"]:
            (binaries / name).symlink_to(shutil.which(name))
        rust_tools = self.root / "rust tool fixtures"
        rust_tools.mkdir()
        (rust_tools / "rustc").write_text("#!/bin/sh\nprintf 'rustc 1.85.0 (test)\\n'\n")
        (rust_tools / "cargo").write_text('''#!/bin/sh
test "$1" = build || exit 91
target_dir=
while [ "$#" -gt 0 ]; do
    if [ "$1" = --target-dir ]; then target_dir=$2; shift; fi
    shift
done
test -n "$target_dir" || exit 92
mkdir -p "$target_dir/release"
cp "$RUST_HELPER_FIXTURE" "$target_dir/release/porssiohjain-prices"
chmod +x "$target_dir/release/porssiohjain-prices"
''')
        for tool in rust_tools.iterdir():
            tool.chmod(0o755)
        bootstrap = self.root / "rustup fixture.sh"
        bootstrap.write_text('''#!/bin/sh
python3 -c 'import json, os, sys; open(os.environ["RUSTUP_ARGUMENTS_FILE"], "w").write(json.dumps(sys.argv[1:]))' "$@"
# The real rustup-init rejects options after a literal -- in the same way.
for argument in "$@"; do
    if [ "$argument" = -- ]; then
        printf "error: unexpected argument '-y' found\\n" >&2
        exit 2
    fi
done
mkdir -p "$CARGO_HOME/bin"
cp "$RUST_TOOLS_FIXTURE/cargo" "$RUST_TOOLS_FIXTURE/rustc" "$CARGO_HOME/bin/"
''')
        curl = binaries / "curl"
        curl.write_text('''#!/bin/sh
output=
while [ "$#" -gt 0 ]; do
    if [ "$1" = -o ]; then output=$2; shift; fi
    shift
done
test -n "$output" || exit 93
cp "$RUSTUP_SCRIPT_FIXTURE" "$output"
''')
        curl.chmod(0o755)
        arguments = self.root / "rustup arguments.json"
        self.environment.update({
            "PATH": str(binaries),
            "CARGO_HOME": str(self.root / "fresh cargo"),
            "RUSTUP_HOME": str(self.root / "fresh rustup"),
            "RUSTUP_ARGUMENTS_FILE": str(arguments),
            "RUSTUP_SCRIPT_FIXTURE": str(bootstrap),
            "RUST_TOOLS_FIXTURE": str(rust_tools),
            "RUST_HELPER_FIXTURE": str(self.stage / "libexec" / BINARY),
            "TMPDIR": str(self.root),
        })
        result = subprocess.run([
            "sh", str(workspace / "install.sh"), "--install-deps", "--no-enable",
        ], cwd=self.root, env=self.environment, text=True, capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(json.loads(arguments.read_text()), [
            "-y", "--profile", "minimal", "--no-modify-path", "--default-toolchain", "stable",
        ])
        self.assertTrue(os.access(self.installed / "libexec" / BINARY, os.X_OK))
        self.assertEqual(list(self.root.glob("porssiohjain-gnome-install.*")), [])

    def test_unknown_options_and_missing_values_fail_without_installing(self):
        for options in [("--unknown",), ("--archive",), ("--extension",), ("--extension", "../escape")]:
            with self.subTest(options=options):
                result = subprocess.run([
                    "sh", str(WORKSPACE / "install.sh"), *options,
                ], env=self.environment, text=True, capture_output=True, timeout=10)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(self.installed.exists())


if __name__ == "__main__":
    unittest.main()

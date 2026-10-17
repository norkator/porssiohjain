#!/usr/bin/env python3
"""Build a selected extension without changing the user's GNOME installation."""

import argparse
import json
import platform
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXTENSIONS = ROOT / "extensions"


def main():
    names = sorted(path.name for path in EXTENSIONS.iterdir() if (path / "extension.json").is_file())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("extension", nargs="?", choices=names, default="electricity-prices")
    parser.add_argument("--list", action="store_true", help="List available extension projects")
    args = parser.parse_args()
    if args.list:
        print("\n".join(names))
        return

    project = EXTENSIONS / args.extension
    config = json.loads((project / "extension.json").read_text())
    metadata = json.loads((project / "shell" / "metadata.json").read_text())
    uuid = metadata["uuid"]
    if not re.fullmatch(r"[a-zA-Z0-9._@-]+", uuid):
        raise SystemExit("Invalid extension UUID")
    binary = config.get("binary")
    package = config.get("cargo-package")
    if bool(binary) != bool(package):
        raise SystemExit("Rust extensions need both cargo-package and binary in extension.json")
    if binary:
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", binary):
            raise SystemExit("Invalid helper binary name")
        subprocess.run([
            "cargo", "build", "--release", "--locked", "--package", package,
            "--target-dir", str(ROOT / "target"),
        ], cwd=ROOT, check=True)

    output = ROOT / "dist" / platform.machine() / f"{uuid}.shell-extension.zip"
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="porssiohjain-gnome-") as temporary:
        stage = Path(temporary) / uuid
        shutil.copytree(project / "shell", stage)
        shutil.copy2(ROOT.parent / "LICENSE", stage / "LICENSE")
        schemas = stage / "schemas"
        if schemas.is_dir():
            subprocess.run(["glib-compile-schemas", "--strict", str(schemas)], check=True)
        if binary:
            libexec = stage / "libexec"
            libexec.mkdir()
            helper = libexec / binary
            shutil.copy2(ROOT / "target" / "release" / binary, helper)
            helper.chmod(0o755)
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for source in sorted(stage.rglob("*")):
                if source.is_file():
                    archive.write(source, source.relative_to(stage))
    print(f"\nBuilt {output}\nNative helper architecture: {platform.machine()}")


if __name__ == "__main__":
    main()

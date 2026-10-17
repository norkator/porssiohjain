#!/usr/bin/env python3
"""Prepare downloadable ZIPs and standalone install bundles from a built extension."""

import argparse
import hashlib
import json
import platform
import re
import shutil
import tarfile
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def prepare_release(extension, workspace=ROOT):
    project = workspace / "extensions" / extension
    metadata = json.loads((project / "shell/metadata.json").read_text())
    uuid = metadata["uuid"]
    architecture = platform.machine()
    if not re.fullmatch(r"[a-zA-Z0-9._@-]+", uuid):
        raise ValueError("Invalid extension UUID")
    archive = workspace / "dist" / architecture / f"{uuid}.shell-extension.zip"
    with zipfile.ZipFile(archive) as bundle:
        if json.loads(bundle.read("metadata.json"))["uuid"] != uuid or bundle.testzip():
            raise ValueError("Built archive does not match this extension or is corrupt")

    output = workspace / "dist/release"
    output.mkdir(parents=True, exist_ok=True)
    release_zip = output / f"{uuid}-linux-{architecture}.shell-extension.zip"
    shutil.copy2(archive, release_zip)

    bundle_name = f"porssiohjain-{extension}-linux-{architecture}"
    release_tar = output / f"{bundle_name}.tar.gz"
    with tempfile.TemporaryDirectory(prefix="porssiohjain-release-") as temporary:
        stage = Path(temporary) / bundle_name
        bundled_workspace = stage / "gnome"
        bundled_project = bundled_workspace / "extensions" / extension
        (bundled_project / "shell").mkdir(parents=True)
        shutil.copy2(project / "extension.json", bundled_project / "extension.json")
        shutil.copy2(project / "shell/metadata.json", bundled_project / "shell/metadata.json")
        shutil.copy2(workspace / "install.sh", bundled_workspace / "install.sh")
        bundled_archive = bundled_workspace / "dist" / architecture / archive.name
        bundled_archive.parent.mkdir(parents=True)
        shutil.copy2(archive, bundled_archive)
        shutil.copy2(workspace.parent / "LICENSE", stage / "LICENSE")
        installer = stage / "install.sh"
        installer.write_text(f'''#!/bin/sh
set -eu
bundle_dir=$(CDPATH= cd -P "$(dirname "$0")" && pwd)
exec sh "$bundle_dir/gnome/install.sh" --extension "{extension}" \\
    --archive "$bundle_dir/gnome/dist/{architecture}/{archive.name}" "$@"
''')
        installer.chmod(0o755)
        versions = ", ".join(metadata["shell-version"])
        (stage / "README.md").write_text(f'''# {metadata["name"]}

Prebuilt installation bundle for Linux {architecture}, GNOME Shell {versions}.
The compiled extension is included; no Rust toolchain or repository checkout is needed.

Extract this archive, open a terminal in the extracted folder, and run:

```sh
./install.sh
```

Run as your desktop user without sudo. If runtime tools are missing on Ubuntu:

```sh
./install.sh --install-deps
```

Log out and back in when installation finishes. The installer configures activation
for your next login and preserves existing preferences and other extensions.

Use `./install.sh --no-enable` to leave activation settings unchanged.
The extension ZIP is also available under `gnome/dist/{architecture}/`.

Open preferences:

```sh
gnome-extensions prefs {uuid}
```

See LICENSE for the Pörssiohjain Personal Use License.
''')
        with tarfile.open(release_tar, "w:gz") as bundle:
            bundle.add(stage, arcname=bundle_name)

    checksums = output / f"SHA256SUMS-{extension}-linux-{architecture}.txt"
    checksums.write_text("".join(
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
        for path in [release_zip, release_tar]
    ))
    return release_zip, release_tar, checksums


def main():
    names = sorted(path.name for path in (ROOT / "extensions").iterdir() if (path / "extension.json").is_file())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("extension", choices=names, nargs="?", default="electricity-prices")
    args = parser.parse_args()
    for path in prepare_release(args.extension):
        print(path)


if __name__ == "__main__":
    main()

"""Check downloadable packages and installation without a source checkout."""

import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile

WORKSPACE = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(WORKSPACE / "tools"))
from release import prepare_release


class ReleasePackagingTest(unittest.TestCase):
    extension = "electricity-prices"

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="gnome release test ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.workspace = self.root / "checkout/gnome"
        self.project = self.workspace / "extensions" / self.extension
        self.project.mkdir(parents=True)
        shutil.copytree(WORKSPACE / "extensions" / self.extension / "shell", self.project / "shell")
        shutil.copy2(WORKSPACE / "extensions" / self.extension / "extension.json", self.project / "extension.json")
        shutil.copy2(WORKSPACE / "install.sh", self.workspace / "install.sh")
        shutil.copy2(WORKSPACE.parent / "LICENSE", self.workspace.parent / "LICENSE")
        self.metadata = json.loads((self.project / "shell/metadata.json").read_text())
        self.uuid = self.metadata["uuid"]
        self.architecture = platform.machine()
        self.archive = self.workspace / "dist" / self.architecture / f"{self.uuid}.shell-extension.zip"
        self.archive.parent.mkdir(parents=True)
        self.binary = json.loads((self.project / "extension.json").read_text())["binary"]
        helper = self.project / "shell/libexec" / self.binary
        helper.parent.mkdir()
        header = bytearray(64)
        header[:6] = b"\x7fELF\x02\x01"
        machine = {"x86_64": 62, "aarch64": 183, "armv7l": 40, "i686": 3,
                   "ppc64le": 21, "riscv64": 243}[self.architecture]
        header[18:20] = machine.to_bytes(2, "little")
        helper.write_bytes(header)
        helper.chmod(0o755)
        with zipfile.ZipFile(self.archive, "w") as archive:
            for source in (self.project / "shell").rglob("*"):
                if source.is_file():
                    archive.write(source, source.relative_to(self.project / "shell"))

    def test_release_archives_preserve_the_built_zip_and_have_valid_checksums(self):
        source_bytes = self.archive.read_bytes()
        extension_zip, install_bundle, checksums = prepare_release(self.extension, self.workspace)
        self.assertEqual(extension_zip.read_bytes(), source_bytes)
        self.assertIn(self.architecture, extension_zip.name)
        for entry in checksums.read_text().splitlines():
            digest, name = entry.split("  ")
            self.assertEqual(hashlib.sha256((checksums.parent / name).read_bytes()).hexdigest(), digest)
        with tarfile.open(install_bundle) as bundle:
            base = install_bundle.name.removesuffix(".tar.gz")
            self.assertTrue(bundle.getmember(f"{base}/install.sh").mode & 0o111)
            self.assertEqual(bundle.extractfile(f"{base}/LICENSE").read(), (WORKSPACE.parent / "LICENSE").read_bytes())
            archived_zip = bundle.extractfile(f"{base}/gnome/dist/{self.architecture}/{self.archive.name}").read()
            self.assertEqual(archived_zip, source_bytes)
            self.assertNotIn("tools/package.py", "\n".join(bundle.getnames()))

    @unittest.skipUnless(shutil.which("gnome-extensions") and shutil.which("gnome-shell"), "GNOME tools required")
    def test_bundle_installs_after_the_original_checkout_is_removed(self):
        _, install_bundle, _ = prepare_release(self.extension, self.workspace)
        extracted = self.root / "extracted download"
        extracted.mkdir()
        with tarfile.open(install_bundle) as bundle:
            bundle.extractall(extracted, filter="data")
        shutil.rmtree(self.workspace.parent)
        environment = dict(os.environ)
        environment.update({
            "XDG_DATA_HOME": str(self.root / "data"),
            "XDG_CONFIG_HOME": str(self.root / "config"),
            "XDG_CACHE_HOME": str(self.root / "cache"),
            "XDG_STATE_HOME": str(self.root / "state"),
            "GSETTINGS_BACKEND": "keyfile",
        })
        installer = next(extracted.glob("*/install.sh"))
        result = subprocess.run([str(installer), "--no-enable"], cwd=self.root,
                                env=environment, text=True, capture_output=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("Building", result.stdout)
        installed = self.root / "data/gnome-shell/extensions" / self.uuid
        self.assertTrue((installed / "extension.js").is_file())
        self.assertTrue(os.access(installed / "libexec" / self.binary, os.X_OK))


class WindReleasePackagingTest(ReleasePackagingTest):
    extension = "wind-forecast"

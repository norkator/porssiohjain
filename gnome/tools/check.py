#!/usr/bin/env python3
"""Check schemas and JavaScript for every extension, then run its GJS tests."""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

subprocess.run(["sh", "-n", str(ROOT / "install.sh")], check=True)
subprocess.run([
    sys.executable, "-m", "unittest", "discover", "-s", str(ROOT / "tools" / "tests"), "-p", "test_*.py",
], check=True)

for project in sorted((ROOT / "extensions").iterdir()):
    if not (project / "extension.json").is_file():
        continue
    schemas = project / "shell" / "schemas"
    if schemas.is_dir():
        subprocess.run(["glib-compile-schemas", "--strict", "--dry-run", str(schemas)], check=True)
    sources = sorted((project / "shell").glob("*.js"))
    subprocess.run(["gjs", "-m", str(ROOT / "tools" / "syntax.js"), *map(str, sources)], check=True)
    for test in sorted((project / "tests").glob("*.test.js")):
        subprocess.run(["gjs", "-m", str(test)], check=True)

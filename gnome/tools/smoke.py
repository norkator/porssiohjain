#!/usr/bin/env python3
"""Exercise built extensions in a private, headless GNOME Shell session."""

import argparse
from datetime import datetime, time, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import tempfile
import threading
import time as clock
from urllib.parse import parse_qs, urlparse
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]


class FixtureServer(ThreadingHTTPServer):
    daemon_threads = True


class FixtureHandler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def do_GET(self):
        mode = (self.server.state / "mode").read_text().strip()
        if mode == "slow":
            clock.sleep(3)
        if mode == "error":
            self.send_error(503)
            return
        request = urlparse(self.path)
        zone = ZoneInfo(parse_qs(request.query).get("timezone", ["Europe/Helsinki"])[0])
        today = datetime.now(zone).date()
        day = today + timedelta(days=1 if "tomorrow" in request.path else 0)
        start = datetime.combine(day, time.min, zone).astimezone(timezone.utc)
        end = datetime.combine(day + timedelta(days=1), time.min, zone).astimezone(timezone.utc)
        if request.path.startswith("/nordpool/"):
            points = []
            instant = start
            while instant < end:
                points.append({"timestamp": instant.isoformat(), "price": 2 + len(points) % 12})
                instant += timedelta(minutes=15)
            body = {"date": str(day), "timezone": str(zone), "resolutionMinutes": 15, "points": points}
        else:
            points = []
            end = datetime.combine(today + timedelta(days=3), time.min, zone).astimezone(timezone.utc)
            instant = start
            while instant < end:
                points.append({"startTime": instant.isoformat(),
                               "endTime": (instant + timedelta(minutes=15)).isoformat(),
                               "megawatts": 1000 + len(points) % 48 * 50})
                instant += timedelta(minutes=15)
            body = {"timezone": str(zone), "points": points}
        payload = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass  # Disabling must terminate an in-flight helper.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", action="append", type=Path,
                        help="Built archive (repeat for both extensions); defaults to dist/<architecture>")
    args = parser.parse_args()
    archives = args.archive or sorted((ROOT / "dist" / platform.machine()).glob("*.shell-extension.zip"))
    if not archives:
        parser.error("Build both extensions first with make build EXTENSION=<name>")

    with tempfile.TemporaryDirectory(prefix="porssiohjain-shell-smoke-") as temporary:
        state = Path(temporary)
        env = dict(os.environ)
        for name in ["DATA", "CONFIG", "CACHE", "STATE", "RUNTIME"]:
            directory = state / name.lower()
            directory.mkdir(mode=0o700)
            env["XDG_RUNTIME_DIR" if name == "RUNTIME" else f"XDG_{name}_HOME"] = str(directory)
        env.update({"GSETTINGS_BACKEND": "keyfile", "LIBGL_ALWAYS_SOFTWARE": "1",
                    "GNOME_SHELL_SLOWDOWN_FACTOR": "0", "PORS_SHELL_SMOKE_STATE": str(state)})
        # Never connect test clients to the user's desktop or session bus.
        for name in ["DISPLAY", "WAYLAND_DISPLAY", "DBUS_SESSION_BUS_ADDRESS"]:
            env.pop(name, None)
        env.update({"WAYLAND_DISPLAY": "porssiohjain-test", "GIO_USE_VFS": "local",
                    "GDK_BACKEND": "wayland", "GIO_USE_PROXY_RESOLVER": "dummy",
                    "GTK_A11Y": "none", "NO_AT_BRIDGE": "1"})
        settings = state / "config/glib-2.0/settings"
        settings.mkdir(parents=True)
        (settings / "keyfile").write_text(
            '[org/gnome/shell]\ndisable-user-extensions=false\nenabled-extensions=[]\n'
            'welcome-dialog-last-shown-version=\'50.0\'\n'
            '[org/gnome/desktop/interface]\nenable-animations=false\n')
        for archive in archives:
            subprocess.run(["gnome-extensions", "install", "--force", str(archive.resolve())], env=env, check=True)
        (state / "mode").write_text("ok")
        server = FixtureServer(("127.0.0.1", 0), FixtureHandler)
        server.state = state
        env["PORS_SHELL_SMOKE_URL"] = f"http://127.0.0.1:{server.server_port}"
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            command = ["dbus-run-session", "--", "sh", "-c",
                       'export DBUS_SYSTEM_BUS_ADDRESS="$DBUS_SESSION_BUS_ADDRESS"; exec "$@"', "sh",
                       "gnome-shell", "--headless", "--no-x11",
                       "--virtual-monitor", "1280x900", "--wayland-display", "porssiohjain-test",
                       "--automation-script", str(ROOT / "tools" / "smoke.js")]
            version = subprocess.check_output(["gnome-shell", "--version"], text=True).strip().split()[-1]
            log = ROOT / "dist" / f"shell-smoke-{version}.log"
            log.parent.mkdir(parents=True, exist_ok=True)
            with log.open("w") as output:
                process = subprocess.Popen(command, env=env, start_new_session=True,
                                           stdout=output, stderr=subprocess.STDOUT)
                try:
                    code = process.wait(timeout=90)
                finally:
                    try:
                        os.killpg(process.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
            lines = log.read_text().splitlines()
            print("\n".join(line for line in lines if "SMOKE:" in line))
            print(f"Shell log: {log}")
            if code:
                print("\n".join(lines[-60:]))
                raise RuntimeError("Native Shell smoke checks failed")
            if not (state / "passed").exists():
                raise RuntimeError("Shell exited without completing the smoke checks")
        finally:
            server.shutdown()
            server.server_close()
    print("Native Shell smoke checks passed for both extensions.")


if __name__ == "__main__":
    main()

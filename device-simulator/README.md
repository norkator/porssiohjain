# Device simulator

An independent TypeScript / Node.js 24 / Fastify container for demo-owned devices.
It polls the actual relay control API and accepts Toshiba state commands through
a private HTTP API. SQLite persists virtual hardware state in `/data`.

## Deployment

1. Deploy the backend with migration V122 and this service on the same private Docker network.
2. Generate a private service secret, for example `openssl rand -hex 32`, and supply
   the same `DEVICE_SIMULATOR_TOKEN` to both services. This is a service credential,
   separate from the publicly accessible demo account credentials.
3. Set `DEVICE_SIMULATOR_BASE_URL=http://device-simulator:8080` on the backend,
   and `BACKEND_BASE_URL=http://<backend-service-name>:8080` on the simulator.
   Use the backend's internal listening port. Host-published ports and localhost
   do not address another container. The URLs must be reachable in their respective
   containers; they can also point to private HTTPS endpoints on different hosts.
4. Mount a persistent volume at `/data`. [compose.example.yml](compose.example.yml)
   contains service settings to merge into an existing deployment. Its `backend`
   entry extends your existing backend service; it is not a standalone stack.
5. In **Admin → Users**, open a demo account. **Device simulation** lets an admin
   enable existing generic relay / Toshiba devices and add a **Demo Toshiba**
   without manufacturer credentials. Device quotas still apply.
6. Use the existing admin support view to attach the Toshiba to heat-pump rules.
   Once discovered, its current state is available through the ordinary state
   dialog and REST API. Demo visitors retain their existing read-only access.

The container image is `ghcr.io/norkator/porssiohjain-device-simulator:latest`.
The separate workflow tests changes and publishes amd64/arm64 images on production
pushes or manual runs. Building does not deploy or create demo devices automatically.

## Behaviour

- Discovery: `GET /internal/device-simulator/devices` on the backend, every minute,
  authenticated using `Authorization: Bearer <DEVICE_SIMULATOR_TOKEN>`. Only enabled,
  simulated, demo-owned supported devices appear. No account/manufacturer credentials
  are included. Removed/disabled devices are revoked on the next successful discovery;
  inventory expires after three minutes if discovery cannot be refreshed.
- Relays: `GET /control/{uuid}` at startup and every five minutes. Responses are
  applied to virtual channels. Polls update backend API connectivity through the
  existing control path. API errors, malformed responses and empty maps apply the
  configured relay fallback (OFF by default), matching the Shelly script's policy.
  Poll timing uses completion-based intervals, rather than Shelly's wall-clock alignment.
- Toshiba: backend command dispatch sends `POST /heat-pumps/{uuid}/commands` with
  `{"stateHex":"<38 hexadecimal characters>"}`. HTTP 202 means accepted; the
  persisted command is applied after two seconds. `GET /heat-pumps/{uuid}/state`
  returns `stateHex`, `observedAt` and `simulated`. These endpoints require the
  private bearer token. Acceptance updates `lastSentStateHex`; state reads update
  `lastPolledStateHex`. Read-back is deliberately separate from acceptance.
- Temperature: a simple deterministic model moves indoor temperature gradually
  toward the commanded target in Auto/Heat/Cool modes, or toward the configured
  outdoor temperature while idle. These are simulated values, not measurements.
  This does not emulate compressor cycles, manufacturer cloud authentication or AMQP.
- Backend refreshes simulated Toshiba state every minute, independently of the
  hourly real-device refresh. A failed mock query marks the pump API offline while
  preserving its last successful state and contact time. Simulated API contact
  also expires after ten minutes. Mock operations never fall back to Toshiba cloud.
- SQLite retains applied state, pending commands and relay state across restarts.
  Re-enabling a removed device starts fresh. Changing simulation mode clears the
  backend's cached sent/polled states. Disabling simulation routes future operations
  through the normal integration, so configure real credentials before doing that
  for a pump created by the simulator feature.

The simulator provides public `/health` (process health) and `/ready` (fresh backend
inventory), plus token-protected `/devices` for inspecting relay state. No host port
publication is needed. Configuration lives in deployment environment variables;
devices cannot supply arbitrary simulator URLs.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `BACKEND_BASE_URL` | required | Backend HTTP(S) origin |
| `DEVICE_SIMULATOR_TOKEN` | required | Private secret, at least 32 characters |
| `PORT` | `8080` | Simulator listen port |
| `STATE_DB_PATH` | `./data/simulator.db`, `/data/simulator.db` in Docker | SQLite path |
| `DISCOVERY_INTERVAL_MS` | `60000` | Device discovery interval |
| `DISCOVERY_TTL_MS` | `180000` | Maximum inventory age; at least twice discovery interval |
| `RELAY_POLL_INTERVAL_MS` | `300000` | Relay polling interval |
| `RELAY_DEFAULT_STATE` | `OFF` | Fallback state, `OFF` or `ON` |
| `COMMAND_DELAY_MS` | `2000` | Command application delay |
| `OUTDOOR_TEMPERATURE` | `5` | Simulated outdoor °C, −40–45 |
| `REQUEST_TIMEOUT_MS` | `10000` | Backend request timeout |

Backend variables: `DEVICE_SIMULATOR_BASE_URL`, `DEVICE_SIMULATOR_TOKEN`,
`DEVICE_SIMULATOR_CONNECT_TIMEOUT` (3s), `DEVICE_SIMULATOR_READ_TIMEOUT` (5s).
Optional Spring property `app.device-simulator.refresh-interval` defaults to `1m`.
An unconfigured backend keeps simulator access disabled.

Admin REST equivalents use ordinary authenticated admin access:

```text
GET  /api/admin/device-simulator/accounts/{accountId}/devices
PUT  /api/admin/device-simulator/accounts/{accountId}/devices/{deviceId}
     {"simulated":true}
POST /api/admin/device-simulator/accounts/{accountId}/heat-pumps
```

## Local development

```sh
cd device-simulator
npm ci
npm test
npm run build
BACKEND_BASE_URL=http://localhost:8080 DEVICE_SIMULATOR_TOKEN='replace-with-your-private-service-token' PORT=8081 npm start
```

To verify the built image with an isolated fake backend, including a restart:

```sh
docker build -t device-simulator-smoke .
docker run --rm --entrypoint node -v "$PWD/test/container-smoke.mjs:/tmp/smoke.mjs:ro" device-simulator-smoke /tmp/smoke.mjs
```

Configure the backend simulator URL as `http://localhost:8081` for this local
two-process setup. The backend remains authoritative for schedules, prices,
priorities, overrides and ownership; this process only simulates devices.

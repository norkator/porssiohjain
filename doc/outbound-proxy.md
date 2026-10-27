# Outbound proxy fallback through Squid

The Spring backend has a shared, opt-in outbound proxy transport. Participating
clients normally connect directly; an optional Squid proxy on a second server
provides a second outgoing public IP when a direct request fails. Fallback is
disabled until configured.

Toshiba, Mitsubishi MELCloud and MELCloud Home are the first integrations using
this transport. Other services can opt in without adding another proxy or
provider-specific connection settings. Existing clients use it only when wired
to `OutboundProxyTransport`; enabling the settings does not reroute all backend
traffic.

## Request behavior

1. Start each outbound operation directly.
2. On an eligible failure, switch to Squid and retry once.
3. If the proxy attempt fails, stop. Do not retry through another proxy or switch
   back to direct during that operation.
4. Start the next operation directly again. There is no persistent proxy mode.

An operation that needs several requests, such as MELCloud Home's OAuth login,
keeps using the proxy for its remaining steps after a successful switch. Its
cookies are retained. Nested token-refresh calls share the operation's route
and fallback budget. Each failed request is replayed at most once; continuing
an OAuth flow can therefore make several successful requests through Squid.
The route/budget lives only for the current synchronous operation and thread.

| Direct failure | Behavior |
| --- | --- |
| Connection refusal, connect timeout, DNS failure, unreachable network | One proxy attempt |
| Read timeout, socket reset or premature EOF on GET/HEAD | One proxy attempt |
| HTTP 403 | One proxy attempt, configurable |
| HTTP 401 | Existing authentication handling; no proxy switch for 401 |
| HTTP 429 | No proxy switch; preserve existing rate-limit handling |
| Other HTTP errors, including 5xx | No proxy switch |
| TLS certificate/handshake failure, interruption | No proxy switch |
| Read timeout/reset after a POST or PUT | No replay; delivery may already have succeeded |

A timeout or 403 is a suspected route problem, not proof of an IP ban. A 403
can also be an account permission failure. Disable the 403 rule if a provider's
normal authorization responses make it inappropriate.

## Toshiba command transport

Toshiba commands normally use AMQPS on port 5671. If opening that connection
fails with a network/open-timeout error, the fallback uses the Azure SDK's
AMQPS-over-WebSockets transport through Squid CONNECT on port 443. The SDK's
automatic retries are disabled. A rejected SAS token may be refreshed once
before sending; an uncertain submitted command is never automatically resent.
An authorization/throttling rejection does not trigger an AMQP proxy switch.
If an earlier registration request switched the current operation to Squid,
the command connection uses that route too.

## Backend configuration

Set these variables in the **backend process/container** and restart it:

```dotenv
OUTBOUND_PROXY_ENABLED=true
OUTBOUND_PROXY_HOST=10.77.0.2
OUTBOUND_PROXY_PORT=3128
OUTBOUND_PROXY_FALLBACK_ON_FORBIDDEN=true
OUTBOUND_PROXY_CONNECT_TIMEOUT=10s
OUTBOUND_PROXY_READ_TIMEOUT=20s
```

Use the second server's reachable VPN or public address. `HOST` is a hostname or
IP only, without `http://`, a path, credentials or a port. Enabled configuration
requires a host and a valid port. Timeouts must be positive. The read timeout
also bounds MELCloud Home requests; the two durations bound Toshiba AMQP open
authentication/device sessions in whole seconds (minimum one second each).
Toshiba command acknowledgement retains its separate 30-second limit.

The matching Spring properties are `app.outbound-proxy.enabled`, `.host`,
`.port`, `.fallback-on-forbidden`, `.connect-timeout` and `.read-timeout`.
Set `OUTBOUND_PROXY_ENABLED=false` to disable fallback. Proxy authentication
is not implemented; use a source-IP allowlist and firewall, preferably over a
private VPN connection. Normal TLS certificate verification remains enabled.
JVM-wide proxy settings and `HTTP_PROXY`/`HTTPS_PROXY` are not needed.

## Squid container on the second server

The published image is `ghcr.io/norkator/porssiohjain-proxy:latest`, supporting
`linux/amd64` and `linux/arm64`. The
[`proxy-container.yml`](../.github/workflows/proxy-container.yml) GitHub Actions
workflow builds and publishes it when `compose/squid/**` or the workflow changes
on `production`. It also supports a manual **Build and Push Proxy Image** run
from GitHub's Actions tab. Publishing uses the existing `GITHUB_TOKEN` package
permissions, matching the backend and hybrid-web image workflows.

The image includes the sample configuration and validates it during the build.
Mount your edited configuration for deployment. The example Compose and config
files are in [`compose/squid`](../compose/squid); copy `compose.yml` and
`squid.conf` to the second server. Adding the container to the backend's existing
host would normally keep the same public outgoing IP.

1. In `squid.conf`, replace `192.0.2.10/32` with the backend's actual source IP as
   seen by Squid. Use its VPN IP when connecting over a VPN. Do not add a general
   `allow all` rule. The documentation IP intentionally grants no real client.
2. The `allowed_destinations` allowlist initially covers the currently integrated
   manufacturer API/authentication hosts and Azure IoT hubs. Add the API and
   authentication hosts required by any future integration. You can narrow
   `.azure-devices.net` to the exact Toshiba
   IoT hostname reported when the integration opens a connection. If a vendor
   introduces another authentication redirect host, review and add that host.
3. Set the host address Docker should listen on and start the container:

```sh
cd compose/squid
export SQUID_BIND_IP=10.77.0.2
docker compose pull
docker compose run --rm squid squid -k parse -f /etc/squid/squid.conf
docker compose up -d
docker compose logs --tail=100 squid
```

The bind IP must already exist on that server. Allow TCP 3128 only from the
backend at the host/cloud firewall as well. Docker publishes the port itself,
so apply restrictions in the cloud firewall or Docker's forwarding firewall
rules where appropriate. The sample permits only CONNECT to the allowed public
destinations on port 443 and ends with `deny all`. It does not cache/decrypt
outbound requests or require installing a proxy CA into Java.

To publish an updated image with Ubuntu/Squid security updates, run the workflow
manually. Update the deployed container with `docker compose pull` followed by
`docker compose up -d`. After changing the mounted config, run the parse command
again and `docker compose restart squid`.

For a local build instead of downloading the published image, copy the Dockerfile
too and run this in `compose/squid`:

```sh
docker build --pull -t ghcr.io/norkator/porssiohjain-proxy:latest .
docker compose up -d
```

## Using the proxy in another integration

Inject `services.proxy.OutboundProxyTransport` and create that integration's
HTTP client through it:

```java
public ExampleApiClient(OutboundProxyTransport transport) {
    this.restTemplate = transport.newRestTemplate();
}
```

For Java `HttpClient`, use `transport.newHttpClient(cookieHandler)` and send
requests through `transport.send(client, request)` to apply fallback. Creating
the Java client alone gives direct connections.

If an operation includes multiple HTTP calls or token refreshes, surround the
whole operation with `try (var ignored = transport.beginOperation())` so nested
calls share the route and retry budget. Independent operations start directly.
Also add the integration's reviewed destination hosts to Squid's allowlist.

Non-HTTP SDKs need an adapter that configures their own proxy-capable transport.
Before submitting a payload, the adapter can call
`tryProxyAfterConnectionFailure(failure, additionalEligibleCause)` within an
operation scope. The optional predicate recognizes SDK-specific wrapped network
errors; TLS failures and interruption still prevent switching. SDK error
classification and protocol selection stay in the integration, keeping the
shared proxy transport independent of Toshiba, Azure or other providers.
Do not replay commands whose delivery is uncertain.

## Check connectivity and fallback

From the backend host, test a tunnel without sending manufacturer credentials:

```sh
curl --proxy http://10.77.0.2:3128 --connect-timeout 5 --max-time 20 \
  --output /dev/null --write-out 'CONNECT=%{http_connect} HTTP=%{http_code}\n' \
  https://app.melcloud.com/Mitsubishi.Wifi.Client/User/ListDevices
```

`CONNECT=200` confirms Squid opened the tunnel. The unauthenticated application
response may be 401/403 or another vendor response. A Squid access denial means
the client IP, destination or port does not match the allowlist. Squid logs show
CONNECT destinations and byte counts, while HTTPS bodies and tokens stay
encrypted. The backend logs a warning when it switches to Squid, without adding
request URLs, bodies, credentials or exception messages to that warning.

Automated tests simulate direct errors and a separate proxy, including request
body/header preservation, bounded retries, direct recovery, rate limits,
certificate failures and uncertain commands. Run:

```sh
./gradlew compileJava test --tests '*OutboundProxyTransportTest' \
  --tests '*Toshiba*Test' --tests '*Mitsubishi*Test'
```

The tests do not contact manufacturer clouds. Validate a real Toshiba command
after deployment to confirm its hub accepts AMQPS over WebSockets; local tests
verify transport selection and retry behavior, not manufacturer availability.
Account restrictions and cloud outages can affect both routes.

References: [Squid access rules](https://www.squid-cache.org/Doc/config/http_access/),
[Squid ACLs](https://www.squid-cache.org/Doc/config/acl/),
[HTTP retry semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2).

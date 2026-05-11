# Provisioning Guide

This document describes the current device provisioning, factory testing, claim-code onboarding, MQTT profile, and OTA flow in this repository.

It is intended for:

- production or factory operators
- backend developers
- anyone integrating MQTT relay devices such as OpenBeken, Tasmota, or ESPHome

## Goals

The provisioning system exists to solve three separate problems without mixing them together:

- manufacturing needs a way to register and test hardware before it belongs to an end user
- users need a simple way to claim a pre-provisioned device into their own account
- MQTT firmware families need a generic backend model instead of one-off logic per platform

The backend therefore has:

- a factory-side domain for pre-user devices
- a normal `device` domain for user-owned devices
- an MQTT device profile layer for capability and OTA behavior

## High-Level Flow

1. A device is flashed with supported firmware such as OpenBeken, Tasmota, or ESPHome.
2. Production registers the hardware into the backend as a `factory_device`.
3. The device connects to MQTT using bootstrap credentials and a bootstrap topic root.
4. Production runs factory tests and records step results.
5. When testing passes, the device becomes claimable.
6. The device has a printed QR code or visible claim code.
7. A logged-in user looks up the device with the claim code.
8. The user claims it into their own account.
9. The backend creates a normal `device` entry and links the original `factory_device` to it.
10. OTA can be triggered either before claim or later, using an HTTP binary URL and MQTT command delivery.

## Data Model

### Factory-Side Tables

Created in [V59__create_factory_provisioning_and_ota_tables.sql](/home/norkator/Documents/GitHub/porssiohjain/src/main/resources/db/migration/V59__create_factory_provisioning_and_ota_tables.sql:1):

- `factory_device`
- `factory_test_run`
- `factory_test_step_result`
- `ota_release`
- `ota_deployment`

Extended in [V60__add_mqtt_profiles_and_claim_codes.sql](/home/norkator/Documents/GitHub/porssiohjain/src/main/resources/db/migration/V60__add_mqtt_profiles_and_claim_codes.sql:1):

- `factory_device.mqtt_device_profile`
- `factory_device.claim_code`
- `factory_device.claimed_at`
- `device.mqtt_device_profile`

### Main Device Ownership Split

`factory_device`:

- exists before a user owns the hardware
- stores factory bootstrap MQTT identity
- stores claim code used by QR or printed label
- stores platform and MQTT profile
- stores testing and OTA history

`device`:

- exists only after claim
- belongs to an account
- is used by normal controls, dashboards, and automation
- inherits MQTT credentials and profile from the provisioned factory device

## Status Model

Factory device statuses:

- `REGISTERED`
- `TESTING`
- `PASSED`
- `FAILED`
- `CLAIMED`

Only `PASSED` devices are claimable.

### Claimability Rule

A device is claimable when:

- `factory_device.status == PASSED`
- `factory_device.claimed_device_id == null`

## MQTT Device Profiles

Profiles are defined in [MqttDeviceProfile.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/entity/enums/MqttDeviceProfile.java:1).

Current profiles:

- `GENERIC_RELAY`
- `OPENBEKEN_RELAY`
- `TASMOTA_RELAY`
- `ESPHOME_RELAY`
- `GENERIC_THERMOSTAT`

Capabilities are resolved by [MqttProfileService.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/services/MqttProfileService.java:1).

Current capability model:

- `RELAY_SWITCH`
- `OTA_HTTP`
- `TELEMETRY`
- `THERMOSTAT_SETPOINT`

This is the intended expansion seam for future profile-aware behavior:

- command topic conventions
- relay payload format
- telemetry parsing
- thermostat payloads
- OTA command payload shape

## Claim Codes and QR Codes

Every `factory_device` has a `claim_code`.

Purpose:

- production can print it as QR code text
- users can type it manually if scanning is unavailable
- it avoids exposing internal database ids

Recommended QR content:

- simplest option: raw claim code such as `QR-AB12CD34`
- richer option: app-specific claim URL like `https://app.example/devices/claim/QR-AB12CD34`

The backend only requires the claim code string.

## Admin / Production API

Admin endpoints are in [AdminFactoryController.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/contollers/AdminFactoryController.java:1).

All endpoints below require an authenticated admin account.

### Factory Devices

- `GET /admin/factory/devices`
- `POST /admin/factory/devices`
- `GET /admin/factory/devices/{id}`
- `PUT /admin/factory/devices/{id}`

Example create request:

```json
{
  "serialNumber": "SER-001",
  "platform": "OPENBEKEN",
  "productModel": "Relay-2CH",
  "mqttDeviceProfile": "OPENBEKEN_RELAY"
}
```

Optional fields:

- `hardwareMac`
- `chipId`
- `firmwareVersion`
- `mqttTopicRoot`
- `mqttUsername`
- `mqttPassword`
- `claimCode`
- `metadataJson`

### Factory Testing

- `POST /admin/factory/devices/{id}/test-runs`
- `POST /admin/factory/test-runs/{runId}/steps`

Typical flow:

1. Start a test run with station name.
2. Add step results such as relay, LED, button, or telemetry checks.
3. Finalize the run with pass/fail.

Example start:

```json
{
  "stationName": "line-a-station-3",
  "notes": "first article inspection"
}
```

Example step:

```json
{
  "stepKey": "relay_1_on",
  "status": "PASSED",
  "expectedValue": "on",
  "actualValue": "on",
  "details": "fixture current draw OK",
  "finalizeRun": false
}
```

Final pass:

```json
{
  "stepKey": "final_result",
  "status": "PASSED",
  "details": "all checks passed",
  "finalizeRun": true
}
```

### Admin Claim Shortcut

- `POST /admin/factory/devices/{id}/claim`

This exists for backend/admin workflows, but the normal intended flow is user self-claim by claim code.

### OTA Release Management

- `GET /admin/factory/ota-releases`
- `POST /admin/factory/ota-releases`
- `POST /admin/factory/devices/{id}/ota-deployments`

Example OTA release:

```json
{
  "platform": "OPENBEKEN",
  "productModel": "Relay-2CH",
  "version": "1.2.3",
  "binaryUrl": "https://ota.example.com/openbeken/relay-2ch-1.2.3.bin",
  "checksumSha256": "abc123",
  "active": true
}
```

Example OTA deployment:

```json
{
  "otaReleaseId": 5
}
```

Optional `commandTemplate` allows overriding the profile default payload. Supported placeholders:

- `{url}`
- `{checksum}`
- `{version}`
- `{platform}`
- `{profile}`

## User Claim API

User-facing provisioning endpoints are in [DevicesController.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/contollers/DevicesController.java:1).

These require a normal authenticated user account.

### Lookup Provisioned Device

- `GET /devices/provisioned/{claimCode}`

Purpose:

- confirm that the QR/claim code exists
- show basic model/profile information
- tell the UI whether the device is currently claimable

Example response fields:

- `factoryDeviceId`
- `claimCode`
- `serialNumber`
- `productModel`
- `platform`
- `mqttDeviceProfile`
- `mqttCapabilities`
- `firmwareVersion`
- `lastSeenAt`
- `claimable`

### Claim Provisioned Device

- `POST /devices/provisioned/claim`

Example request:

```json
{
  "claimCode": "QR-TEST-001",
  "deviceName": "Boiler Relay",
  "timezone": "Europe/Helsinki"
}
```

Result:

- creates a normal `device`
- assigns it to the authenticated account
- preserves MQTT username/password from the factory device
- preserves MQTT profile
- marks the factory device as `CLAIMED`

## MQTT Topic and Identity Model

There are two identity phases.

### Factory Bootstrap Identity

Factory devices use:

- `factory_device.mqtt_username`
- `factory_device.mqtt_password`
- `factory_device.mqtt_topic_root`

Default topic root:

- `factory/bootstrap/{serialNumber}`

Observed inbound bootstrap topics currently include:

- `factory/bootstrap/{serial}/state`
- `factory/bootstrap/{serial}/telemetry/...`

Commands are sent to:

- `{mqttTopicRoot}/command`

### Final User Device Identity

Claimed user devices use:

- `device.uuid` as their logical root
- inherited MQTT username/password
- `device.mqtt_device_profile`

Examples:

- `{deviceUuid}/command/...`
- `{deviceUuid}/online`
- `{deviceUuid}/state/...`
- `{deviceUuid}/telemetry/...`

## RabbitMQ Authorization Rules

Broker HTTP auth is handled by [RabbitMqAuthController.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/contollers/RabbitMqAuthController.java:1).

Current design:

- clients authenticate with username/password stored in either `device` or `factory_device`
- clients are restricted to the default vhost `/`
- clients cannot write to command topics
- clients can only read their own command topics
- clients can only write their own state/telemetry/online or factory state/status topics
- unknown users are denied

This is important for OTA and provisioning safety. Do not widen those permissions casually.

## OTA Model

OTA transport is intentionally split:

- MQTT is used as the control plane
- HTTP is used as the binary delivery plane

The backend stores OTA release metadata in `ota_release`, then creates `ota_deployment` records when dispatching an update.

The current default command payload comes from `MqttProfileService`:

- OpenBeken profile: `ota_http`
- Tasmota profile: `upgrade`
- ESPHome profile: `ota_update`
- generic fallback: `ota_install`

This is only the backend command envelope. The actual firmware implementation and topic subscription behavior must match the selected profile.

## Current Backend Classes

Core provisioning classes:

- [FactoryProvisioningService.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/services/FactoryProvisioningService.java:1)
- [AdminFactoryController.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/contollers/AdminFactoryController.java:1)
- [DevicesController.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/contollers/DevicesController.java:1)
- [MqttProfileService.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/services/MqttProfileService.java:1)
- [RabbitMqAuthController.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/contollers/RabbitMqAuthController.java:1)
- [MqttListener.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/mqtt/MqttListener.java:1)

Core model classes:

- [FactoryDeviceEntity.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/entity/FactoryDeviceEntity.java:1)
- [DeviceEntity.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/entity/DeviceEntity.java:1)
- [FactoryDeviceResponse.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/services/models/FactoryDeviceResponse.java:1)
- [ProvisionedDeviceLookupResponse.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/services/models/ProvisionedDeviceLookupResponse.java:1)
- [ClaimProvisionedDeviceRequest.java](/home/norkator/Documents/GitHub/porssiohjain/src/main/java/com/nitramite/porssiohjain/services/models/ClaimProvisionedDeviceRequest.java:1)

## Recommended Production Workflow

1. Flash firmware and initial configuration.
2. Create `factory_device` in admin API.
3. Print QR label from `claimCode`.
4. Connect device to MQTT bootstrap broker/user.
5. Verify device seen in backend.
6. Run factory tests and mark pass/fail.
7. Optionally push a known-good OTA release.
8. Ship the device.
9. User logs in, scans QR code, sees device details, and claims it.

## Recommended Next Development Steps

- Make relay command publishing profile-aware instead of assuming one generic MQTT command pattern.
- Add profile-aware telemetry parsers so device health and runtime state can be normalized.
- Add explicit mobile/web UI for scan-and-claim flow.
- Add QR code generation endpoint or admin UI helper.
- Add reservation or claim-expiry rules if production inventory needs stricter handling.
- Add user-facing error states for `FAILED`, offline, or already-claimed devices.

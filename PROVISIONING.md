# Provisioning Guide

This document contains only the basic usage flow for provisioning devices.

## Purpose

Provisioning is used when:

- production adds hardware into the system before any end user owns it
- the device is tested in production
- the end user later claims that tested device into their own account with a claim code

## Basic Idea

There are two phases:

1. Production creates and tests a provisioned device.
2. The user later turns that provisioned device into their own actual device.

Production does not create the final user-owned device directly.

## Short Production Flow

If you have device X on the production line:

1. Flash device X with the target firmware.
2. Open the admin provisioning UI at `/admin/provisioning`.
3. Create a new provisioned device with at least:
    - serial number
    - platform
    - product model
    - MQTT profile
4. Let the backend generate defaults when possible.
    - `mqttTopicRoot` is prefilled from serial number
    - `mqttUsername` is prefilled from serial number
    - `mqttPassword` can be left empty and will be auto-generated
    - `claimCode` can be left empty and will be auto-generated
5. Print the claim code as QR code or label on the device or package.
6. Power the device and verify it connects.
7. Run the test flow.
8. Mark the device as passed.
9. Ship the device.

After that, the device is ready for the user claim flow.

## Admin Provisioning UI

Route:

- `/admin/provisioning`

Access:

- only available when `AccountEntity.admin == true`

Current UI supports:

- creating provisioned devices
- viewing provisioned devices
- mock testing from the UI
    - `Start test`
    - `Mark passed`
    - `Mark failed`

The mock buttons are useful when you want to simulate the production end of the process without separate tooling.

## User Claim Flow

When a device has passed production testing:

1. The user logs in.
2. The user opens the Vaadin device view.
3. In `DeviceView`, the user uses the claim-code section.
4. The user enters the claim code.
5. The user looks up the provisioned device.
6. If the device is claimable, the user gives it a final device name and timezone.
7. The user clicks `Claim device`.

Result:

- a normal user-owned device is created
- it is attached to the user account
- the provisioned device is marked as claimed

## DeviceView Claim Section

The manual claim UI is now in the Vaadin `DeviceView`.

It supports:

- entering a claim code
- looking up a provisioned device
- creating the final user device from that claim code

QR scanning is not part of this UI yet. For now, the user enters the claim code manually.

## Basic API Endpoints

Admin side:

- `GET /admin/factory/devices`
- `POST /admin/factory/devices`
- `POST /admin/factory/devices/{id}/test-runs`
- `POST /admin/factory/test-runs/{runId}/steps`

User side:

- `GET /devices/provisioned/{claimCode}`
- `POST /devices/provisioned/claim`

## OTA

OTA uses:

- MQTT for sending the update command
- HTTP for serving the firmware binary

In practice:

1. Store the firmware binary on your OTA HTTP server.
2. Create an OTA release in the backend.
3. Trigger OTA for the provisioned device or later for the claimed device.

## Notes

- A device must pass testing before it can be claimed.
- Claim code is the key thing production gives to the user.
- MQTT profile should match the firmware family, for example OpenBeken, Tasmota, or ESPHome.
- If you leave generated fields empty in the admin UI, the backend will fill them for you.

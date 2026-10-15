# Tuya Smart integration

Pörssiohjain provides a Vaadin proof of concept for linking an existing Tuya Smart account, selecting devices from its homes, and manually reading temperature, humidity, and battery properties. Account linking, discovery, sensor import, and polling were confirmed with one physical temperature/humidity sensor on 2026-10-15.

The connector is implemented in Java. It uses the sharing protocol from Tuya's Python SDK without requiring Python, a separate connector process, or a Home Assistant installation.

## Scope

- One saved Tuya account connection per Pörssiohjain account, reused by all selected Tuya devices.
- Connection setup, device selection, and manual polling in Vaadin's **Devices** view.
- Imported devices use `DevicePlatform.TUYA_SMART` and `DeviceType.TEMPERATURE_SENSOR`.
- Read-only device access: temperature, humidity, battery percentage, and reported battery state when exposed by Tuya.

Measurement history, charts, scheduled polling, device commands, Heating Planner integration, new REST endpoints, and a React setup flow are outside this proof of concept. Existing devices remain in the Tuya Smart app.

## Link an account and add a device

1. Open **Devices → Tuya Smart connection**. The add-device form also provides this connection action.
2. Find **User Code** in Tuya Smart under **Me → Settings → Account and Security**. Enter it in the required **Tuya User Code** field. **Generate new QR code** stays disabled while the field is blank or contains only whitespace.
3. Generate the QR code. In Tuya Smart, use **+ → Scan**, scan it, and approve the login. Tuya may identify the authorization as **Home Assistant**, because the connector uses that public client registration. The connection dialog explains this before scanning.
4. Return to Vaadin and choose **Complete linking after approval**. If approval is still pending, retry. If the code expires, generate a new one. A successful login displays the saved account label and the disconnect action.
5. Open the add-device form and choose **Platform = Tuya Smart**. The device type becomes **Temperature sensor**. Choose **Load homes / devices**, select a home, then select a device. A single returned home is selected automatically.
6. Adjust the local device name, timezone, and enabled setting, then save. The selected Tuya device cannot be imported twice into the same connection.
7. Open the saved device's **Manage** dialog and choose **Poll now** to display its latest reported values and cloud online state.

Account linking is normally performed once per Tuya account. Subsequent devices reuse the saved connection while authorization remains valid. Refresh the home/device list after adding devices in Tuya Smart. Imported devices keep their Tuya platform and sensor type; the manage dialog allows editing their local name, timezone, and enabled setting.

## Connection lifecycle and persistence

Flyway migration [V111__create_tuya_connection.sql](../src/main/resources/db/migration/V111__create_tuya_connection.sql) creates:

| Table | Purpose |
| --- | --- |
| `tuya_connection` | Account ownership, provider user ID, display label, and encrypted session data. The account ID is unique. |
| `tuya_device` | A local device's connection and external Tuya device ID. Both the local device mapping and the connection/external-ID pair are unique. |

The session contains the User Code, regional endpoint, terminal ID, provider user ID, access and refresh tokens, and expiry. It is encrypted using the existing [CryptoConverter](../src/main/java/com/nitramite/porssiohjain/utils/CryptoConverter.java) and `app.crypto.key`. The Tuya password is not collected. QR login attempts are temporary server memory state; they are not saved as reusable credentials.

Saved session data supports reuse across browser sessions and application restarts. Before cloud requests, the service refreshes tokens approaching expiry and saves their replacements. Database row locking serializes token refresh and cloud operations for a connection. Successful refreshes remain saved if a subsequent cloud read fails. The saved-connection label indicates that session data exists; it does not independently verify current cloud authorization.

If authorization is revoked or cannot be refreshed, generate and approve a new QR code. Reconnecting to a different Tuya account is rejected while existing device mappings refer to the original provider user ID.

**Disconnect Tuya account** sends Tuya's terminal-expiry request and clears local session data only after success. A failed request leaves the connection available for retry. Local device mappings remain for reconnecting the same account. Normal application shutdown preserves authorization. Deleting a local device removes its Tuya mapping through the database cascade.

## Polling and sensor values

**Poll now** retrieves the last status available in Tuya's cloud. It does not command the sensor to take a new physical measurement. The UI displays **Fetched at** and explicitly states that sensor measurement time is unavailable. It can display the last reported values even when Tuya reports the device offline.

The service reads the selected device's status and property definitions, then decodes supported values:

| Reading | Supported property codes |
| --- | --- |
| Temperature | `va_temperature`, `temp_current`, `temp_current_f`, `ext_temp`, `temp_current_external`, `temp_current_external_1`, `temp_current_external_2`, `temp_current_external_3` |
| Humidity | `va_humidity`, `humidity_value` |
| Battery percentage | `battery_percentage` |
| Battery state | `battery_state` |

Numeric readings require integer-type property definitions with a usable scale. For example, raw `235` with scale `1` becomes `23.5` in the declared unit. Temperature units come from the property definition or the reported `temp_unit_convert` setting; recognized Celsius/Fahrenheit spellings are normalized for display. Battery states are displayed as reported, without inventing percentages. Recognized properties with missing values or unusable numeric definitions are displayed as unavailable.

A device can be imported when its discovery data exposes a supported temperature property, its category is `wsdcg` or `qxj`, or its property definitions expose a supported temperature property. This permits selecting a recognized sensor before it reports a value. Other models and properties depend on what the sharing API exposes, which can differ from the Tuya app's capabilities. [Home Assistant's Tuya documentation](https://www.home-assistant.io/integrations/tuya/) describes that API limitation.

Readings are returned directly to the dialog. They are not persisted in measurement tables or `DeviceEntity.lastTelemetry`. Cloud requests run on background threads, with UI polling used only to deliver their results; this is separate from scheduled sensor polling.

## Implementation and protocol reference

| Component | Responsibility |
| --- | --- |
| [DeviceView](../src/main/java/com/nitramite/porssiohjain/views/DeviceView.java) | Tuya platform selection, sensor import, and the manage-dialog polling action. |
| [TuyaConnectionDialog](../src/main/java/com/nitramite/porssiohjain/views/TuyaConnectionDialog.java) | Required User Code input, QR rendering, login completion, relinking, and disconnect. |
| [TuyaDevicePicker](../src/main/java/com/nitramite/porssiohjain/views/TuyaDevicePicker.java) | Home and device discovery/selection. |
| [TuyaTask](../src/main/java/com/nitramite/porssiohjain/views/TuyaTask.java) | Background requests and delivery of results to the attached Vaadin UI. |
| [TuyaService](../src/main/java/com/nitramite/porssiohjain/services/tuya/TuyaService.java) | Account ownership, login attempts, saved sessions, token refresh, import validation, and reading conversion. |
| [TuyaApiClient](../src/main/java/com/nitramite/porssiohjain/services/tuya/TuyaApiClient.java) and [TuyaSharingCodec](../src/main/java/com/nitramite/porssiohjain/services/tuya/TuyaSharingCodec.java) | Tuya HTTP requests, signing, encryption, and response handling. |

The protocol reference is [tuya-device-sharing-sdk 0.2.15, commit 170cfd9](https://github.com/tuya/tuya-device-sharing-sdk/tree/170cfd9ee7aefb48c14e27d9ee4a45c6bc6b15c4). Its [login implementation][sdk-user] and [authenticated transport][sdk-api] define the flow used by the Java client.

The client uses the published Home Assistant registration:

| Parameter | Value |
| --- | --- |
| `client_id` | `HA_3y9q4ak7g4ephrvke` |
| `schema` | `haauthorize` |

These are public integration identifiers. The user's QR approval supplies account authorization. This remains dependent on Tuya accepting the Home Assistant registration; it does not create a separately registered Pörssiohjain integration. The live validation used one authorization without a Home Assistant installation.

Device reads use the regional endpoint returned by login:

| Purpose | Request |
| --- | --- |
| Homes | `GET /v1.0/m/life/users/homes` |
| Devices in a home | `GET /v1.0/m/life/ha/home/devices`, parameter `homeId` |
| Selected device status | `GET /v1.0/m/life/ha/devices/detail`, parameter `devIds` |
| Property definitions | `GET /v1.1/m/life/{device_id}/specifications` |

These requests use the sharing API's signed, encrypted transport. They are not interchangeable with ordinary Tuya cloud-project OpenAPI requests. See the upstream [home.py][sdk-home] and [device.py][sdk-device] implementations for the endpoint definitions.

The adapted code retains Tuya's MIT attribution. The [license notice](../src/main/resources/META-INF/THIRD_PARTY_LICENSES/tuya-device-sharing-sdk.txt) is packaged in the application JAR at `META-INF/THIRD_PARTY_LICENSES/tuya-device-sharing-sdk.txt`.

## Verification

Use Java 21 and the repository Gradle wrapper:

```sh
./gradlew compileJava test \
  --tests 'com.nitramite.porssiohjain.services.tuya.*' \
  --tests com.nitramite.porssiohjain.DeviceServiceTest \
  --tests com.nitramite.porssiohjain.views.ListCreationViewsTest
```

Automated checks cover SDK signing/encryption reference vectors, HTTP handling, token rotation, ownership, duplicate import rejection, encrypted JPA persistence, migration constraints/cascade cleanup, and Vaadin form behavior, including required User Code validation.

Live account linking, home/device discovery, import, and polling were confirmed on 2026-10-15 with one physical temperature/humidity sensor. The reported results were temperature **20.9 °C**, humidity **51%**, and battery **100%**. This validates the flow for that account and sensor; it does not establish support for every Tuya model.

Additional live lifecycle checks remain: token refresh after expiry, reuse after an application restart, and explicit disconnect/reconnect. For further sensor validation, compare returned units/values with the Tuya app and repeat polling after the sensor reports a change.

[sdk-user]: https://github.com/tuya/tuya-device-sharing-sdk/blob/170cfd9ee7aefb48c14e27d9ee4a45c6bc6b15c4/tuya_sharing/user.py
[sdk-api]: https://github.com/tuya/tuya-device-sharing-sdk/blob/170cfd9ee7aefb48c14e27d9ee4a45c6bc6b15c4/tuya_sharing/customerapi.py
[sdk-home]: https://github.com/tuya/tuya-device-sharing-sdk/blob/170cfd9ee7aefb48c14e27d9ee4a45c6bc6b15c4/tuya_sharing/home.py
[sdk-device]: https://github.com/tuya/tuya-device-sharing-sdk/blob/170cfd9ee7aefb48c14e27d9ee4a45c6bc6b15c4/tuya_sharing/device.py

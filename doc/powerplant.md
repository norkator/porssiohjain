# Powerplant

## Status

Initial Vaadin view and persistence exist. Powerplant is a free-form home process monitoring and control-room board for user-built heating or energy systems, for example wood/oil burner equipment, pumps, tanks, relays, and temperature indicators.

Current implementation:

- `views/PowerplantView.java` provides the board at `/powerplant`.
- `entity/PowerplantElementEntity.java`, `entity/enums/PowerplantElementType.java`, and `entity/repository/PowerplantElementRepository.java` persist board elements.
- `services/PowerplantService.java` owns validation, mapping, position updates, deletion, and device-control command dispatch.
- `services/models/PowerplantElementResponse.java` is the UI/service response model.
- `src/main/resources/db/migration/V91__create_powerplant_element.sql` creates `powerplant_element`.
- `HomeView.java` includes the Powerplant menu item.
- Powerplant styles live in `src/main/frontend/themes/my-theme/styles.css`.
- English/Finnish translations use the `powerplant.*` and `home.powerplant` keys.

## User-visible scope

The view is intended to behave like a small control-room panel where the user can place elements freely on a grid. Elements are added from a dialog, dragged on the board, edited, and deleted.

Supported first-version element types:

- `INDICATOR`: displays a manually configured numeric value and unit, for example temperature.
- `BUTTON`: visual/manual button placeholder, not wired to an action yet.
- `DEVICE_CONTROL`: links a STANDARD device and relay channel, then sends on/off MQTT relay commands through the existing `ControlService.sendDebugMqttRelayCommand` path.
- `EQUIPMENT`: equipment/process block with a Vaadin icon.
- `LABEL`: text label.

All elements store a Vaadin icon name. The current UI offers a curated subset of `VaadinIcon` values. Expand that list in `PowerplantView.iconChoices()` unless a broader searchable icon picker is added.

## Important constraints

- Preserve account ownership checks. Every element belongs to one account.
- Keep writes protected by `DemoAccountGuard`.
- Only `STANDARD` devices and channels `0..3` may be used for relay control in the current implementation.
- Do not route heat-pump or thermostat behavior through Powerplant unless a deliberate design is added. Existing dedicated control systems should remain authoritative.
- Device control currently sends direct relay commands and does not model acknowledged/read-back state. The UI must not imply confirmed state unless actual state tracking is added.
- Indicator values are currently configured display values, not live measurements. Do not present them as fresh telemetry until a live measurement source and freshness state are implemented.
- Do not use `DeviceEntity.lastTelemetry` as historical measurement storage. For temperature/humidity history, use the normalized Zigbee measurement pattern already used by Heating Planner.

## Likely next steps

1. Add live indicator bindings to normalized measurement sources, including fresh/stale/missing states.
2. Add read-back/acknowledged relay state display for `DEVICE_CONTROL` elements.
3. Add element size and style configuration for larger gauges, tanks, burners, pipes, and grouped panels.
4. Add links/pipes between elements if the board needs process-flow visualization.
5. Add confirmation or safety settings for critical controls such as oil burner, pump, or boiler relays.
6. Consider separating display-only controls from direct command controls so the UI can make action risk obvious.

## Focused verification

```sh
./gradlew compileJava
```

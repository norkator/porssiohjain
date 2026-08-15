# Powerplant

## Status

Initial Vaadin view and persistence exist. Powerplant is a free-form home process monitoring and control-room board for user-built heating or energy systems, for example wood/oil burner equipment, pumps, tanks, relays, and temperature indicators.

Current implementation:

- `views/PowerplantView.java` provides the board at `/powerplant`.
- `entity/PowerplantElementEntity.java`, `entity/enums/PowerplantElementType.java`, and `entity/repository/PowerplantElementRepository.java` persist board elements.
- `entity/PowerplantRuleEntity.java`, `entity/enums/PowerplantComparisonType.java`, and `entity/repository/PowerplantRuleRepository.java` persist measurement-to-device rules.
- `services/PowerplantService.java` owns validation, mapping, position updates, deletion, and device-control command dispatch.
- `services/models/PowerplantElementResponse.java` is the UI/service response model.
- `services/models/PowerplantMeasurementOptionResponse.java` describes selectable latest Zigbee measurements for indicator binding.
- `services/models/PowerplantRuleResponse.java` is the rule UI/service response model.
- `src/main/resources/db/migration/V91__create_powerplant_element.sql` creates `powerplant_element`.
- `src/main/resources/db/migration/V92__add_powerplant_measurement_binding.sql` adds indicator measurement binding fields.
- `src/main/resources/db/migration/V93__create_powerplant_rule.sql` creates `powerplant_rule`.
- `HomeView.java` includes the Powerplant menu item.
- Powerplant styles live in `src/main/frontend/themes/my-theme/styles.css`.
- English/Finnish translations use the `powerplant.*` and `home.powerplant` keys.

## User-visible scope

The view is intended to behave like a small control-room panel where the user can place elements freely on a grid. Elements are added from a dialog, dragged on the board, edited, and deleted.

Supported first-version element types:

- `INDICATOR`: displays either a manually configured numeric value/unit or the latest selected normalized Zigbee measurement.
- `BUTTON`: visual/manual button placeholder, not wired to an action yet.
- `DEVICE_CONTROL`: links a STANDARD device and relay channel, then sends on/off MQTT relay commands through the existing `ControlService.sendDebugMqttRelayCommand` path.
- `EQUIPMENT`: equipment/process block with a Vaadin icon.
- `LABEL`: text label.

All elements store a Vaadin icon name. The current UI offers a curated subset of `VaadinIcon` values. Expand that list in `PowerplantView.iconChoices()` unless a broader searchable icon picker is added.

## Rule automation

Powerplant rules connect one source indicator to one target device-control element. The source indicator must be bound to a normalized Zigbee measurement. The target must be a `DEVICE_CONTROL` element linked to a STANDARD relay device/channel.

Rules support:

- comparison types: `<`, `<=`, `>`, `>=`, and `=`;
- numeric threshold;
- optional hysteresis;
- target action `TURN_ON` or `TURN_OFF`;
- enabled flag;
- cooldown seconds;
- last evaluated time, last command time, last matched state, and last skip reason.

The evaluator sends a command only when the condition transitions from not matched to matched. While the condition remains matched, it records `Condition already matched` and does not repeatedly send commands. Hysteresis keeps the matched state active until the value moves beyond the reset band. For example, `< 55 C` with hysteresis `5` becomes matched below `55 C` and resets only after the value rises above `60 C`.

Rules are evaluated by `Scheduler.powerplantRules()` every `${powerplant.rule-evaluation-interval:1m}` and can also be run manually from the view with **Evaluate rules**.

## Important constraints

- Preserve account ownership checks. Every element belongs to one account.
- Keep writes protected by `DemoAccountGuard`.
- Only `STANDARD` devices and channels `0..3` may be used for relay control in the current implementation.
- Do not route heat-pump or thermostat behavior through Powerplant unless a deliberate design is added. Existing dedicated control systems should remain authoritative.
- Device control currently sends direct relay commands and does not model acknowledged/read-back state. The UI must not imply confirmed state unless actual state tracking is added.
- Indicator values can bind to normalized Zigbee measurements from `zigbee_device_measurement`. Bound indicators show the latest value and whether it is fresh or stale. Manual values remain supported for unbound indicators.
- Rule automation refuses to act when the source measurement is missing or stale. Current freshness is 60 minutes in `PowerplantService.MEASUREMENT_FRESHNESS`.
- Do not use `DeviceEntity.lastTelemetry` as historical measurement storage. For temperature/humidity history, use the normalized Zigbee measurement pattern already used by Heating Planner.

## Likely next steps

1. Add read-back/acknowledged relay state display for `DEVICE_CONTROL` elements.
2. Add element size and style configuration for larger gauges, tanks, burners, pipes, and grouped panels.
3. Add richer links/pipes between elements if the board needs process-flow visualization beyond rule connectors.
4. Add confirmation or safety settings for critical controls such as oil burner, pump, or boiler relays.
5. Consider separating display-only controls from direct command controls so the UI can make action risk obvious.

## Focused verification

```sh
./gradlew compileJava
```

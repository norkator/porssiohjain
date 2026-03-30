# Weather controls

Weather controls let Porssiohjain react to site weather data instead of electricity price or production.

## Prerequisite: a site with weather place

Weather controls are tied to a site. That site should have:

- A name
- `Enabled = true`
- A valid weather place, for example `Helsinki`

The app fetches forecast data for configured sites automatically on scheduled runs.

## What a weather control does

A weather control stores:

- `Name`
- `Site`

Inside the detail view, it also shows the latest weather values available for that site, including:

- Forecast timestamp
- Temperature
- Wind speed
- Humidity

The current automation logic uses temperature and humidity as weather metrics for rule matching.

## Device automation

You can link standard devices to a weather control with:

- Device
- Channel
- Weather metric
- Comparison type
- Threshold value

If the current site weather metric matches the rule, the linked channel can be controlled through that automation path.

## Heat-pump automation

Weather control is especially important for heat pumps. A heat-pump weather rule includes:

- Device
- Saved state hex
- Weather metric
- Comparison type
- Threshold value

If the rule matches, the saved state is dispatched to the heat pump.

For heat pumps, weather controls have the highest priority among the current automation types:

1. Weather control
2. Own production
3. Control

## Comparison logic

The current app supports these comparisons:

- `GREATER_THAN`
- `LESS_THAN`

Examples:

- If temperature is less than `-10`, send a stronger heating state
- If humidity is greater than `75`, switch a ventilation-related output

## Forecast handling

The app looks for the nearest stored forecast value around the current time. If no suitable weather value exists, the rule is skipped until forecast data becomes available.

## Good use cases

- Cold-weather heat-pump boost
- Humidity-based dehumidification mode
- Site-specific automation for detached homes, cabins or outbuildings

## Things to remember

- Weather controls work only with sites that have stored forecast data.
- In the current implementation, weather metrics for automation are temperature and humidity.
- If a heat pump already matches the wanted state, the app skips sending the same command again.

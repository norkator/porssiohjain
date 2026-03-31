# Controls

Pörssiohjain controls are used for price-based switching of standard device channels and for scheduled heat-pump state changes.

## What a control does

A control creates an automatic timetable from Nord Pool prices. Devices that are linked to the control follow that timetable, unless a higher-priority rule overrides it.

For standard devices, the runtime priority is:

1. Power limit
2. Own production
3. Control

For heat pumps, scheduled rules are evaluated in this order:

1. Weather control
2. Own production
3. Control

The first matching rule wins.

## Fields in a control

- `Name`: Internal name for the control.
- `Timezone`: Timezone used when daily schedules are built.
- `Max price (snt)`: Maximum accepted combined price for active hours.
- `Min price (snt)`: Used by cheapest-hours mode when `Always on below min price` is enabled.
- `Daily on minutes`: Daily runtime target in cheapest-hours mode.
- `Tax percent`: Added to Nord Pool spot price before comparison.
- `Mode`: `BELOW_MAX_PRICE`, `CHEAPEST_HOURS` or `MANUAL`.
- `Manual on`: Only used in manual mode.
- `Always on below min price`: In cheapest-hours mode, all periods at or below the minimum price are always included first.
- `Energy contract`: Stored on the control for reporting and cost context.
- `Transfer contract`: Added to the Nord Pool price when control schedules are calculated.
- `Site`: Optional site link for grouping and reporting.

## Modes

### BELOW_MAX_PRICE

The control is active whenever the combined electricity price is less than or equal to the configured maximum price.

Combined price means:

`Nord Pool price * tax + transfer price`

If no transfer contract is linked, only the taxed Nord Pool price is used.

### CHEAPEST_HOURS

The control selects the cheapest valid periods per day until the configured `Daily on minutes` target is filled.

Rules:

- Only periods with combined price less than or equal to `Max price` are eligible.
- If `Always on below min price` is enabled, all periods at or below `Min price` are included first.
- Runtime is rounded to 15-minute blocks.
- The schedule is generated per local day using the control timezone.

### MANUAL

The control ignores the price schedule and simply keeps linked channels on or off based on the `Manual on` checkbox.

## Linking devices

Inside the control detail view you can link:

- Standard devices by device and channel number
- Heat pumps by device and a saved Toshiba state hex

For heat pumps you can either:

- Follow the current active control schedule, or
- Apply the saved state only when the current price matches a comparison rule

## Schedule generation

Control schedules are refreshed automatically after the next-day Nord Pool import. In the current app that happens on the daily scheduled runs after new price data has been fetched.

## Good use cases

- Boiler or water heater channel control
- EV charging relays
- Night-time heating based on cheap hours
- Heat-pump state changes when spot price is low enough

## Things to remember

- Controls do not override active power-limit protection.
- For standard devices, own-production rules override controls.
- For heat pumps, weather rules and production rules override control rules.
- If a device is disabled, no control output is returned for it.

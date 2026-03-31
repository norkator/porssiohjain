# Own production

Own production sources let Pörssiohjain react to local generation, such as solar production, and use that live power value to automate devices and heat pumps.

## What a production source does

A production source stores:

- The API connection details
- Current production in kW
- Peak production in kW
- Production history for charts
- Optional site linkage

In the current app, production history older than 90 days is cleaned automatically.

## Creating a source

The source list view includes these fields:

- `Name`
- `API type`
- `App ID`
- `App Secret`
- `Email`
- `Password`
- `Station ID`
- `Enabled`

After creation, the detail view also lets you set:

- `Timezone`
- `Site`

Use the API credentials required by the selected source type. If the integration does not need some fields, they can be left empty.

## What happens after setup

The backend polls enabled sources on a schedule and updates the current production value. The production detail view shows:

- Current kW
- Peak kW
- A production chart
- Linked device and heat-pump rules

## Device automation

You can link standard device channels to a production source with:

- Device
- Channel
- Trigger kW
- Comparison type
- Action

Comparison types in the current app are:

- `GREATER_THAN`
- `LESS_THAN`

Actions in the current app are:

- `TURN_ON`
- `TURN_OFF`

Example:

- If production is greater than 3.5 kW, turn on channel 1 of a water heater relay.
- If production is less than 1.0 kW, turn off channel 2 of a load.

For standard devices, own-production rules have higher priority than normal controls but lower priority than power limits.

## Heat-pump automation

You can also link a heat pump to a production source by saving:

- Device
- State hex
- Control action
- Comparison type
- Trigger kW

When the production rule matches, the saved heat-pump state can be dispatched automatically. For heat pumps, production rules have lower priority than weather controls and higher priority than control-based rules.

## Good use cases

- Run resistive heating when there is surplus solar
- Enable EV charging only above a production threshold
- Push a heat pump into a more aggressive heating mode when solar output is high

## Things to remember

- The source must be enabled for production-based automations to match.
- The linked device must belong to the same account.
- Standard-device production rules are evaluated per device channel.
- Heat-pump production rules use the saved state hex, not a simple on/off toggle.

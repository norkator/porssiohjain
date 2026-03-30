# Power limits

Power limits protect a site or circuit by forcing linked device channels off when measured consumption for the configured interval rises above the allowed limit.

## What a power limit does

A power limit stores:

- `Name`
- `Limit kW`
- `Enabled`

In the detail view you can also manage:

- `Limit interval minutes`
- `Notifications enabled`
- `Timezone`
- `Site`
- `Energy contract`
- `Transfer contract`

The detail view also shows:

- Current kW
- Peak kW
- Current interval sum
- Consumption history chart

## Device priority

For standard device channels, power limits have the highest runtime priority. If a linked power limit is active for a channel, that channel is forced off even if a production rule or control schedule would otherwise turn it on.

Priority order:

1. Power limit
2. Own production
3. Control

## How over-limit detection works

The app receives measured values for the power limit UUID:

- Current kW
- Total kWh
- Measurement timestamp

It stores interval history and sums the consumption inside the configured interval window. If the interval sum is greater than `Limit kW`, the power limit becomes active and linked channels are turned off.

## Notifications

If notifications are enabled, the app can send a power-limit exceeded email. To avoid spam, the current implementation sends at most one notification per power limit per 24 hours.

## Linking devices

You can attach standard devices to a power limit with:

- Device
- Channel

When the limit is exceeded, those linked channels are forced off.

## Good use cases

- Main-fuse protection
- Electric heating load shedding
- Preventing too many large loads from running at the same time

## Things to remember

- Power limits apply only to linked standard device channels.
- The power-limit logic currently overrides normal control and own-production logic for those channels.
- History older than 90 days is cleaned automatically.

# Devices and sites

Most Pörssiohjain automations depend on devices and sites being configured correctly first.

## Devices

Devices are the execution targets for automation rules.

The current app supports at least these device types:

- Standard devices
- Heat pumps

### Standard devices

Standard devices are usually controlled by channel number. They are used in:

- Controls
- Own production
- Power limits
- Weather controls

### Heat pumps

Heat pumps are controlled with saved state hex values. They are used in:

- Controls
- Own production
- Weather controls

If you want automation to work reliably, keep the device enabled and confirm it is communicating normally.

## Sites

Sites are used to group automation by location. A site can include:

- Name
- Weather place
- Site type
- Enabled state

Sites matter because:

- Weather controls require a site
- Weather forecast fetching depends on the site weather place
- Controls, production sources and power limits can all be linked to a site for clearer organization

## Contracts

Some automations can also be linked to electricity contracts.

### Energy contract

Used as contextual pricing information for controls and power limits.

### Transfer contract

Used directly in control price calculations. In the current app, transfer price can be:

- Static
- Day/night based

Transfer tax amount is added on top of the selected transfer price.

## Recommended setup order

1. Create devices
2. Create sites
3. Add weather places to sites that need weather automations
4. Add electricity contracts if you want full price calculations
5. Create controls, production sources, weather controls and power limits
6. Link devices or heat pumps to each feature

## Practical recommendation

Use consistent names across devices, sites and automations. That makes it easier to keep marketing material, customer instructions and the app configuration aligned.

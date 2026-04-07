# Pörssiohjain 2000 ™

![ukjx6lcc](./doc/odw6ej6ii.png)

<b>Pörssiohjain</b> is a Spring-based web service that fetches electricity spot prices from Nord Pool and controls
devices based on those prices.

With controls, users can:

* schedule devices to run during the cheapest hours for a selected duration
* prevent devices from running when the electricity price exceeds a configured limit
* also apply manual controls

Power limits provide the system with real-time information about the building's electricity consumption. If consumption
rises too high, the system can turn controlled devices off. The goal is to reduce or avoid power-based fees.

Devices, such as Shelly devices, call the control API and receive channel-specific control states in response. In
practice, this tells the device which configured channels should be on or off.

Pörssiohjain also supports using your own electricity production for your own consumption. In the future, the system
will support optimization between self-consumption and selling electricity back to the grid.

The UI is built with Vaadin and rendered on the server side. It is intentionally kept as simple as possible because it
is
only used occasionally.

⚠️ Pörssiohjain may be used free of charge by private individuals for their own household use, either as the hosted
service or by running a private self-hosted instance.

Commercial use, resale, managed hosting, customer installations, or offering the software or a hosted instance to third
parties is not allowed without a separate written commercial license. I am developing this as a paid company product
with
broader functionality and different service tiers.

If Pörssiohjain has helped you financially, consider [donating coffee money](https://buymeacoffee.com/norkator).

## Features

* Device control based on electricity spot prices.
    * Run devices during the cheapest hours for a selected duration each day.
    * Run devices during the most expensive hours if you want to create market impact while using a fixed-price
      contract.
* Electricity consumption monitoring for power-based fees.
    * The software turns selected loads off when the configured power limit is exceeded.
    * Email alerts.
* Own electricity production monitoring.
    * Turn devices on when solar panels or another source produces the desired amount of energy.

## Installation Guide

### Service Account and Settings

#### User Account

Start by creating a user account and copy the credentials immediately, because they are shown only once.

![account](./doc/account.png)

#### Adding Devices

After logging in, add your devices and copy the UUID.
For example, in the Shelly script, this UUID is used as the `DEVICE_UUID` value.

![my-devices](./doc/my_devices.png)

#### Creating Controls

Create controls in the `My controls` view by setting the mode, tax, and maximum prices.

In each control, add the devices and channels you want to control from the menu.

### Shelly Script for Controls

Note that channel numbering starts from `0` in the configuration.

```javascript
const DEVICE_UUID = '28217a08-df0b-4d21-b2b8-66a321cc6658';
const API_URL = 'https://porssiohjain.nitramite.com/control/' + DEVICE_UUID;
const RELAY_DEFAULT_STATE = "OFF"; // ON or OFF
const DEVICE_RELAY_COUNT = 1; // How many relay outputs your shelly has in total?

function setRelay(id, state) {
    Shelly.call('Switch.Set', {id: id, on: state}, function (res, err) {
        if (err) {
            print('Failed to set relay', id, ':', JSON.stringify(err));
        } else {
            print('Relay', id, 'set to', state ? 'ON' : 'OFF');
        }
    });
}

function defaultStateBool() {
    return RELAY_DEFAULT_STATE === "ON";
}

function applyDefaultState() {
    print("Applying default relay state:", RELAY_DEFAULT_STATE);
    let fallback = defaultStateBool();
    for (let i = 0; i < DEVICE_RELAY_COUNT; i++) {
        setRelay(i, fallback);
    }
}

function getUnixTime(callback) {
    Shelly.call('Sys.GetStatus', {}, function (res, err) {
        if (!err && res && res.unixtime) {
            callback(Math.floor(res.unixtime));
        } else {
            print('Failed to get time:', JSON.stringify(err));
            callback(0);
        }
    });
}

function fetchControlData() {
    Shelly.call('HTTP.REQUEST', {
        method: 'GET',
        url: API_URL,
        headers: {'Content-Type': 'application/json'},
        timeout: 10
    }, function (res, err) {
        if (err || res.code !== 200) {
            print('API call failed:', JSON.stringify(err || res));
            applyDefaultState();
            return;
        }

        let data = {};
        try {
            data = JSON.parse(res.body);
        } catch (e) {
            print('Invalid JSON response:', res.body);
            applyDefaultState();
            return;
        }

        if (!data || Object.keys(data).length === 0) {
            print('Empty JSON response');
            applyDefaultState();
            return;
        }

        for (let key in data) {
            let state = !!data[key]; // convert 0/1 to boolean
            setRelay(parseInt(key), state);
        }
    });
}

function scheduleEveryFiveMinutes() {
    getUnixTime(function (now) {
        let nextFiveMinute = Math.floor(now / 300) * 300 + 300;
        let delay = (nextFiveMinute + 10 - now) * 1000;
        print('Next API call in', delay / 1000, 'seconds');

        Timer.set(delay, false, function () {
            fetchControlData();
            scheduleEveryFiveMinutes();
        });
    });
}

fetchControlData();
scheduleEveryFiveMinutes();
```

### Shelly Script for Power Limits

Create a power limit in the service and copy the power limit UUID. Set it in the script below, which should be installed
on the sending device, such as a Shelly Pro 3EM.

```javascript
const DEVICE_UUID = '28217a08-df0b-4d21-b2b8-66a321cc6658';
const API_URL = 'https://porssiohjain.nitramite.com/power/' + DEVICE_UUID;
const POLL_INTERVAL_MS = 1 * 60 * 1000; // every minute

function sendCurrentKw() {
    let emStatus = Shelly.getComponentStatus("em", 0);

    if (!emStatus) {
        print("EM component unavailable");
        return;
    }

    let totalW = 0;
    if (typeof emStatus.total_act_power === "number") {
        totalW = emStatus.total_act_power;
    } else {
        totalW =
            (typeof emStatus.a_act_power === "number" ? emStatus.a_act_power : 0) +
            (typeof emStatus.b_act_power === "number" ? emStatus.b_act_power : 0) +
            (typeof emStatus.c_act_power === "number" ? emStatus.c_act_power : 0);
    }


    let emData = Shelly.getComponentStatus("emdata", 0);

    let totalWh = 0;
    if (emData) {
        if (typeof emData.total_act === "number") {
            totalWh = emData.total_act;
        } else {
            totalWh =
                (typeof emData.a_total_act_energy === "number" ? emData.a_total_act_energy : 0) +
                (typeof emData.b_total_act_energy === "number" ? emData.b_total_act_energy : 0) +
                (typeof emData.c_total_act_energy === "number" ? emData.c_total_act_energy : 0);
        }
    }

    let currentKw = totalW / 1000;
    let totalKwh = totalWh / 1000;
    let measuredAt = Date.now();

    let body = JSON.stringify({
        currentKw: currentKw,
        totalKwh: totalKwh,
        measuredAt: measuredAt
    });

    Shelly.call("HTTP.REQUEST", {
        method: "POST",
        url: API_URL,
        headers: {"Content-Type": "application/json"},
        body: body,
        timeout: 10
    }, function (res, err) {
        if (err || res.code !== 200) {
            print("API POST failed:", JSON.stringify(err || res));
            return;
        }
        print("Successfully sent currentKw:", currentKw, "kW");
    });
}

sendCurrentKw();
Timer.set(POLL_INTERVAL_MS, true, sendCurrentKw);
```

### Environment Variables

```env
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=porssiohjain
export DB_USER=porssiohjain
export DB_PASSWORD=porssiohjain
export FINGRID_API_ENABLED=true
export FINGRID_API_KEY=xxxxxx
export RESEND_API_KEY=re_xxxxxx
export ALERTS_MAIL_ADDRESS=xxxxxx
export ADMIN_MAIL_ADDRESS=xxxxxx@xxxxxx.xx
export APP_CRYPTO_KEY=(run node app_crypto_key.js)
export MQTT_BROKER_ADDRESS=localhost
export MQTT_BROKER_PASSWORD=xxxxxx
export MQTT_CLIENT_ID=xxxxxx
``` 

## License

This project is licensed under the Pörssiohjain Personal Use License v1.0.

Private individuals may self-host it for their own household use. Commercial use, resale, managed hosting, customer
installations, or offering it as a service to third parties requires a separate written commercial license.

See the LICENSE file for details.

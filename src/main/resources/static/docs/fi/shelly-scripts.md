# Shelly-laiteskriptit

Tämä sivu sisältää nykyiset Shelly-skriptiesimerkit Pörssiohjainin käyttöön:

- Releohjaus laitteen ohjaustilojen perusteella.
- Tehorajan mittaustiedon lähetys Shelly Pro 3EM:llä.

## Ennen aloittamista

### 1. Luo tili ja laite

Luo ensin palveluun käyttäjätili ja tallenna tunnukset talteen.

Kirjautumisen jälkeen lisää laite sovellukseen ja kopioi laitteen UUID. Tätä UUID:ta käytetään skripteissä kentässä
`DEVICE_UUID`.

### 2. Luo automaatio palvelussa

Releohjausta varten:

- Luo ohjaus `My controls` -näkymässä
- Aseta ohjauksen halutut asetukset
- Liitä halutut laitekanavat ohjaukseen

Tehorajavalvontaa varten:

- Luo palvelussa tehoraja
- Kopioi tehorajan UUID talteen
- Aseta tämä UUID mittausskriptiin, joka ajetaan lähettävällä laitteella, esimerkiksi Shelly Pro 3EM:llä

## Shelly-skripti rele ohjauksille

Huomioi, että kanavien numerointi alkaa arvosta `0`.

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

## Shelly-skripti tehorajoille (energian mittaus)

Tämä skripti on tarkoitettu mittaustietoa lähettävälle laitteelle, esimerkiksi Shelly Pro 3EM:lle.

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

## Huomioita

- Releohjausskripti kyselee ohjausrajapintaa ja asettaa palvelun palauttamat kanavien tilat laitteelle.
- Tehorajaskripti lähettää nykyisen tehon ja kokonaisenergian power-rajapintaan kerran minuutissa.
- Päivitä UUID:t ja palveluosoite vastaamaan omaa asennustasi.

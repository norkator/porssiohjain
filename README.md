# Pörssiohjain

![ukjx6lcc](./doc/ukjx6lcc.png)

Pörssiohjain on Springin päälle rakennettu yksinkertainen online sovellus, joka hakee Nordpoolista pörssisähkön hinnat
ja päivittää ohjaukset. Ohjauksien on tarkoitus antaa käyttäjälle mahdollisuus säätää laitteita päälle halvimmilla
tunneilla halutun aikaa, katkaista ohjauksia kun hinta nousee arvoa korkeammaksi tai tehdä erilaisia manuaalisia
ohjauksia. Tehorajoituksilla voidaan palveluun tuoda tietoa talon reaaliaikaisesta kulutuksesta ja tämän perusteella
kytkeä ohjauksia pois päältä. Tällä on ajatus purea tehomaksuihin.
Laitteesi esim Shelly kutsuu control rajapintaa ja saa vastauksena kanavakohtaiset ohjauksien
tilat, eli käytännössä konfiguroidut kavananumerot ja tila päälle/pois päältä.

Käyttöliittymä on tehty Vaadinilla ja renderöityy palvelimen puolella. Käyttöliittymän tarkoitus on olla mahdollisimman
yksinkertainen kun tavoite on että siellä käydään harvoin.

⚠️ En takaa minkäänlaista uptimea esimerkin palvelimelle (tälle palvelulle). Jos sinulla on tarjota virtuaalikone,
kubernetes, docker tai vastaava muu ympäristö missä ajaa tätä sovellusta niin ota yhteyttä.

## Asennusohje

### Palvelun tili ja asetukset

#### Käyttäjätili

Aloita luomalla käyttäjätili ja kopioi tiedot talteen, sillä ne näkyvät vain tämän kerran.

![account](./doc/account.png)

#### Laitteiden lisäys

Kirjautumisen jälkeen mene lisäämään laitteet ja ota UUID talteen.
UUID laitetaan esim Shelly scriptissä `DEVICE_UUID` kohtaan.

![my-devices](./doc/my_devices.png)

#### Ohjausten tekeminen

Luo ohjaukset `My controls` näkymässä asettaen moodi, vero, max hinnat.

Ohjauksissa lisätään valikosta laitteet ja kanavat mitä haluat ohjata.

### Shelly skripti (ohjauksiin)

Huomioi, että konfiguroinnissa kanavat alkavat arvosta `0`

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

### Shelly skripti (tehorajat)

Luo palvelussa tehoraja ja ota tehorajan UUID talteeen. Aseta se alla olevaan scriptiin, joka tulee
lähettävälle laitteelle esim Shelly Pro 3EM.

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

    let currentKw = totalW / 1000;

    let body = JSON.stringify({currentKw: currentKw});

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

### Ympäristömuuttujat

```env
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=porssiohjain
export DB_USER=porssiohjain
export DB_PASSWORD=porssiohjain
export FINGRID_API_KEY=xxxxxx
``` 

# Tehorajat

Tehorajat suojaavat kohdetta tai ryhmää pakottamalla liitetyt laitekanavat pois päältä, kun mitattu kulutus ylittää
asetetun rajan valitulla tarkasteluvälillä.

## Mitä tehoraja tekee

Tehoraja tallentaa:

- `Nimi`
- `Raja kW`
- `Käytössä`

Tarkemmassa näkymässä voidaan lisäksi hallita:

- `Raja-ajan minuutit`
- `Ilmoitukset käytössä`
- `Aikavyöhyke`
- `Kohde`
- `Sähkösopimus`
- `Siirtosopimus`

Tarkempi näkymä näyttää myös:

- Nykyinen kW
- Huippu-kW
- Nykyisen tarkasteluvälin summa
- Kulutushistoriakaavion

## Laiteprioriteetti

Tavallisilla laitekanavilla tehorajat ovat korkein ajonaikainen prioriteetti. Jos kanavalle liitetty tehoraja on
aktiivinen, kanava pakotetaan pois päältä, vaikka tuotantosääntö tai ohjausaikataulu yrittäisi kytkeä sen päälle.

Prioriteettijärjestys:

1. Tehoraja
2. Oma tuotanto
3. Ohjaus

## Miten ylitys havaitaan

Sovellus vastaanottaa tehorajan UUID:lle mitatut arvot:

- Nykyinen kW
- Kokonais-kWh
- Mittausaikaleima

Sovellus tallentaa historiatiedon ja laskee kulutuksen summan valitun tarkasteluvälin sisällä. Jos summakulutus on
suurempi kuin `Raja kW`, tehoraja aktivoituu ja liitetyt kanavat kytketään pois päältä.

## Ilmoitukset

Jos ilmoitukset ovat käytössä, sovellus voi lähettää sähköpostin tehorajan ylittymisestä. Roskapostin estämiseksi
nykyinen toteutus lähettää enintään yhden ilmoituksen per tehoraja 24 tunnin aikana.

## Laitteiden liittäminen

Tehorajaan voidaan liittää tavallisia laitteita näillä tiedoilla:

- Laite
- Kanava

Kun raja ylittyy, nämä liitetyt kanavat pakotetaan pois päältä.

## Hyviä käyttökohteita

- Pääsulakkeen suojaus
- Sähkölämmityksen kuormien pudotus
- Suurten kuormien yhtäaikaisen käytön rajoittaminen

## Muista nämä

- Tehorajat koskevat vain liitettyjä tavallisia laitekanavia.
- Nykyinen tehorajalogiiikka ohittaa näillä kanavilla tavalliset ohjaukset ja oman tuotannon logiikan.
- Yli 90 päivää vanha historia poistetaan automaattisesti.

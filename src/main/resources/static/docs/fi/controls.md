# Ohjaukset

Pörssiohjainin ohjauksilla ohjataan tavallisten laitekanavien kytkentää sähkön hinnan perusteella sekä lämpöpumppujen
tilamuutoksia ajastetusti.

## Mitä ohjaus tekee

Ohjaus muodostaa automaattisen aikataulun Nord Poolin hintojen perusteella. Ohjaukseen liitetyt laitteet seuraavat tätä
aikataulua, ellei jokin korkeampiprioriteettinen sääntö ohita sitä.

Tavallisille laitteille ajonaikainen prioriteetti on:

1. Tehoraja
2. Oma tuotanto
3. Ohjaus

Lämpöpumpuille ajastettujen sääntöjen järjestys on:

1. Sääohjaus
2. Oma tuotanto
3. Ohjaus

Ensimmäinen täsmäävä sääntö voittaa.

## Ohjauksen kentät

- `Nimi`: Ohjauksen sisäinen nimi.
- `Aikavyöhyke`: Aikavyöhyke, jonka mukaan päiväkohtaiset aikataulut muodostetaan.
- `Maksimihinta (snt)`: Suurin hyväksytty kokonaishinta aktiivisille tunneille.
- `Minimihinta (snt)`: Käytetään halvimmat tunnit -tilassa, kun `Aina päällä kun alle minimihinnan` on käytössä.
- `Päivittäiset päällä-minuutit`: Päivittäinen käyttöaikatavoite halvimmat tunnit -tilassa.
- `Veroprosentti`: Lisätään Nord Poolin spot-hintaan ennen vertailua.
- `Tila`: `BELOW_MAX_PRICE`, `CHEAPEST_HOURS` tai `MANUAL`.
- `Manuaalinen päällä`: Käytössä vain manuaalitilassa.
- `Aina päällä kun alle minimihinnan`: Halvimmat tunnit -tilassa kaikki minimihinnan alittavat tai siihen osuvat jaksot
  valitaan ensin.
- `Sähkösopimus`: Tallennetaan ohjaukseen raportointia ja kustannuskontekstia varten.
- `Siirtosopimus`: Lisätään Nord Pool -hintaan, kun ohjauksen aikataulu lasketaan.
- `Kohde`: Valinnainen kohdeliitos ryhmittelyä ja raportointia varten.

## Tilat

### BELOW_MAX_PRICE

Ohjaus on aktiivinen aina, kun sähkön kokonaishinta on pienempi tai yhtä suuri kuin asetettu maksimihinta.

Kokonaishinta tarkoittaa:

`Nord Pool -hinta * verot + siirtohinta`

Jos siirtosopimusta ei ole liitetty, käytetään vain verollista Nord Pool -hintaa.

### CHEAPEST_HOURS

Ohjaus valitsee päivän halvimmat kelvolliset jaksot, kunnes `Päivittäiset päällä-minuutit` -tavoite täyttyy.

Säännöt:

- Vain jaksot, joiden kokonaishinta on pienempi tai yhtä suuri kuin `Maksimihinta`, ovat kelvollisia.
- Jos `Aina päällä kun alle minimihinnan` on käytössä, kaikki `Minimihinnan` alittavat tai siihen osuvat jaksot otetaan
  ensin.
- Käyttöaika pyöristetään 15 minuutin lohkoihin.
- Aikataulu muodostetaan paikallisen päivän mukaan ohjauksen aikavyöhykettä käyttäen.

### MANUAL

Ohjaus ohittaa hintapohjaisen aikataulun ja pitää liitetyt kanavat päällä tai pois päältä `Manuaalinen päällä` -valinnan
mukaan.

## Laitteiden liittäminen

Ohjauksen tarkemmassa näkymässä voidaan liittää:

- Tavallisia laitteita laitteen ja kanavanumeron perusteella
- Lämpöpumppuja laitteen ja tallennetun Toshiba state hex -arvon perusteella

Lämpöpumpuille voidaan:

- Seurata aktiivista ohjausaikataulua, tai
- Käyttää tallennettua tilaa vain silloin, kun nykyinen hinta täyttää vertailusäännön

## Aikataulun muodostus

Ohjausten aikataulut päivitetään automaattisesti seuraavan päivän Nord Pool -tuonnin jälkeen. Nykyisessä sovelluksessa
tämä tapahtuu päivittäisissä ajastetuissa ajoissa uuden hintadatan latauksen jälkeen.

## Hyviä käyttökohteita

- Varaajan tai käyttövesivastuksen kanavaohjaus
- Sähköauton latausreleet
- Yölämmitys halpojen tuntien perusteella
- Lämpöpumpun tilamuutokset, kun spot-hinta on tarpeeksi alhainen

## Muista nämä

- Ohjaukset eivät ohita aktiivista tehorajaa.
- Tavallisilla laitteilla oman tuotannon säännöt ohittavat ohjaukset.
- Lämpöpumpuilla sää- ja tuotantosäännöt ohittavat ohjaussäännöt.
- Jos laite on poistettu käytöstä, sille ei palauteta ohjauslähtöä.

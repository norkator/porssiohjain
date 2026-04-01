# Oma tuotanto

Oman tuotannon lähteillä Pörssiohjain reagoi paikalliseen tuotantoon, kuten aurinkosähköön, ja käyttää reaaliaikaista
kW-arvoa laitteiden ja lämpöpumppujen automaatioihin.

## Mitä tuotantolähde tekee

Tuotantolähde tallentaa:

- API-yhteyden tiedot
- Tämänhetkisen tuotannon kilowatteina
- Huipputuotannon kilowatteina
- Tuotantohistorian kaavioita varten
- Valinnaisen kohdeliitoksen

Nykyisessä sovelluksessa yli 90 päivää vanha tuotantohistoria poistetaan automaattisesti.

## Tuotantolähteen luonti

Lähdelistan näkymässä on nämä kentät:

- `Nimi`
- `API-tyyppi`
- `App ID`
- `App Secret`
- `Sähköposti`
- `Salasana`
- `Station ID`
- `Käytössä`

Luonnin jälkeen tarkemmassa näkymässä voidaan lisäksi asettaa:

- `Aikavyöhyke`
- `Kohde`

Käytä valitun lähdetyypin vaatimia API-tunnuksia. Jos integraatio ei tarvitse kaikkia kenttiä, ne voidaan jättää
tyhjiksi.

## Mitä tapahtuu käyttöönoton jälkeen

Taustapalvelu kyselee käytössä olevia tuotantolähteitä ajastetusti ja päivittää nykyisen tuotantoarvon. Tuotantolähteen
tarkempi näkymä näyttää:

- Nykyinen kW
- Huippu-kW
- Tuotantokaavion
- Liitetyt laite- ja lämpöpumppusäännöt

## Laiteautomaatio

Tavallisia laitekanavia voidaan liittää tuotantolähteeseen näillä tiedoilla:

- Laite
- Kanava
- Laukaisuraja kW
- Vertailutyyppi
- Toiminto

Nykyisessä sovelluksessa vertailutyypit ovat:

- `GREATER_THAN`
- `LESS_THAN`

Toiminnot ovat:

- `TURN_ON`
- `TURN_OFF`

Esimerkki:

- Jos tuotanto on yli 3.5 kW, kytke varaajan relekanava 1 päälle.
- Jos tuotanto on alle 1.0 kW, kytke kuorman kanava 2 pois päältä.

Tavallisilla laitteilla oman tuotannon säännöt ovat korkeammalla prioriteetilla kuin tavalliset ohjaukset, mutta
alempana kuin tehorajat.

## Lämpöpumppuautomaatio

Lämpöpumppu voidaan liittää tuotantolähteeseen tallentamalla:

- Laite
- State hex
- Ohjaustoiminto
- Vertailutyyppi
- Laukaisuraja kW

Kun tuotantosääntö täsmää, tallennettu lämpöpumpun tila voidaan lähettää automaattisesti. Lämpöpumpuilla tuotantosäännöt
ovat sääohjausten alapuolella mutta ohjauspohjaisten sääntöjen yläpuolella.

## Hyviä käyttökohteita

- Vastuslämmityksen käynnistys aurinkoylijäämällä
- Sähköauton latauksen salliminen vain tuotantorajan ylittyessä
- Lämpöpumpun aggressiivisempi lämmitystila korkean aurinkotuotannon aikana

## Muista nämä

- Lähteen on oltava käytössä, jotta tuotantopohjaiset automaatiot voivat täsmätä.
- Liitetyn laitteen on kuuluttava samalle tilille.
- Tavallisten laitteiden tuotantosäännöt arvioidaan kanavakohtaisesti.
- Lämpöpumppujen tuotantosäännöt käyttävät tallennettua state hex -arvoa, eivät pelkkää päälle/pois-logiikkaa.

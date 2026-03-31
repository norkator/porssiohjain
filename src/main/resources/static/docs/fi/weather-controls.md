# Sääohjaukset

Sääohjaukset antavat Pörssiohjainille mahdollisuuden reagoida kohteen säätietoihin sähkön hinnan tai oman tuotannon
sijaan.

## Esivaatimus: kohde, jolla on sääpaikka

Sääohjaukset sidotaan kohteeseen. Kohteella tulisi olla:

- Nimi
- `Käytössä = true`
- Kelvollinen sääpaikka, esimerkiksi `Helsinki`

Sovellus hakee säätiedot määritetyille kohteille automaattisesti ajastetuissa ajoissa.

## Mitä sääohjaus tekee

Sääohjaus tallentaa:

- `Nimi`
- `Kohde`

Tarkemmassa näkymässä se näyttää myös kohteen viimeisimmät säätiedot, mukaan lukien:

- Ennusteen aikaleima
- Lämpötila
- Tuulen nopeus
- Kosteus

Nykyinen automaatiologiikka käyttää sääsääntöjen vertailuun lämpötilaa ja kosteutta.

## Laiteautomaatio

Tavallisia laitteita voidaan liittää sääohjaukseen näillä tiedoilla:

- Laite
- Kanava
- Säämittari
- Vertailutyyppi
- Kynnysarvo

Jos kohteen nykyinen sääarvo täsmää sääntöön, liitettyä kanavaa voidaan ohjata tämän automaation kautta.

## Lämpöpumppuautomaatio

Sääohjaus on erityisen hyödyllinen lämpöpumpuille. Lämpöpumpun sääsääntö sisältää:

- Laite
- Tallennettu state hex
- Säämittari
- Vertailutyyppi
- Kynnysarvo

Jos sääntö täsmää, tallennettu tila lähetetään lämpöpumpulle.

Lämpöpumpuilla sääohjauksilla on nykyisistä automaatiotyypeistä korkein prioriteetti:

1. Sääohjaus
2. Oma tuotanto
3. Ohjaus

## Vertailulogiikka

Nykyinen sovellus tukee näitä vertailuja:

- `GREATER_THAN`
- `LESS_THAN`

Esimerkkejä:

- Jos lämpötila on alle `-10`, lähetä voimakkaampi lämmitystila
- Jos kosteus on yli `75`, vaihda ilmanvaihtoon tai kuivaukseen liittyvä tila

## Ennusteen käyttö

Sovellus etsii nykyhetken ympäriltä lähimmän tallennetun ennustearvon. Jos sopivaa sääarvoa ei löydy, sääntö ohitetaan
siihen asti, kunnes ennustedataa on saatavilla.

## Hyviä käyttökohteita

- Kovan pakkasen lämpöpumpputehostus
- Kosteuteen perustuva kuivaustila
- Kohdekohtainen automaatio omakotitaloihin, mökeille tai piharakennuksiin

## Muista nämä

- Sääohjaukset toimivat vain kohteilla, joille on tallennettua säätietoa.
- Nykyisessä toteutuksessa automaation säämittarit ovat lämpötila ja kosteus.
- Jos lämpöpumppu on jo halutussa tilassa, samaa komentoa ei lähetetä uudelleen.

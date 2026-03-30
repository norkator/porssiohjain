# Laitteet ja kohteet

Suurin osa Porssiohjainin automaatioista toimii kunnolla vasta, kun laitteet ja kohteet on määritetty oikein.

## Laitteet

Laitteet ovat automaatiosääntöjen toteutuskohteita.

Nykyinen sovellus tukee ainakin näitä laitetyyppejä:

- Tavalliset laitteet
- Lämpöpumput

### Tavalliset laitteet

Tavallisia laitteita ohjataan yleensä kanavanumerolla. Niitä käytetään:

- Ohjauksissa
- Oman tuotannon automaatioissa
- Tehorajoissa
- Sääohjauksissa

### Lämpöpumput

Lämpöpumppuja ohjataan tallennetuilla state hex -arvoilla. Niitä käytetään:

- Ohjauksissa
- Oman tuotannon automaatioissa
- Sääohjauksissa

Jos haluat automaation toimivan luotettavasti, pidä laite käytössä ja varmista, että sen yhteydet toimivat normaalisti.

## Kohteet

Kohteita käytetään automaatioiden ryhmittelyyn sijainnin mukaan. Kohteella voi olla esimerkiksi:

- Nimi
- Sääpaikka
- Kohdetyyppi
- Käytössä / ei käytössä

Kohteet ovat tärkeitä, koska:

- Sääohjaukset tarvitsevat kohteen
- Säätiedon haku perustuu kohteen sääpaikkaan
- Ohjaukset, tuotantolähteet ja tehorajat voidaan liittää kohteeseen selkeämmän hallinnan vuoksi

## Sopimukset

Joihinkin automaatioihin voidaan liittää myös sähkösopimuksia.

### Sähkösopimus

Käytetään hintakontekstina ohjauksissa ja tehorajoissa.

### Siirtosopimus

Käytetään suoraan ohjauksen hintalaskennassa. Nykyisessä sovelluksessa siirtohinta voi olla:

- Kiinteä
- Päivä/yö-hintainen

Siirron verosumma lisätään valitun siirtohinnan päälle.

## Suositeltu käyttöönottojärjestys

1. Luo laitteet
2. Luo kohteet
3. Lisää sääpaikat kohteisiin, jotka tarvitsevat sääautomaatioita
4. Lisää sähkösopimukset, jos haluat täydet hintalaskelmat
5. Luo ohjaukset, tuotantolähteet, sääohjaukset ja tehorajat
6. Liitä laitteet tai lämpöpumput kuhunkin ominaisuuteen

## Käytännön suositus

Käytä yhtenäisiä nimiä laitteissa, kohteissa ja automaatioissa. Se helpottaa markkinointimateriaalien, käyttöohjeiden ja sovelluksen asetusten pitämistä linjassa.

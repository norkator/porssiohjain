/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinnishWeatherPlaceService {

    private static final List<String> SUPPORTED_PLACES = List.of(
            "Akaa", "Alajärvi", "Alavus", "Espoo", "Forssa", "Haapajärvi", "Haapavesi", "Hamina", "Hanko",
            "Harjavalta", "Heinola", "Helsinki", "Huittinen", "Hyvinkää", "Hämeenlinna", "Iisalmi", "Ikaalinen",
            "Imatra", "Joensuu", "Jyväskylä", "Jämsä", "Järvenpää", "Kaarina", "Kajaani", "Kalajoki", "Kankaanpää",
            "Karkkila", "Kaskinen", "Kauhajoki", "Kauhava", "Kauniainen", "Kemi", "Kemijärvi", "Kerava", "Keuruu",
            "Kitee", "Kiuruvesi", "Kokemäki", "Kokkola", "Kotka", "Kouvola", "Kristiinankaupunki", "Kuhmo",
            "Kuopio", "Kurikka", "Kuusamo", "Lahti", "Laitila", "Lappeenranta", "Lapua", "Lieksa", "Lohja",
            "Loimaa", "Loviisa", "Maarianhamina", "Mikkeli", "Mänttä-Vilppula", "Naantali", "Nokia", "Nurmes",
            "Orimattila", "Orivesi", "Oulainen", "Oulu", "Outokumpu", "Paimio", "Parainen", "Parkano", "Pieksämäki",
            "Pietarsaari", "Pori", "Porvoo", "Pudasjärvi", "Pyhäjärvi", "Raahe", "Raasepori", "Raisio", "Rauma",
            "Riihimäki", "Rovaniemi", "Salo", "Sastamala", "Savonlinna", "Seinäjoki", "Somero", "Suonenjoki",
            "Tampere", "Tornio", "Turku", "Uusikaarlepyy", "Uusikaupunki", "Vaasa", "Valkeakoski", "Vantaa",
            "Varkaus", "Viitasaari", "Virrat", "Ylivieska", "Ylöjärvi", "Ähtäri"
    );

    private static final Map<String, String> SUPPORTED_PLACES_BY_KEY = SUPPORTED_PLACES.stream()
            .collect(Collectors.toUnmodifiableMap(FinnishWeatherPlaceService::normalizeKey, Function.identity()));

    public List<String> getSupportedPlaces() {
        return SUPPORTED_PLACES;
    }

    public Optional<String> findSupportedPlace(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SUPPORTED_PLACES_BY_KEY.get(normalizeKey(value)));
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

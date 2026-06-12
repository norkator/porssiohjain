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

package com.nitramite.porssiohjain.services.nordpool;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class NordpoolMarket {

    public static final String DEFAULT_MARKET = "FI";
    public static final List<String> SUPPORTED_MARKETS = List.of(
            "EE", "LT", "LV", "AT", "BE", "FR", "GER", "NL", "PL",
            "DK1", "DK2", "FI",
            "NO1", "NO2", "NO3", "NO4", "NO5",
            "SE1", "SE2", "SE3", "SE4",
            "BG", "TEL"
    );

    private static final Set<String> SUPPORTED_MARKET_SET = Set.copyOf(SUPPORTED_MARKETS);

    private NordpoolMarket() {
    }

    public static String normalize(String market) {
        String normalized = market == null || market.isBlank()
                ? DEFAULT_MARKET
                : market.trim().toUpperCase();
        if (!SUPPORTED_MARKET_SET.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported Nordpool market index: " + market);
        }
        return normalized;
    }

    public static List<String> normalizeAll(Set<String> markets) {
        return markets.stream()
                .map(NordpoolMarket::normalize)
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> splitIndexNames(String indexNames) {
        return Arrays.stream(indexNames.split(","))
                .map(NordpoolMarket::normalize)
                .distinct()
                .toList();
    }
}

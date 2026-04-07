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

package com.nitramite.porssiohjain.services.models;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class NordpoolResponse {

    private LocalDate deliveryDateCET;
    private int version;
    private Instant updatedAt;
    private String market;
    private List<String> indexNames;
    private String currency;
    private int resolutionInMinutes;
    private List<AreaState> areaStates;
    private List<MultiIndexEntry> multiIndexEntries;

    @Data
    public static class AreaState {
        private String state;
        private List<String> areas;
    }

    @Data
    public static class MultiIndexEntry {
        private Instant deliveryStart;
        private Instant deliveryEnd;
        private Map<String, BigDecimal> entryPerArea;
    }

}

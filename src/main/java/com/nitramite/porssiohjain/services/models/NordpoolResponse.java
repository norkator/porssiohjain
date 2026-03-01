/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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

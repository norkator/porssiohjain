/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0. See LICENSE.
 */
package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.ComparisonType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record HeatPumpControlPreviewResponse(String timezone, Instant generatedAt, Instant start, Instant end,
                                              List<PricePeriod> prices, List<Pump> pumps) {
    public record PricePeriod(Instant start, Instant end, BigDecimal price) { }

    public record Pump(Long id, String name, boolean competingRules, List<Period> periods) { }

    public enum Reason { PRICE, SCHEDULE, NO_MATCH, MISSING_PRICE, DISABLED, NO_AC_DATA }

    public record Period(Instant start, Instant end, Long ruleId, Reason reason, ComparisonType comparison,
                         BigDecimal threshold, String stateHex, State state, boolean assumed) { }

    /** Unknown/unsupported fields remain null rather than being interpreted as OFF or zero degrees. */
    public record State(Boolean power, String mode, Double temperature, String fan, String swing) { }
}

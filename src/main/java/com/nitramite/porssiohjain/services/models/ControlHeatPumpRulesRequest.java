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

import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** One pump's complete ordered rule set. An empty list removes its link. */
public record ControlHeatPumpRulesRequest(LocalTime activeFrom, LocalTime activeUntil, List<Rule> rules) {
    public record Rule(Long id, String stateHex, ControlAction controlAction, ComparisonType comparisonType,
                       BigDecimal priceLimit, BigDecimal estimatedPowerKw, boolean outsideActiveTime) { }
}

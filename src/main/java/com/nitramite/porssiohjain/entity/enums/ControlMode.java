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

package com.nitramite.porssiohjain.entity.enums;

public enum ControlMode {
    BELOW_MAX_PRICE, // turns ON when price < maxPriceSnt
    CHEAPEST_HOURS,  // daily cheapest hours, control on based on dailyOnMinutes
    CHEAPEST_HOURS_TOMORROW_AWARE, // two-day cheapest hours, allowing today's minutes to move into tomorrow
    MANUAL,          // manual override, use manualOn field
    SCHEDULED;       // user defined schedule

    public boolean isCheapestHours() {
        return this == CHEAPEST_HOURS || this == CHEAPEST_HOURS_TOMORROW_AWARE;
    }

    public boolean usesGeneratedControlTable() {
        return this == BELOW_MAX_PRICE || isCheapestHours();
    }
}

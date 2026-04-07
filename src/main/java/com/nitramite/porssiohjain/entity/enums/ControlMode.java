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
    MANUAL,          // manual override, use manualOn field
    SCHEDULED        // user defined schedule
}
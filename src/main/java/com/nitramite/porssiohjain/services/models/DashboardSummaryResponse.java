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

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryResponse {
    private TodayPriceStatsResponse todayPriceStats;
    private ControlSavingsSummaryResponse controlSavings;
    private int deviceCount;
    private int controlCount;
    private int siteCount;
    private int powerLimitCount;
    private int onlineDeviceCount;
    private int offlineDeviceCount;
    private int activePowerLimitAlertCount;
    private boolean onboardingCompleted;
}

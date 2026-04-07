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

import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.services.models.DashboardSummaryResponse;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.PowerLimitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final DeviceService deviceService;
    private final ControlService controlService;
    private final PowerLimitService powerLimitService;
    private final SiteRepository siteRepository;
    private final NordpoolService nordpoolService;
    private final OnboardingService onboardingService;
    private final ControlSavingsService controlSavingsService;

    public DashboardSummaryResponse getSummary(Long accountId, String timezone) {
        List<DeviceResponse> devices = deviceService.getAllDevices(accountId);
        List<PowerLimitResponse> powerLimits = powerLimitService.getAllLimits(accountId);

        int onlineDevices = (int) devices.stream()
                .filter(device -> Boolean.TRUE.equals(device.getApiOnline()) || Boolean.TRUE.equals(device.getMqttOnline()))
                .count();
        int offlineDevices = devices.size() - onlineDevices;
        int activePowerLimitAlerts = (int) powerLimits.stream()
                .filter(limit -> limit.isEnabled()
                        && limit.getCurrentKw() != null
                        && limit.getLimitKw() != null
                        && limit.getCurrentKw().compareTo(limit.getLimitKw()) > 0)
                .count();

        return DashboardSummaryResponse.builder()
                .todayPriceStats(nordpoolService.getTodayStats(accountId, timezone))
                .controlSavings(controlSavingsService.getCurrentMonthSavings(accountId, timezone))
                .deviceCount(devices.size())
                .controlCount(controlService.getAllControls(accountId).size())
                .siteCount(siteRepository.findByAccountId(accountId).size())
                .powerLimitCount(powerLimits.size())
                .onlineDeviceCount(onlineDevices)
                .offlineDeviceCount(offlineDevices)
                .activePowerLimitAlertCount(activePowerLimitAlerts)
                .onboardingCompleted(onboardingService.isCompleted(accountId))
                .build();
    }
}

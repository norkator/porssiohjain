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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.OnboardingChecklistItemResponse;
import com.nitramite.porssiohjain.services.models.OnboardingChecklistResponse;
import com.nitramite.porssiohjain.services.models.OnboardingStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final DeviceService deviceService;
    private final ControlService controlService;
    private final SiteRepository siteRepository;

    public OnboardingStatusResponse getStatus(Long accountId) {
        List<DeviceResponse> devices = deviceService.getAllDevices(accountId);
        int deviceCount = devices.size();
        int controlCount = controlService.getAllControls(accountId).size();
        int siteCount = siteRepository.findByAccountId(accountId).size();
        int deviceControlLinkCount = controlService.getAllControls(accountId).stream()
                .filter(control -> !Boolean.TRUE.equals(control.getShared()))
                .mapToInt(control -> controlService.getControlDeviceLinks(accountId, control.getId()).size())
                .sum();

        boolean hasShellySetup = devices.stream()
                .filter(device -> device.getDeviceType() == DeviceType.STANDARD)
                .anyMatch(device -> device.getLastCommunication() != null
                        || Boolean.TRUE.equals(device.getApiOnline())
                        || Boolean.TRUE.equals(device.getMqttOnline()));

        return OnboardingStatusResponse.builder()
                .hasDevices(deviceCount > 0)
                .hasControls(controlCount > 0)
                .hasDeviceControlLinks(deviceControlLinkCount > 0)
                .hasSites(siteCount > 0)
                .hasCompletedShellySetup(hasShellySetup)
                .deviceCount(deviceCount)
                .controlCount(controlCount)
                .siteCount(siteCount)
                .deviceControlLinkCount(deviceControlLinkCount)
                .build();
    }

    public OnboardingChecklistResponse getChecklist(Long accountId) {
        OnboardingStatusResponse status = getStatus(accountId);
        List<OnboardingChecklistItemResponse> items = List.of(
                OnboardingChecklistItemResponse.builder()
                        .id("create_device")
                        .title("Create first device")
                        .description("Add at least one device that the system can control.")
                        .completed(status.isHasDevices())
                        .build(),
                OnboardingChecklistItemResponse.builder()
                        .id("create_control")
                        .title("Create first control")
                        .description("Create a price-based automation control.")
                        .completed(status.isHasControls())
                        .build(),
                OnboardingChecklistItemResponse.builder()
                        .id("link_device")
                        .title("Link device to control")
                        .description("Attach a device channel to the control so it can act on schedules.")
                        .completed(status.isHasDeviceControlLinks())
                        .build(),
                OnboardingChecklistItemResponse.builder()
                        .id("setup_shelly")
                        .title("Complete Shelly setup")
                        .description("Configure your Shelly script so the device starts polling the backend.")
                        .completed(status.isHasCompletedShellySetup())
                        .build(),
                OnboardingChecklistItemResponse.builder()
                        .id("create_site")
                        .title("Create a site")
                        .description("Add a site when you need weather-based or site-based automation later.")
                        .completed(status.isHasSites())
                        .build()
        );

        String nextStep = items.stream()
                .filter(item -> !item.isCompleted())
                .map(OnboardingChecklistItemResponse::getId)
                .findFirst()
                .orElse("done");

        return OnboardingChecklistResponse.builder()
                .nextStep(nextStep)
                .items(items)
                .build();
    }

    public boolean isCompleted(Long accountId) {
        OnboardingStatusResponse status = getStatus(accountId);
        return status.isHasDevices()
                && status.isHasControls()
                && status.isHasDeviceControlLinks()
                && status.isHasCompletedShellySetup();
    }
}

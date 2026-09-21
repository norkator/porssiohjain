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
package com.nitramite.porssiohjain.services.mitsubishihome;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MitsubishiMelCloudHomeDevicesService {

    private final MitsubishiMelCloudHomeApiClient apiClient;

    public List<MitsubishiMelCloudHomeDevice> getAcDevices(DeviceAcDataEntity acData) {
        try {
            MitsubishiMelCloudHomeContextResponse context = apiClient.getContext(acData.getAcAccessToken());
            List<MitsubishiMelCloudHomeDevice> devices = new ArrayList<>();
            addBuildingDevices(devices, context.buildings(), false);
            addBuildingDevices(devices, context.guestBuildings(), true);
            return List.copyOf(devices);
        } catch (MitsubishiMelCloudHomeException e) {
            log.warn("Unable to list MELCloud Home units: {}", e.getMessage());
            return List.of();
        }
    }

    private void addBuildingDevices(
            List<MitsubishiMelCloudHomeDevice> devices,
            List<MitsubishiMelCloudHomeContextResponse.Building> buildings,
            boolean guestAccess
    ) {
        for (MitsubishiMelCloudHomeContextResponse.Building building : buildings) {
            building.airToAirUnits().stream()
                    .map(unit -> toDevice(unit, building, UnitType.AIR_TO_AIR, guestAccess))
                    .forEach(devices::add);
            building.airToWaterUnits().stream()
                    .map(unit -> toDevice(unit, building, UnitType.AIR_TO_WATER, guestAccess))
                    .forEach(devices::add);
        }
    }

    private MitsubishiMelCloudHomeDevice toDevice(
            MitsubishiMelCloudHomeContextResponse.Unit unit,
            MitsubishiMelCloudHomeContextResponse.Building building,
            UnitType unitType,
            boolean guestAccess
    ) {
        return new MitsubishiMelCloudHomeDevice(
                unit.id(),
                unit.givenDisplayName(),
                unitType,
                building.id(),
                building.name(),
                unit.isConnected(),
                unit.isInError(),
                guestAccess
        );
    }

    public enum UnitType {
        AIR_TO_AIR,
        AIR_TO_WATER
    }

    public record MitsubishiMelCloudHomeDevice(
            String id,
            String name,
            UnitType unitType,
            String buildingId,
            String buildingName,
            Boolean connected,
            Boolean inError,
            boolean guestAccess
    ) {
    }
}

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MitsubishiMelCloudHomeStateService {

    private final MitsubishiMelCloudHomeLoginService loginService;
    private final MitsubishiMelCloudHomeApiClient apiClient;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceOfflineNotificationService deviceOfflineNotificationService;
    private final ObjectMapper objectMapper;

    public MitsubishiMelCloudHomeState getAcState(DeviceAcDataEntity acData) {
        if (acData.getAcDeviceId() == null || acData.getAcDeviceId().isBlank()) {
            throw new IllegalArgumentException("MELCloud Home unit ID is required");
        }
        if (!loginService.login(acData).isSuccess()) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home login failed");
        }

        MitsubishiMelCloudHomeContextResponse context = apiClient.getContext(acData.getAcAccessToken());
        LocatedUnit locatedUnit = findUnit(context, acData.getAcDeviceId());
        MitsubishiMelCloudHomeState state = toState(locatedUnit);
        acData.setLastPolledStateHex(formatState(state));
        if (acData.getId() != null) {
            deviceAcDataRepository.save(acData);
        }
        DeviceEntity device = acData.getDevice();
        if (device != null && device.getId() != null) {
            deviceRepository.findWithAccountById(device.getId()).ifPresent(managed -> {
                boolean wasApiOnline = managed.isApiOnline();
                boolean wasMqttOnline = managed.isMqttOnline();
                boolean connected = Boolean.TRUE.equals(state.getConnected());
                Instant now = Instant.now();
                if (connected) managed.setLastCommunication(now);
                managed.setApiOnline(connected);
                deviceRepository.save(managed);
                if (connected) deviceOfflineNotificationService.sendIfDeviceCameOnline(
                        managed, wasApiOnline, wasMqttOnline, "API", now);
            });
        }
        return state;
    }

    private LocatedUnit findUnit(MitsubishiMelCloudHomeContextResponse context, String unitId) {
        LocatedUnit located = findUnit(context.buildings(), unitId, false);
        if (located == null) {
            located = findUnit(context.guestBuildings(), unitId, true);
        }
        if (located == null) {
            throw new MitsubishiMelCloudHomeException("MELCloud Home unit was not found in the account");
        }
        return located;
    }

    private LocatedUnit findUnit(
            List<MitsubishiMelCloudHomeContextResponse.Building> buildings,
            String unitId,
            boolean guestAccess
    ) {
        for (MitsubishiMelCloudHomeContextResponse.Building building : buildings) {
            for (MitsubishiMelCloudHomeContextResponse.Unit unit : building.airToAirUnits()) {
                if (unitId.equals(unit.id())) {
                    return new LocatedUnit(building, unit, MitsubishiMelCloudHomeState.UnitType.AIR_TO_AIR, guestAccess);
                }
            }
            for (MitsubishiMelCloudHomeContextResponse.Unit unit : building.airToWaterUnits()) {
                if (unitId.equals(unit.id())) {
                    return new LocatedUnit(building, unit, MitsubishiMelCloudHomeState.UnitType.AIR_TO_WATER, guestAccess);
                }
            }
        }
        return null;
    }

    private MitsubishiMelCloudHomeState toState(LocatedUnit located) {
        MitsubishiMelCloudHomeContextResponse.Unit unit = located.unit();
        Map<String, String> settings = settingsByName(unit.settings());
        MitsubishiMelCloudHomeContextResponse.Capabilities capabilities = unit.capabilities();

        MitsubishiMelCloudHomeState.MitsubishiMelCloudHomeStateBuilder builder = MitsubishiMelCloudHomeState.builder()
                .unitId(unit.id())
                .name(unit.givenDisplayName())
                .buildingId(located.building().id())
                .buildingName(located.building().name())
                .unitType(located.unitType())
                .connected(unit.isConnected())
                .inError(unit.isInError())
                .rssi(unit.rssi())
                .power(booleanSetting(settings, "Power"))
                .operationMode(settings.get("OperationMode"))
                .inStandbyMode(booleanSetting(settings, "InStandbyMode"));

        if (located.unitType() == MitsubishiMelCloudHomeState.UnitType.AIR_TO_AIR) {
            String operationMode = settings.get("OperationMode");
            builder
                    .setTemperature(doubleSetting(settings, "SetTemperature"))
                    .roomTemperature(doubleSetting(settings, "RoomTemperature"))
                    .setFanSpeed(settings.get("SetFanSpeed"))
                    .actualFanSpeed(settings.get("ActualFanSpeed"))
                    .vaneVerticalDirection(settings.get("VaneVerticalDirection"))
                    .vaneHorizontalDirection(settings.get("VaneHorizontalDirection"));
            if (capabilities != null) {
                builder.numberOfFanSpeeds(capabilities.numberOfFanSpeeds());
                applyAtaTemperatureRange(builder, capabilities, operationMode);
            }
        } else {
            builder
                    .operationModeZone1(settings.get("OperationModeZone1"))
                    .operationModeZone2(settings.get("OperationModeZone2"))
                    .setTemperatureZone1(doubleSetting(settings, "SetTemperatureZone1"))
                    .setTemperatureZone2(doubleSetting(settings, "SetTemperatureZone2"))
                    .roomTemperatureZone1(doubleSetting(settings, "RoomTemperatureZone1"))
                    .roomTemperatureZone2(doubleSetting(settings, "RoomTemperatureZone2"))
                    .setTankWaterTemperature(doubleSetting(settings, "SetTankWaterTemperature"))
                    .tankWaterTemperature(doubleSetting(settings, "TankWaterTemperature"))
                    .forcedHotWaterMode(booleanSetting(settings, "ForcedHotWaterMode"))
                    .hasZone2(booleanSetting(settings, "HasZone2"))
                    .setHeatFlowTemperatureZone1(doubleSetting(settings, "SetHeatFlowTemperatureZone1"))
                    .setCoolFlowTemperatureZone1(doubleSetting(settings, "SetCoolFlowTemperatureZone1"))
                    .setHeatFlowTemperatureZone2(doubleSetting(settings, "SetHeatFlowTemperatureZone2"))
                    .setCoolFlowTemperatureZone2(doubleSetting(settings, "SetCoolFlowTemperatureZone2"));
            if (capabilities != null) {
                builder
                        .hasZone2(capabilities.hasZone2() != null ? capabilities.hasZone2() : booleanSetting(settings, "HasZone2"))
                        .minTemperature(capabilities.minSetTemperatureZone1())
                        .maxTemperature(capabilities.maxSetTemperatureZone1());
            }
        }
        return builder.build();
    }

    private void applyAtaTemperatureRange(
            MitsubishiMelCloudHomeState.MitsubishiMelCloudHomeStateBuilder builder,
            MitsubishiMelCloudHomeContextResponse.Capabilities capabilities,
            String operationMode
    ) {
        if ("Heat".equals(operationMode)) {
            builder.minTemperature(capabilities.minTempHeat()).maxTemperature(capabilities.maxTempHeat());
        } else if ("Automatic".equals(operationMode)) {
            builder.minTemperature(capabilities.minTempAutomatic()).maxTemperature(capabilities.maxTempAutomatic());
        } else {
            builder.minTemperature(capabilities.minTempCool()).maxTemperature(capabilities.maxTempCool());
        }
    }

    private Map<String, String> settingsByName(List<MitsubishiMelCloudHomeContextResponse.Setting> settings) {
        Map<String, String> values = new LinkedHashMap<>();
        for (MitsubishiMelCloudHomeContextResponse.Setting setting : settings) {
            if (setting.name() != null) {
                values.put(setting.name(), setting.value());
            }
        }
        return values;
    }

    private Boolean booleanSetting(Map<String, String> settings, String name) {
        String value = settings.get(name);
        if (value == null) {
            return null;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private Double doubleSetting(Map<String, String> settings, String name) {
        String value = settings.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String formatState(MitsubishiMelCloudHomeState state) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to format MELCloud Home state", e);
        }
    }

    private record LocatedUnit(
            MitsubishiMelCloudHomeContextResponse.Building building,
            MitsubishiMelCloudHomeContextResponse.Unit unit,
            MitsubishiMelCloudHomeState.UnitType unitType,
            boolean guestAccess
    ) {
    }
}

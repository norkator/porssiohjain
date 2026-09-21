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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MitsubishiMelCloudHomeState {

    public enum UnitType {
        AIR_TO_AIR,
        AIR_TO_WATER
    }

    private String unitId;
    private String name;
    private String buildingId;
    private String buildingName;
    private UnitType unitType;
    private Boolean connected;
    private Boolean inError;
    private Integer rssi;

    private Boolean power;
    private String operationMode;
    private Double setTemperature;
    private Double roomTemperature;
    private String setFanSpeed;
    private String actualFanSpeed;
    private String vaneVerticalDirection;
    private String vaneHorizontalDirection;
    private Boolean inStandbyMode;
    private Integer numberOfFanSpeeds;
    private Double minTemperature;
    private Double maxTemperature;

    private String operationModeZone1;
    private String operationModeZone2;
    private Double setTemperatureZone1;
    private Double setTemperatureZone2;
    private Double roomTemperatureZone1;
    private Double roomTemperatureZone2;
    private Double setTankWaterTemperature;
    private Double tankWaterTemperature;
    private Boolean forcedHotWaterMode;
    private Boolean hasZone2;
    private Double setHeatFlowTemperatureZone1;
    private Double setCoolFlowTemperatureZone1;
    private Double setHeatFlowTemperatureZone2;
    private Double setCoolFlowTemperatureZone2;
}

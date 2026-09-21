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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MitsubishiMelCloudHomeContextResponse(
        String id,
        String email,
        List<Building> buildings,
        List<Building> guestBuildings
) {

    public MitsubishiMelCloudHomeContextResponse {
        buildings = buildings != null ? List.copyOf(buildings) : List.of();
        guestBuildings = guestBuildings != null ? List.copyOf(guestBuildings) : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Building(
            String id,
            String name,
            String timezone,
            @JsonProperty("airToAirUnits") List<Unit> airToAirUnits,
            @JsonProperty("airToWaterUnits") List<Unit> airToWaterUnits
    ) {
        public Building {
            airToAirUnits = airToAirUnits != null ? List.copyOf(airToAirUnits) : List.of();
            airToWaterUnits = airToWaterUnits != null ? List.copyOf(airToWaterUnits) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Unit(
            String id,
            String givenDisplayName,
            Boolean isConnected,
            Boolean isInError,
            Integer rssi,
            List<Setting> settings,
            Capabilities capabilities
    ) {
        public Unit {
            settings = settings != null ? List.copyOf(settings) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Setting(String name, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Capabilities(
            Boolean hasEnergyConsumedMeter,
            Boolean hasHotWater,
            Boolean hasZone2,
            Integer numberOfFanSpeeds,
            Double minTempHeat,
            Double maxTempHeat,
            Double minTempCool,
            Double maxTempCool,
            Double minTempAutomatic,
            Double maxTempAutomatic,
            Double minSetTemperatureZone1,
            Double maxSetTemperatureZone1,
            Double minSetTemperatureZone2,
            Double maxSetTemperatureZone2,
            Double minSetTankTemperature,
            Double maxSetTankTemperature
    ) {
    }
}

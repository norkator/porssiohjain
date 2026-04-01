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

package com.nitramite.porssiohjain.services.mitsubishi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MitsubishiAcDevicesResponse {

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Building {
        @JsonProperty("ID")
        private Integer id;

        @JsonProperty("Name")
        private String name;

        @JsonProperty("Structure")
        private Structure structure;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Structure {
        @JsonProperty("Devices")
        private List<Device> devices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Device {
        @JsonProperty("DeviceID")
        private Long deviceId;

        @JsonProperty("DeviceName")
        private String deviceName;

        @JsonProperty("BuildingID")
        private Integer buildingId;
    }

}

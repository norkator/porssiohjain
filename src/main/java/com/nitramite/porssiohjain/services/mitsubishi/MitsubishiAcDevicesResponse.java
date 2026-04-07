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

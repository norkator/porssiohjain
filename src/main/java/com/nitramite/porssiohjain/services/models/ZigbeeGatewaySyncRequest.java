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

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class ZigbeeGatewaySyncRequest {
    private UUID gatewayId;
    private List<DeviceReport> devices;

    @Data
    public static class DeviceReport {
        private String zigbeeIeee;
        private String customName;
        private String profile;
        private long lastAppliedVersion;
        private BigDecimal temperature;
        private BigDecimal setpoint;
        private String mode;
        private Boolean success;
        private String error;
    }
}

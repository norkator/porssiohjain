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
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class ZigbeeGatewaySyncResponse {
    int pollAfterSeconds;
    List<DeviceCommand> devices;

    @Value
    @Builder
    public static class DeviceCommand {
        String zigbeeIeee;
        long version;
        BigDecimal targetTemperature;
        String mode;
        Instant expiresAt;
    }
}

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

import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private Long id;
    private UUID uuid;
    private DeviceType deviceType;
    private Boolean enabled;
    private String deviceName;
    private String timezone;
    private Instant lastCommunication;
    private Instant createdAt;
    private Instant updatedAt;
    private Long accountId;
    private Boolean shared;
    private Boolean apiOnline;
    private Boolean mqttOnline;
    private String mqttUsername;
    private String mqttPassword;

    // Heat pump related
    private String hpName;
    private AcType acType;
    private String acUsername;
    private String acPassword;
    private String acDeviceId;
    private String buildingId;
    private String acDeviceUniqueId;
}

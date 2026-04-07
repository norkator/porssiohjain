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
import lombok.Data;

@Data
public class CreateDeviceRequest {
    private String deviceName;
    private String timezone;
    private DeviceType deviceType;
    private Boolean enabled;
    private String hpName;
    private AcType acType;
    private String acUsername;
    private String acPassword;
    private String acDeviceId;
    private String buildingId;
}

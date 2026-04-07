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

package com.nitramite.porssiohjain.services.mappers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.services.models.DeviceResponse;

import java.util.List;

public class DeviceMapper {

    public static DeviceResponse toResponse(
            DeviceEntity entity
    ) {
        return DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceType(entity.getDeviceType())
                .enabled(entity.isEnabled())
                .deviceName(entity.getDeviceName())
                .timezone(entity.getTimezone())
                .lastCommunication(entity.getLastCommunication())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .accountId(entity.getAccount().getId())
                .build();
    }

    public static List<DeviceResponse> toResponseList(List<DeviceEntity> entities) {
        return entities.stream()
                .map(DeviceMapper::toResponse)
                .toList();
    }

}

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

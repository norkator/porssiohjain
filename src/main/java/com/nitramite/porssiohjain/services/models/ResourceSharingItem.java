package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.ResourceType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceSharingItem {
    private Long id;
    private String name;
    private ResourceType resourceType;
    private Long resourceId;
    private boolean shared = false;
}
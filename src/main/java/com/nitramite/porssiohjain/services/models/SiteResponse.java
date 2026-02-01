package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.SiteType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SiteResponse {
    private Long id;
    private String name;
    private SiteType type;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}

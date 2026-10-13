/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import java.time.Instant;

public record FeatureRequestSummary(Long id, String requestedChanges, Instant createdAt, boolean handled, boolean unreadComments) {
    public static FeatureRequestSummary from(FeatureRequestEntity row) {
        return new FeatureRequestSummary(row.getId(), row.getRequestedChanges(), row.getCreatedAt(), row.isHandled(), row.hasUnreadComments());
    }
}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.services.models;

import java.time.Instant;
import java.util.List;

public record FeatureRequestDetail(Long id, String useCase, String requestedChanges, String contactEmail,
                                   Instant createdAt, boolean handled, List<Comment> comments) {
    public record Comment(Long id, boolean adminAuthor, String message, Instant createdAt) {}
    public long lastSeenCommentId() { return comments.isEmpty() ? 0 : comments.getLast().id(); }
}

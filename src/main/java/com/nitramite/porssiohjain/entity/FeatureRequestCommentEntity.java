/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "feature_request_comment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeatureRequestCommentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, updatable = false)
    private Long featureRequestId;
    @Column(name = "account_id", updatable = false)
    private Long accountId;
    @Column(name = "admin_author", nullable = false, updatable = false)
    private boolean adminAuthor;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}

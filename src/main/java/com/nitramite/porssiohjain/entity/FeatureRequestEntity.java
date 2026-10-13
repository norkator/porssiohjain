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
@Table(name = "feature_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "use_case", nullable = false, columnDefinition = "TEXT")
    private String useCase;
    @Column(name = "requested_changes", nullable = false, columnDefinition = "TEXT")
    private String requestedChanges;
    @Column(name = "contact_email", length = 254)
    private String contactEmail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean handled;
    @Column(name = "latest_admin_comment_id", nullable = false)
    private long latestAdminCommentId;
    @Column(name = "user_read_comment_id", nullable = false)
    private long userReadCommentId;

    public boolean hasUnreadComments() { return latestAdminCommentId > userReadCommentId; }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}

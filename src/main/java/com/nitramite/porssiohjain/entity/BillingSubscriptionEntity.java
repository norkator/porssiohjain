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

package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.enums.BillingProvider;
import com.nitramite.porssiohjain.entity.enums.BillingSubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "billing_subscription",
        indexes = {
                @Index(name = "idx_billing_subscription_account", columnList = "account_id"),
                @Index(name = "idx_billing_subscription_purchase_token", columnList = "purchase_token", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private BillingProvider provider;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "purchase_token", nullable = false, unique = true, length = 512)
    private String purchaseToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private AccountTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private BillingSubscriptionStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "auto_renewing", nullable = false)
    private boolean autoRenewing;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "linked_purchase_token", length = 512)
    private String linkedPurchaseToken;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}

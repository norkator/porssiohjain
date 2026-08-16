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

import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.PowerplantComparisonType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "powerplant_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerplantRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_element_id", nullable = false)
    private PowerplantElementEntity sourceElement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_element_id", nullable = false)
    private PowerplantElementEntity targetElement;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", nullable = false, length = 32)
    private PowerplantComparisonType comparisonType;

    @Column(name = "threshold_value", nullable = false, precision = 10, scale = 3)
    private BigDecimal thresholdValue;

    @Column(name = "hysteresis_value", precision = 10, scale = 3)
    private BigDecimal hysteresisValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_action", nullable = false, length = 32)
    private ControlAction targetAction;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "cooldown_seconds", nullable = false)
    private Integer cooldownSeconds = 300;

    @Column(name = "last_condition_matched")
    private Boolean lastConditionMatched;

    @Column(name = "last_command_sent_at")
    private Instant lastCommandSentAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "last_skip_reason", length = 256)
    private String lastSkipReason;

    @Column(name = "control_point_x")
    private Integer controlPointX;

    @Column(name = "control_point_y")
    private Integer controlPointY;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}

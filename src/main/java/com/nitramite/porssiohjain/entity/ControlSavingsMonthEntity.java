/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** Immutable completed-month report, owned by the account rather than its current controls. */
@Entity
@Table(name = "control_savings_month", uniqueConstraints = @UniqueConstraint(
        name = "uk_control_savings_month", columnNames = {"account_id", "month_start", "timezone"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlSavingsMonthEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    /** Stores the existing API response, including the frozen per-control estimates and names. */
    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}

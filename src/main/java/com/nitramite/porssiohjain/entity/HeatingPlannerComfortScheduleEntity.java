/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.services.models.HeatingComfortSchedule;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "heating_planner_comfort_schedule")
@Getter
@Setter
@NoArgsConstructor
public class HeatingPlannerComfortScheduleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;
    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(name = "default_target", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultTarget;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "heating_planner_comfort_period", joinColumns = @JoinColumn(name = "schedule_id"))
    @OrderColumn(name = "sort_order")
    private List<HeatingPlannerComfortPeriod> periods = new ArrayList<>();

    public HeatingComfortSchedule schedule() {
        return new HeatingComfortSchedule(defaultTarget, periods.stream()
                .map(period -> new HeatingComfortSchedule.Period(period.getFrom(), period.getTo(), period.getTarget()))
                .toList());
    }
}

package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "power_limit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "limit_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal limitKw;

    @Column(name = "current_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentKw;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "powerLimit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PowerLimitDeviceEntity> powerLimitDevices;

    @OneToMany(
            mappedBy = "powerLimit",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<PowerLimitHistoryEntity> history;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;

        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
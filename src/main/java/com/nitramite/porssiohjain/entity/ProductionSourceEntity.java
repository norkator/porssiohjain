package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "production_source")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "current_kw", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal currentKw = BigDecimal.ZERO;

    @Column(name = "peak_kw", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal peakKw = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_type", nullable = false, length = 32)
    private ProductionApiType apiType;

    // api credential related below

    @Column(name = "app_id")
    private String appId;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "app_secret")
    private String appSecret;

    @Column(name = "email")
    private String email;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "password")
    private String password;

    @Column(name = "station_id")
    private String stationId;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "productionSource",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<ProductionHistoryEntity> history;

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
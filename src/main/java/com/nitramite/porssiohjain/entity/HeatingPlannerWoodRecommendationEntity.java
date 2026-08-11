package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.HeatingPlannerWoodRecommendationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "heating_planner_wood_recommendation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_heating_planner_wood_recommendation_event",
                columnNames = {"settings_id", "release_starts_at"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatingPlannerWoodRecommendationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private HeatingPlannerSettingsEntity settings;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private HeatingPlannerPlanEntity plan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private HeatingPlannerRoomEntity room;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "plan_version", nullable = false)
    private UUID planVersion;

    @Column(name = "load_name", nullable = false, length = 128)
    private String loadName;

    @Column(name = "wood_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal woodAmount;

    @Column(name = "notify_at", nullable = false)
    private Instant notifyAt;

    @Column(name = "release_starts_at", nullable = false)
    private Instant releaseStartsAt;

    @Column(name = "release_ends_at", nullable = false)
    private Instant releaseEndsAt;

    @Column(name = "initial_room_heating_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal initialRoomHeatingRate;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HeatingPlannerWoodRecommendationStatus status = HeatingPlannerWoodRecommendationStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (status == null) status = HeatingPlannerWoodRecommendationStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

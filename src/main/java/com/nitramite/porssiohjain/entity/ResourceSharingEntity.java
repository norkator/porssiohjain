package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "resource_sharing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceSharingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sharer_account_id", nullable = false)
    private Long sharerAccountId;

    @Column(name = "receiver_account_id", nullable = false)
    private Long receiverAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "control_id")
    private Long controlId;

    @Column(name = "production_source_id")
    private Long productionSourceId;

    @Column(name = "power_limit_id")
    private Long powerLimitId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.enabled = true;
    }

    public Long getResourceId() {
        return switch (this.resourceType) {
            case DEVICE -> this.deviceId;
            case CONTROL -> this.controlId;
            case PRODUCTION_SOURCE -> this.productionSourceId;
            case POWER_LIMIT -> this.powerLimitId;
        };
    }
}
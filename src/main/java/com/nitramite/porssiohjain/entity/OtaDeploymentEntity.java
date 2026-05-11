package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.OtaDeploymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ota_deployment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaDeploymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ota_release_id", nullable = false)
    private OtaReleaseEntity otaRelease;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_device_id")
    private FactoryDeviceEntity factoryDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private DeviceEntity device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_account_id")
    private AccountEntity requestedByAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OtaDeploymentStatus status;

    @Column(name = "command_topic", nullable = false, length = 255)
    private String commandTopic;

    @Column(name = "command_payload", nullable = false, columnDefinition = "TEXT")
    private String commandPayload;

    @Column(name = "result_details", columnDefinition = "TEXT")
    private String resultDetails;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (requestedAt == null) {
            requestedAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}

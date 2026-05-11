package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ota_release")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaReleaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private DevicePlatform platform;

    @Column(name = "product_model", nullable = false, length = 128)
    private String productModel;

    @Column(name = "version", nullable = false, length = 128)
    private String version;

    @Column(name = "binary_url", nullable = false, columnDefinition = "TEXT")
    private String binaryUrl;

    @Column(name = "checksum_sha256", length = 128)
    private String checksumSha256;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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

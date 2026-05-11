package com.nitramite.porssiohjain.entity;

import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import com.nitramite.porssiohjain.utils.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "factory_device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactoryDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 128)
    private String serialNumber;

    @Column(name = "hardware_mac", unique = true, length = 64)
    private String hardwareMac;

    @Column(name = "chip_id", length = 128)
    private String chipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private DevicePlatform platform;

    @Column(name = "product_model", nullable = false, length = 128)
    private String productModel;

    @Column(name = "firmware_version", length = 128)
    private String firmwareVersion;

    @Column(name = "mqtt_topic_root", nullable = false, unique = true, length = 255)
    private String mqttTopicRoot;

    @Column(name = "mqtt_username", nullable = false, unique = true, length = 128)
    private String mqttUsername;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "mqtt_password", nullable = false)
    private String mqttPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "mqtt_device_profile", nullable = false, length = 64)
    @Builder.Default
    private MqttDeviceProfile mqttDeviceProfile = MqttDeviceProfile.GENERIC_RELAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private FactoryDeviceStatus status = FactoryDeviceStatus.REGISTERED;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_bootstrap_payload", columnDefinition = "TEXT")
    private String lastBootstrapPayload;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_device_id")
    private DeviceEntity claimedDevice;

    @Column(name = "claim_code", nullable = false, unique = true, length = 64)
    private String claimCode;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = FactoryDeviceStatus.REGISTERED;
        }
        if (mqttUsername == null || mqttUsername.isBlank()) {
            mqttUsername = "factory-" + serialNumber.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        if (mqttPassword == null || mqttPassword.isBlank()) {
            byte[] randomBytes = new SecureRandom().generateSeed(18);
            mqttPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
        if (mqttDeviceProfile == null) {
            mqttDeviceProfile = MqttDeviceProfile.GENERIC_RELAY;
        }
        if (claimCode == null || claimCode.isBlank()) {
            claimCode = "QR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}

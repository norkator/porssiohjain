package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "power_limit_device",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"power_limit_id", "device_id", "device_channel"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PowerLimitDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "power_limit_id", nullable = false)
    private PowerLimitEntity powerLimit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "device_channel", nullable = false)
    private Integer deviceChannel;
}
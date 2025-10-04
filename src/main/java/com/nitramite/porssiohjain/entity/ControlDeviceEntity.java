package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "control_device",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"control_id", "device_id", "device_channel"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_id", nullable = false)
    private ControlEntity control;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "device_channel", nullable = false)
    private Integer deviceChannel;
}
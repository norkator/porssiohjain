package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "fingrid_data", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fingrid_data", columnNames = {"dataset_id", "start_time", "end_time"})
})
public class FingridDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Integer datasetId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "value", precision = 14, scale = 4, nullable = false)
    private BigDecimal value;

}
package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "production_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_source_id", nullable = false)
    private ProductionSourceEntity productionSource;

    @Column(name = "kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal kw;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

}

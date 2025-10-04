package com.nitramite.porssiohjain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "nordpool")
public class NordpoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_start", nullable = false)
    private Instant deliveryStart;

    @Column(name = "delivery_end", nullable = false)
    private Instant deliveryEnd;

    @Column(name = "price_fi", precision = 10, scale = 4, nullable = false)
    private BigDecimal priceFi;

}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.models;

import com.nitramite.porssiohjain.entity.enums.ContractType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectricityContractResponse {
    private Long id;
    private String name;
    private ContractType type;
    private BigDecimal basicFee;
    private BigDecimal nightPrice;
    private BigDecimal dayPrice;
    private BigDecimal staticPrice;
    private BigDecimal taxPercent;
    private BigDecimal taxAmount;
    private Instant createdAt;
    private Instant updatedAt;
}

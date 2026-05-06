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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ControlPriceService {

    private final NordpoolRepository nordpoolRepository;

    public Optional<BigDecimal> getCurrentCombinedPrice(ControlEntity control, Instant now) {
        return nordpoolRepository.findFirstByDeliveryStartLessThanEqualAndDeliveryEndGreaterThan(now, now)
                .map(currentPrice -> getCombinedPrice(control, currentPrice));
    }

    public BigDecimal getCombinedPrice(ControlEntity control, NordpoolEntity priceEntry) {
        BigDecimal taxPercent = control.getTaxPercent() != null ? control.getTaxPercent() : BigDecimal.ZERO;
        BigDecimal taxMultiplier = BigDecimal.ONE.add(taxPercent.divide(BigDecimal.valueOf(100)));
        BigDecimal nordpoolPrice = priceEntry.getPriceFi()
                .multiply(BigDecimal.valueOf(0.1))
                .multiply(taxMultiplier);
        return nordpoolPrice.add(resolveTransferPrice(
                control.getTransferContract(),
                priceEntry.getDeliveryStart(),
                ZoneId.of(control.getTimezone())
        ));
    }

    private BigDecimal resolveTransferPrice(
            ElectricityContractEntity transferContract,
            Instant deliveryStart,
            ZoneId zone
    ) {
        if (transferContract == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal staticPrice = transferContract.getStaticPrice();
        BigDecimal nightPrice = transferContract.getNightPrice();
        BigDecimal dayPrice = transferContract.getDayPrice();
        BigDecimal taxAmount = transferContract.getTaxAmount() != null ? transferContract.getTaxAmount() : BigDecimal.ZERO;

        if (staticPrice != null && dayPrice == null && nightPrice == null) {
            return staticPrice.add(taxAmount);
        }

        if (dayPrice != null || nightPrice != null) {
            int hour = deliveryStart.atZone(zone).getHour();
            boolean isNight = hour >= 22 || hour < 7;
            BigDecimal basePrice = isNight ? nightPrice : dayPrice;
            return basePrice != null ? basePrice.add(taxAmount) : BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }
}

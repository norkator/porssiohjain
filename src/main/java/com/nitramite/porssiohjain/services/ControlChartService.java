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
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.services.models.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ControlChartService {

    private final ControlService controlService;
    private final ControlRepository controlRepository;
    private final ControlSchedulerService controlSchedulerService;
    private final NordpoolService nordpoolService;

    public ControlChartResponse getControlChart(Long accountId, Long controlId) {
        controlService.getControl(accountId, controlId);

        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found: " + controlId));

        ZoneId controlZone = ZoneId.of(control.getTimezone());
        List<NordpoolPriceResponse> nordpoolPrices = nordpoolService.getNordpoolPricesForControl(controlId, null, null);
        Map<Instant, ControlTableResponse> finalRowsByTimestamp = controlSchedulerService.findByControlId(controlId).stream()
                .filter(entry -> entry.getStatus() == Status.FINAL)
                .collect(Collectors.toMap(ControlTableResponse::getStartTime, Function.identity(), (left, right) -> left));
        Map<Instant, BigDecimal> transferPriceByTimestamp = computeTransferPrices(
                control.getTransferContract(),
                nordpoolPrices,
                controlZone
        );

        List<ControlChartPointResponse> points = nordpoolPrices.stream()
                .map(price -> {
                    ControlTableResponse finalRow = finalRowsByTimestamp.get(price.getDeliveryStart());
                    return ControlChartPointResponse.builder()
                            .timestamp(price.getDeliveryStart())
                            .nordpoolPrice(price.getPriceFiWithTax())
                            .transferPrice(transferPriceByTimestamp.get(price.getDeliveryStart()))
                            .finalControlPrice(finalRow != null ? finalRow.getPriceSnt() : null)
                            .build();
                })
                .toList();

        return ControlChartResponse.builder()
                .timezone(control.getTimezone())
                .transferContractName(control.getTransferContract() != null ? control.getTransferContract().getName() : null)
                .points(points)
                .build();
    }

    private Map<Instant, BigDecimal> computeTransferPrices(
            ElectricityContractEntity transferContract,
            List<NordpoolPriceResponse> prices,
            ZoneId zone
    ) {
        if (transferContract == null) {
            return Map.of();
        }

        BigDecimal staticPrice = transferContract.getStaticPrice();
        BigDecimal nightPrice = transferContract.getNightPrice();
        BigDecimal dayPrice = transferContract.getDayPrice();
        BigDecimal taxAmount = transferContract.getTaxAmount() != null ? transferContract.getTaxAmount() : BigDecimal.ZERO;
        boolean hasStatic = staticPrice != null;
        boolean hasDayNight = dayPrice != null || nightPrice != null;

        return prices.stream()
                .collect(Collectors.toMap(NordpoolPriceResponse::getDeliveryStart, price -> {
                    BigDecimal transferPrice = null;
                    if (hasStatic && !hasDayNight) {
                        transferPrice = staticPrice;
                    } else if (hasDayNight) {
                        int hour = price.getDeliveryStart().atZone(zone).getHour();
                        boolean isNight = hour >= 22 || hour < 7;
                        transferPrice = isNight ? nightPrice : dayPrice;
                    }
                    return transferPrice != null ? transferPrice.add(taxAmount) : null;
                }));
    }
}

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.NordpoolPriceResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceStatsResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class NordpoolService {

    private final NordpoolRepository nordpoolRepository;
    private final ControlRepository controlRepository;

    public List<NordpoolPriceResponse> getNordpoolPricesForControl(Long controlId) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found: " + controlId));

        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofHours(1));
        Instant end = now.plus(Duration.ofHours(24));

        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(start, end);

        BigDecimal taxMultiplier = BigDecimal.ONE.add(control.getTaxPercent().divide(BigDecimal.valueOf(100)));

        return prices.stream()
                .map(n -> NordpoolPriceResponse.builder()
                        .deliveryStart(n.getDeliveryStart())
                        .deliveryEnd(n.getDeliveryEnd())
                        .priceFi(n.getPriceFi().multiply(BigDecimal.valueOf(0.1)))
                        .priceFiWithTax(n.getPriceFi().multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier))
                        .build())
                .toList();
    }

    public TodayPriceStatsResponse getTodayStats(Long accountId, String timezone) {
        ZoneId zone = ZoneId.systemDefault();
        BigDecimal taxMultiplier;

        Optional<ControlEntity> optionalControl = controlRepository.findFirstByAccountId(accountId);
        if (optionalControl.isPresent()) {
            ControlEntity control = optionalControl.get();
            try {
                if (control.getTimezone() != null && !control.getTimezone().isBlank()) {
                    zone = ZoneId.of(control.getTimezone());
                } else if (timezone != null && !timezone.isBlank()) {
                    zone = ZoneId.of(timezone);
                }
            } catch (Exception ignored) {
            }

            if (control.getTaxPercent() != null) {
                taxMultiplier = BigDecimal.ONE.add(control.getTaxPercent().divide(BigDecimal.valueOf(100)));
            } else {
                taxMultiplier = BigDecimal.ONE;
            }
        } else {
            taxMultiplier = BigDecimal.ONE;
            try {
                if (timezone != null && !timezone.isBlank()) {
                    zone = ZoneId.of(timezone);
                }
            } catch (Exception ignored) {
            }
        }

        ZonedDateTime startOfDay = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        List<NordpoolEntity> prices = nordpoolRepository.findPricesBetween(
                startOfDay.toInstant(), endOfDay.toInstant()
        );

        if (prices.isEmpty()) {
            return TodayPriceStatsResponse.builder()
                    .min(BigDecimal.ZERO)
                    .avg(BigDecimal.ZERO)
                    .max(BigDecimal.ZERO)
                    .build();
        }

        List<BigDecimal> values = prices.stream()
                .map(p -> p.getPriceFi().multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier))
                .toList();

        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avg = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), RoundingMode.HALF_UP);

        return TodayPriceStatsResponse.builder()
                .min(min)
                .avg(avg)
                .max(max)
                .build();
    }


}

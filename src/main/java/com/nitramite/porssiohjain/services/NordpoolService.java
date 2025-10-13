package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.NordpoolPriceResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

}

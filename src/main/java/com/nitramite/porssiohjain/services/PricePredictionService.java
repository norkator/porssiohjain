package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.repository.PricePredictionRepository;
import com.nitramite.porssiohjain.services.models.PricePredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PricePredictionService {

    private final PricePredictionRepository predictionRepository;

    @Transactional(readOnly = true)
    public List<PricePredictionResponse> getPredictions(
            Instant startDate,
            Instant endDate
    ) {
        Instant now = Instant.now();
        Instant start = startDate != null ? startDate : now.truncatedTo(ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : start.plus(2, ChronoUnit.DAYS).minusNanos(1);

        return predictionRepository.findBetween(start, end).stream()
                .map(p -> PricePredictionResponse.builder()
                        .timestamp(p.getTimestamp())
                        .priceCents(p.getPriceCents())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PricePredictionResponse> getFuturePredictions() {
        Instant now = Instant.now();

        return predictionRepository.findByTimestampAfterOrderByTimestampAsc(now).stream()
                .map(p -> PricePredictionResponse.builder()
                        .timestamp(p.getTimestamp())
                        .priceCents(p.getPriceCents())
                        .build())
                .toList();
    }

}

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.PricePredictionEntity;
import com.nitramite.porssiohjain.entity.repository.PricePredictionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PricePredictionDataService {

    @SuppressWarnings("FieldCanBeLocal")
    private final String DATA_URL = "https://raw.githubusercontent.com/vividfog/nordpool-predict-fi/refs/heads/main/deploy/prediction.json";
    private final RestTemplate restTemplate = new RestTemplate();

    private final PricePredictionRepository predictionRepository;
    private final SystemLogService systemLogService;

    public PricePredictionDataService(
            PricePredictionRepository predictionRepository,
            SystemLogService systemLogService
    ) {
        this.predictionRepository = predictionRepository;
        this.systemLogService = systemLogService;
    }

    public void fetchData() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Cache-Control", "no-cache");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Double[][]> response = restTemplate.exchange(
                DATA_URL,
                HttpMethod.GET,
                entity,
                Double[][].class
        );

        Double[][] body = response.getBody();
        if (body == null || body.length == 0) {
            return;
        }

        saveEntries(body);
    }

    private void saveEntries(Double[][] raw) {
        Set<Instant> existing = predictionRepository.findAll().stream()
                .map(PricePredictionEntity::getTimestamp)
                .collect(Collectors.toSet());

        List<PricePredictionEntity> toInsert = Arrays.stream(raw)
                .filter(arr -> arr.length >= 2)
                .map(arr -> Instant.ofEpochMilli(arr[0].longValue()))
                .filter(ts -> !existing.contains(ts))
                .map(ts -> {
                    Double price = Arrays.stream(raw)
                            .filter(a -> a[0].longValue() == ts.toEpochMilli())
                            .findFirst()
                            .map(a -> a[1])
                            .orElse(0.0);
                    return PricePredictionEntity.builder()
                            .timestamp(ts)
                            .priceCents(BigDecimal.valueOf(price))
                            .build();
                })
                .toList();

        if (!toInsert.isEmpty()) {
            log.info("Inserting {} price prediction entries", toInsert.size());
            predictionRepository.saveAll(toInsert);
            systemLogService.log("Insert of " + toInsert.size() + " price prediction entries completed.");
        }
    }

    public boolean hasPredictionsForTomorrow(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Instant start = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(2).atStartOfDay(zone).toInstant();
        return predictionRepository.existsByTimestampBetween(start, end);
    }

    public void deleteOldData() {
        int deleteAfterDays = 30;
        Instant cutoff = LocalDate.now()
                .minusDays(deleteAfterDays)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        predictionRepository.deleteByTimestampBefore(cutoff);
    }

}

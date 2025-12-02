package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import com.nitramite.porssiohjain.services.models.WindDataEntry;
import com.nitramite.porssiohjain.services.models.WindForecastResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FingridDataService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fingrid.wind-power-forecast-api-url}")
    private String apiUrl;

    @Value("${fingrid.api-key}")
    private String apiKey;

    @Value("${fingrid.delete-data-after-months:5}")
    private Integer deleteAfterMonths;

    private final SystemLogService systemLogService;
    private final FingridDataRepository fingridDataRepository;

    FingridDataService(
            SystemLogService systemLogService,
            FingridDataRepository fingridDataRepository
    ) {
        this.systemLogService = systemLogService;
        this.fingridDataRepository = fingridDataRepository;
    }

    public WindForecastResponse fetchData(
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Cache-Control", "no-cache");
        headers.set("x-api-key", apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(72, ChronoUnit.HOURS);
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                .withZone(ZoneOffset.UTC);
        String startTimeStr = formatter.format(startTime);
        String endTimeStr = formatter.format(endTime);

        String urlWithParams = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("startTime", startTimeStr)
                .queryParam("endTime", endTimeStr)
                .queryParam("format", "json")
                .queryParam("pageSize", "288")
                .queryParam("locale", "fi")
                .queryParam("sortBy", "startTime")
                .queryParam("sortOrder", "asc")
                .build(true)
                .toUriString();

        ResponseEntity<WindForecastResponse> response = restTemplate.exchange(
                urlWithParams,
                HttpMethod.GET,
                entity,
                WindForecastResponse.class
        );
        assert response.getBody() != null;
        saveEntries(response.getBody().getData());
        return response.getBody();
    }

    private void saveEntries(List<WindDataEntry> entries) {
        List<FingridDataEntity> existing = fingridDataRepository.findAll();
        Set<String> existingKeys = existing.stream()
                .map(e -> e.getStartTime() + "|" + e.getEndTime())
                .collect(Collectors.toSet());
        List<FingridDataEntity> toInsert = entries.stream()
                .filter(e -> !existingKeys.contains(e.getStartTime() + "|" + e.getEndTime()))
                .map(e -> {
                    FingridDataEntity entity = new FingridDataEntity();
                    entity.setDatasetId(e.getDatasetId());
                    entity.setStartTime(e.getStartTime());
                    entity.setEndTime(e.getEndTime());
                    entity.setValue(e.getValue());
                    return entity;
                })
                .toList();
        if (!toInsert.isEmpty()) {
            log.info("Inserting {} Fingrid multiIndex entries", toInsert.size());
            fingridDataRepository.saveAll(toInsert);
            systemLogService.log("Insert of " + toInsert.size() + " Fingrid entries completed.");
        }
    }

    public boolean hasFingridDataForTomorrow() {
        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneId.systemDefault()).plusDays(1).toInstant();
        Instant end = today.plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return fingridDataRepository.existsByDatasetIdAndStartTimeBetween(245, start, end);
    }

    public void deleteOldFingridData() {
        Instant cutoff = LocalDate.now()
                .minusMonths(deleteAfterMonths)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        fingridDataRepository.deleteByStartTimeBefore(cutoff);
    }

}

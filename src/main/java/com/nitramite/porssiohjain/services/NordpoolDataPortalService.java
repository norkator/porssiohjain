package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NordpoolDataPortalService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${nordpool.day-ahead-prices-api-url}")
    private String apiUrl;

    private final NordpoolRepository nordpoolRepository;

    NordpoolDataPortalService(
            NordpoolRepository nordpoolRepository
    ) {
        this.nordpoolRepository = nordpoolRepository;
    }

    public NordpoolResponse fetchData(
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Origin", "https://data.nordpoolgroup.com");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = tomorrow.format(formatter);

        String urlWithParams = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("currency", "EUR")
                .queryParam("market", "DayAhead")
                .queryParam("indexNames", "FI")
                .queryParam("resolutionInMinutes", "15")
                .queryParam("date", formattedDate)
                .build(true)
                .toUriString();

        ResponseEntity<NordpoolResponse> response = restTemplate.exchange(
                urlWithParams,
                HttpMethod.GET,
                entity,
                NordpoolResponse.class
        );

        assert response.getBody() != null;
        saveEntries(response.getBody().getMultiIndexEntries());
        return response.getBody();
    }

    private void saveEntries(List<NordpoolResponse.MultiIndexEntry> entries) {
        List<NordpoolEntity> existing = nordpoolRepository.findAll();
        Set<String> existingKeys = existing.stream()
                .map(e -> e.getDeliveryStart() + "|" + e.getDeliveryEnd())
                .collect(Collectors.toSet());

        List<NordpoolEntity> toInsert = entries.stream()
                .filter(e -> e.getEntryPerArea().containsKey("FI"))
                .filter(e -> !existingKeys.contains(e.getDeliveryStart() + "|" + e.getDeliveryEnd()))
                .map(e -> {
                    NordpoolEntity entity = new NordpoolEntity();
                    entity.setDeliveryStart(e.getDeliveryStart());
                    entity.setDeliveryEnd(e.getDeliveryEnd());
                    entity.setPriceFi(e.getEntryPerArea().get("FI"));
                    return entity;
                })
                .toList();

        if (!toInsert.isEmpty()) {
            log.info("Inserting {} Nordpool multiIndex entries", toInsert.size());
            nordpoolRepository.saveAll(toInsert);
        }
    }

}

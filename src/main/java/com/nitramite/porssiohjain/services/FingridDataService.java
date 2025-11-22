package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class FingridDataService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fingrid.wind-power-forecast-api-url}")
    private String apiUrl;

    @Value("${fingrid.api-key}")
    private String apiKey;

    private final SystemLogService systemLogService;

    FingridDataService(
            SystemLogService systemLogService
    ) {
        this.systemLogService = systemLogService;
    }

    public NordpoolResponse fetchData(
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Cache-Control", "no-cache");
        headers.set("x-api-key", apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // LocalDate today = LocalDate.now();
        // LocalDate tomorrow = today.plusDays(1);
        // // LocalDate y = today.minusDays(1);
        // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        // String formattedDate = tomorrow.format(formatter);

        String urlWithParams = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("startTime", "") // 2025-11-23T00:00:00.000Z
                .queryParam("endTime", "") // 2025-11-24T00:00:00.000Z
                .queryParam("format", "json")
                .queryParam("pageSize", "288")
                .queryParam("locale", "fi")
                .queryParam("sortBy", "startTime")
                .queryParam("sortOrder", "asc")
                .build(true)
                .toUriString();

        // ResponseEntity<NordpoolResponse> response = restTemplate.exchange(
        //         urlWithParams,
        //         HttpMethod.GET,
        //         entity,
        //         NordpoolResponse.class
        // );
//
        // assert response.getBody() != null;
        // saveEntries(response.getBody().getMultiIndexEntries());
        // return response.getBody();

        return null;
    }

    // private void saveEntries(List<NordpoolResponse.MultiIndexEntry> entries) {
    //     List<NordpoolEntity> existing = nordpoolRepository.findAll();
    //     Set<String> existingKeys = existing.stream()
    //             .map(e -> e.getDeliveryStart() + "|" + e.getDeliveryEnd())
    //             .collect(Collectors.toSet());
//
    //     List<NordpoolEntity> toInsert = entries.stream()
    //             .filter(e -> e.getEntryPerArea().containsKey("FI"))
    //             .filter(e -> !existingKeys.contains(e.getDeliveryStart() + "|" + e.getDeliveryEnd()))
    //             .map(e -> {
    //                 NordpoolEntity entity = new NordpoolEntity();
    //                 entity.setDeliveryStart(e.getDeliveryStart());
    //                 entity.setDeliveryEnd(e.getDeliveryEnd());
    //                 entity.setPriceFi(e.getEntryPerArea().get("FI"));
    //                 return entity;
    //             })
    //             .toList();
//
    //     if (!toInsert.isEmpty()) {
    //         log.info("Inserting {} Nordpool multiIndex entries", toInsert.size());
    //         nordpoolRepository.saveAll(toInsert);
    //         systemLogService.log("Insert of " + toInsert.size() + " Nordpool entries completed.");
    //     }
    // }


}

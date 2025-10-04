package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.services.models.NordpoolResponse;
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

@Service
public class NordpoolDataPortalService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${nordpool.day-ahead-prices-api-url}")
    private String apiUrl;

    public NordpoolResponse fetchData(
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Origin", "https://data.nordpoolgroup.com");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = today.format(formatter);

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
        return response.getBody();
    }

}

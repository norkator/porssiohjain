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

package com.nitramite.porssiohjain.services.nordpool;

import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.Day;
import com.nitramite.porssiohjain.services.PushNotificationService;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NordpoolDataPortalService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${nordpool.day-ahead-prices-api-url}")
    private String apiUrl;

    @Value("${nordpool.delete-data-after-months:12}")
    private Integer deleteAfterMonths;

    private final NordpoolRepository nordpoolRepository;
    private final AccountRepository accountRepository;
    private final SystemLogService systemLogService;
    private final PushNotificationService pushNotificationService;

    NordpoolDataPortalService(
            NordpoolRepository nordpoolRepository,
            AccountRepository accountRepository,
            SystemLogService systemLogService,
            PushNotificationService pushNotificationService
    ) {
        this.nordpoolRepository = nordpoolRepository;
        this.accountRepository = accountRepository;
        this.systemLogService = systemLogService;
        this.pushNotificationService = pushNotificationService;
    }

    public NordpoolResponse fetchData(
            Day day
    ) {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = day.equals(Day.TODAY) ? today.format(formatter) : tomorrow.format(formatter);
        String indexNames = getRequiredMarketIndexNames();
        List<String> markets = NordpoolMarket.splitIndexNames(indexNames);

        NordpoolResponse response = fetchDataForMarkets(indexNames, formattedDate, markets);
        saveEntries(response.getMultiIndexEntries(), markets);
        return response;
    }

    private NordpoolResponse fetchDataForMarkets(String indexNames, String formattedDate, List<String> markets) {
        try {
            return fetchDataForIndexNames(indexNames, formattedDate);
        } catch (RestClientException e) {
            if (markets.size() <= 1) {
                throw e;
            }
            log.warn("Nordpool combined market fetch failed for {}. Retrying markets separately.", indexNames, e);
            sendAdminErrorPush("Nordpool combined market fetch failed for " + indexNames, e);
            List<NordpoolResponse> successfulResponses = new ArrayList<>();
            for (String market : markets) {
                try {
                    NordpoolResponse response = fetchDataForIndexNames(market, formattedDate);
                    saveEntries(response.getMultiIndexEntries(), List.of(market));
                    successfulResponses.add(response);
                } catch (RestClientException marketError) {
                    log.error("Nordpool fetch failed for market {}", market, marketError);
                    sendAdminErrorPush("Nordpool market fetch failed for " + market, marketError);
                }
            }
            if (successfulResponses.isEmpty()) {
                throw e;
            }
            return mergeResponses(successfulResponses);
        }
    }

    private NordpoolResponse fetchDataForIndexNames(String indexNames, String formattedDate) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Origin", "https://data.nordpoolgroup.com");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String urlWithParams = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("currency", "EUR")
                .queryParam("market", "DayAhead")
                .queryParam("indexNames", indexNames)
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
        return response.getBody();
    }

    private NordpoolResponse mergeResponses(List<NordpoolResponse> responses) {
        NordpoolResponse first = responses.getFirst();
        NordpoolResponse merged = new NordpoolResponse();
        merged.setDeliveryDateCET(first.getDeliveryDateCET());
        merged.setVersion(responses.stream().mapToInt(NordpoolResponse::getVersion).max().orElse(first.getVersion()));
        merged.setUpdatedAt(responses.stream()
                .map(NordpoolResponse::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(first.getUpdatedAt()));
        merged.setMarket(first.getMarket());
        merged.setIndexNames(responses.stream()
                .flatMap(response -> response.getIndexNames().stream())
                .distinct()
                .sorted()
                .toList());
        merged.setCurrency(first.getCurrency());
        merged.setResolutionInMinutes(first.getResolutionInMinutes());
        merged.setAreaStates(responses.stream()
                .flatMap(response -> response.getAreaStates().stream())
                .toList());
        merged.setMultiIndexEntries(mergeMultiIndexEntries(responses));
        return merged;
    }

    private List<NordpoolResponse.MultiIndexEntry> mergeMultiIndexEntries(List<NordpoolResponse> responses) {
        Map<String, NordpoolResponse.MultiIndexEntry> entriesByPeriod = new LinkedHashMap<>();
        responses.stream()
                .flatMap(response -> response.getMultiIndexEntries().stream())
                .sorted(Comparator.comparing(NordpoolResponse.MultiIndexEntry::getDeliveryStart))
                .forEach(entry -> {
                    String key = entry.getDeliveryStart() + "|" + entry.getDeliveryEnd();
                    NordpoolResponse.MultiIndexEntry mergedEntry = entriesByPeriod.computeIfAbsent(key, ignored -> {
                        NordpoolResponse.MultiIndexEntry created = new NordpoolResponse.MultiIndexEntry();
                        created.setDeliveryStart(entry.getDeliveryStart());
                        created.setDeliveryEnd(entry.getDeliveryEnd());
                        created.setEntryPerArea(new LinkedHashMap<>());
                        return created;
                    });
                    mergedEntry.getEntryPerArea().putAll(entry.getEntryPerArea());
                });
        return List.copyOf(entriesByPeriod.values());
    }

    private void sendAdminErrorPush(String context, Throwable error) {
        try {
            pushNotificationService.sendSystemErrorAdminNotification(context, error);
        } catch (Exception pushError) {
            log.error("Failed to send Nordpool error admin push notification", pushError);
        }
    }

    private String getRequiredMarketIndexNames() {
        Set<String> markets = accountRepository.findDistinctMarketIndexNames().stream()
                .map(NordpoolMarket::normalize)
                .collect(Collectors.toSet());
        markets.add(NordpoolMarket.DEFAULT_MARKET);
        return NordpoolMarket.normalizeAll(markets).stream()
                .collect(Collectors.joining(","));
    }

    private void saveEntries(List<NordpoolResponse.MultiIndexEntry> entries, List<String> markets) {
        List<NordpoolEntity> existing = nordpoolRepository.findAll();
        Set<String> existingKeys = existing.stream()
                .map(e -> e.getMarketIndexName() + "|" + e.getDeliveryStart() + "|" + e.getDeliveryEnd())
                .collect(Collectors.toSet());

        List<NordpoolEntity> toInsert = entries.stream()
                .flatMap(e -> markets.stream()
                        .filter(market -> e.getEntryPerArea().containsKey(market))
                        .filter(market -> !existingKeys.contains(market + "|" + e.getDeliveryStart() + "|" + e.getDeliveryEnd()))
                        .map(market -> {
                            NordpoolEntity entity = new NordpoolEntity();
                            entity.setDeliveryStart(e.getDeliveryStart());
                            entity.setDeliveryEnd(e.getDeliveryEnd());
                            entity.setMarketIndexName(market);
                            entity.setPriceFi(e.getEntryPerArea().get(market));
                            return entity;
                        }))
                .toList();

        if (!toInsert.isEmpty()) {
            log.info("Inserting {} Nordpool multiIndex entries for markets {}", toInsert.size(), markets);
            nordpoolRepository.saveAll(toInsert);
            systemLogService.log("Insert of " + toInsert.size() + " Nordpool entries completed.");
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasDataForToday() {
        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneId.systemDefault()).plusHours(4).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return NordpoolMarket.splitIndexNames(getRequiredMarketIndexNames()).stream()
                .allMatch(market -> nordpoolRepository.existsByMarketIndexNameAndDeliveryStartBetween(market, start, end));
    }

    public void deleteOldNordpoolData() {
        Instant cutoff = LocalDate.now()
                .minusMonths(deleteAfterMonths)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        nordpoolRepository.deleteByDeliveryStartBefore(cutoff);
    }

}

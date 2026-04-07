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

package com.nitramite.porssiohjain.services.fmi;

import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class FmiWeatherService {

    private static final DateTimeFormatter FMI_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fmi.api-enabled:true}")
    private boolean apiEnabled;

    @Value("${fmi.wfs-url:https://opendata.fmi.fi/wfs}")
    private String wfsUrl;

    @Value("${fmi.forecast.stored-query-id:fmi::forecast::harmonie::surface::point::timevaluepair}")
    private String storedQueryId;

    @Value("${fmi.forecast.timestep-minutes:60}")
    private Integer timestepMinutes;

    @Value("${fmi.forecast.hours:72}")
    private Integer forecastHours;

    @Value("${fmi.forecast.parameters:temperature,windspeedms,windgust,humidity,totalcloudcover,precipitationamount}")
    private String forecastParameters;

    public SiteWeatherForecastResponse getForecastForSite(SiteEntity site) {
        if (!apiEnabled) {
            throw new IllegalStateException("FMI weather API is disabled");
        }
        if (site == null) {
            throw new IllegalArgumentException("Site is required");
        }
        if (site.getWeatherPlace() == null || site.getWeatherPlace().isBlank()) {
            throw new IllegalArgumentException("Weather place is not defined for site");
        }

        Instant startTime = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant endTime = startTime.plus(forecastHours, ChronoUnit.HOURS);

        String url = UriComponentsBuilder.fromUriString(wfsUrl)
                .queryParam("service", "WFS")
                .queryParam("version", "2.0.0")
                .queryParam("request", "getFeature")
                .queryParam("storedquery_id", storedQueryId)
                .queryParam("place", site.getWeatherPlace())
                .queryParam("starttime", FMI_TIME_FORMATTER.format(startTime))
                .queryParam("endtime", FMI_TIME_FORMATTER.format(endTime))
                .queryParam("timestep", timestepMinutes)
                .queryParam("parameters", forecastParameters)
                .build(true)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        String xml = response.getBody();
        if (xml == null || xml.isBlank()) {
            throw new IllegalStateException("FMI weather response was empty");
        }

        List<SiteWeatherForecastPointResponse> points = parseForecastPoints(xml);
        Instant forecastStart = points.isEmpty() ? startTime : points.getFirst().getTime();
        Instant forecastEnd = points.isEmpty() ? endTime : points.getLast().getTime();

        return SiteWeatherForecastResponse.builder()
                .siteId(site.getId())
                .siteName(site.getName())
                .weatherPlace(site.getWeatherPlace())
                .fetchedAt(Instant.now())
                .forecastStartTime(forecastStart)
                .forecastEndTime(forecastEnd)
                .timestepMinutes(timestepMinutes)
                .points(points)
                .build();
    }

    private List<SiteWeatherForecastPointResponse> parseForecastPoints(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList timeseriesNodes = document.getElementsByTagNameNS("*", "MeasurementTimeseries");

            Map<Instant, ForecastPointAccumulator> pointsByTime = new LinkedHashMap<>();
            for (int i = 0; i < timeseriesNodes.getLength(); i++) {
                Node node = timeseriesNodes.item(i);
                if (!(node instanceof Element timeseriesElement)) {
                    continue;
                }

                String parameterName = resolveParameterName(timeseriesElement);
                if (parameterName == null || parameterName.isBlank()) {
                    log.debug("Skipping FMI timeseries without parameter name");
                    continue;
                }

                NodeList pointNodes = timeseriesElement.getElementsByTagNameNS("*", "point");
                for (int j = 0; j < pointNodes.getLength(); j++) {
                    Node pointNode = pointNodes.item(j);
                    if (!(pointNode instanceof Element pointElement)) {
                        continue;
                    }

                    Element tvpElement = findFirstChildElementByLocalName(pointElement, "MeasurementTVP");
                    if (tvpElement == null) {
                        continue;
                    }

                    Instant time = parseInstant(getFirstChildText(tvpElement, "time"));
                    BigDecimal value = parseDecimal(getFirstChildText(tvpElement, "value"));
                    if (time == null) {
                        continue;
                    }

                    ForecastPointAccumulator accumulator = pointsByTime.computeIfAbsent(time, ForecastPointAccumulator::new);
                    accumulator.apply(parameterName, value);
                }
            }

            return pointsByTime.values().stream()
                    .sorted(Comparator.comparing(ForecastPointAccumulator::getTime))
                    .map(ForecastPointAccumulator::toResponse)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to parse FMI weather XML response", e);
            throw new IllegalStateException("Failed to parse FMI weather response", e);
        }
    }

    private String resolveParameterName(Element timeseriesElement) {
        String parameterName = extractParameterName(timeseriesElement.getAttribute("gml:id"));
        if (parameterName != null) {
            return parameterName;
        }

        Node current = timeseriesElement;
        while (current != null) {
            if (current instanceof Element element && "member".equals(element.getLocalName())) {
                Element observedPropertyElement = findFirstDescendantByLocalName(element, "observedProperty");
                if (observedPropertyElement != null) {
                    parameterName = extractParameterName(
                            observedPropertyElement.getAttribute("xlink:href"),
                            observedPropertyElement.getAttribute("href"),
                            observedPropertyElement.getTextContent()
                    );
                }
                break;
            }
            current = current.getParentNode();
        }
        return parameterName;
    }

    private String extractParameterName(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }

            String normalized = candidate.trim();
            int slashIndex = normalized.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
                normalized = normalized.substring(slashIndex + 1);
            }
            int hashIndex = normalized.lastIndexOf('#');
            if (hashIndex >= 0 && hashIndex < normalized.length() - 1) {
                normalized = normalized.substring(hashIndex + 1);
            }
            int colonIndex = normalized.lastIndexOf(':');
            if (colonIndex >= 0 && colonIndex < normalized.length() - 1) {
                normalized = normalized.substring(colonIndex + 1);
            }
            int dashIndex = normalized.lastIndexOf('-');
            if (dashIndex >= 0 && dashIndex < normalized.length() - 1) {
                normalized = normalized.substring(dashIndex + 1);
            }

            normalized = normalized.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return null;
    }

    private Element findFirstDescendantByLocalName(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) {
            return null;
        }
        return element;
    }

    private Element findFirstChildElementByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private String getFirstChildText(Element parent, String localName) {
        Element child = findFirstDescendantByLocalName(parent, localName);
        return child != null ? child.getTextContent() : null;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value.trim());
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if ("nan".equalsIgnoreCase(trimmed) || "inf".equalsIgnoreCase(trimmed) || "-inf".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return new BigDecimal(trimmed);
    }

    @Data
    private static class ForecastPointAccumulator {
        private final Instant time;
        private BigDecimal temperature;
        private BigDecimal windSpeedMs;
        private BigDecimal windGust;
        private BigDecimal humidity;
        private BigDecimal totalCloudCover;
        private BigDecimal precipitationAmount;

        private void apply(String parameterName, BigDecimal value) {
            switch (parameterName) {
                case "temperature" -> temperature = value;
                case "windspeedms" -> windSpeedMs = value;
                case "windgust" -> windGust = value;
                case "humidity", "relativehumidity" -> humidity = value;
                case "totalcloudcover" -> totalCloudCover = value;
                case "precipitationamount", "precipitation1h" -> precipitationAmount = value;
                default -> {
                }
            }
        }

        private SiteWeatherForecastPointResponse toResponse() {
            return SiteWeatherForecastPointResponse.builder()
                    .time(time)
                    .temperature(temperature)
                    .windSpeedMs(windSpeedMs)
                    .windGust(windGust)
                    .humidity(humidity)
                    .totalCloudCover(totalCloudCover)
                    .precipitationAmount(precipitationAmount)
                    .build();
        }
    }

}

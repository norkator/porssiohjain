/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 */

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ServiceNoticeEntity;
import com.nitramite.porssiohjain.entity.repository.ServiceNoticeRepository;
import com.nitramite.porssiohjain.services.models.ServiceNoticeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ServiceNoticeService {

    static final int NOTICE_ID = 1;
    public static final int MAX_TEXT_LENGTH = 4000;

    private final ServiceNoticeRepository repository;

    @Transactional(readOnly = true)
    public Configuration getConfiguration() {
        return repository.findById(NOTICE_ID)
                .map(entity -> new Configuration(
                        entity.isActive(),
                        entity.getFinnishText(),
                        entity.getEnglishText(),
                        entity.getUpdatedAt()
                ))
                .orElseGet(() -> new Configuration(false, "", "", null));
    }

    @Transactional(readOnly = true)
    public ServiceNoticeResponse getNotice(Locale locale) {
        Configuration configuration = getConfiguration();
        if (!configuration.active()) {
            return new ServiceNoticeResponse(false, "", configuration.updatedAt());
        }

        String text = "fi".equalsIgnoreCase(locale.getLanguage())
                ? configuration.finnishText()
                : configuration.englishText();
        return new ServiceNoticeResponse(true, text, configuration.updatedAt());
    }

    @Transactional
    public Configuration save(boolean active, String finnishText, String englishText) {
        String normalizedFinnish = normalize(finnishText);
        String normalizedEnglish = normalize(englishText);
        validate(active, normalizedFinnish, normalizedEnglish);

        ServiceNoticeEntity entity = repository.findById(NOTICE_ID)
                .orElseGet(() -> ServiceNoticeEntity.builder()
                        .id(NOTICE_ID)
                        .finnishText("")
                        .englishText("")
                        .build());
        entity.setActive(active);
        entity.setFinnishText(normalizedFinnish);
        entity.setEnglishText(normalizedEnglish);
        ServiceNoticeEntity saved = repository.save(entity);
        return new Configuration(
                saved.isActive(),
                saved.getFinnishText(),
                saved.getEnglishText(),
                saved.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private void validate(boolean active, String finnishText, String englishText) {
        if (finnishText.length() > MAX_TEXT_LENGTH || englishText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Notice text must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        if (active && (finnishText.isBlank() || englishText.isBlank())) {
            throw new IllegalArgumentException("Both Finnish and English text are required when the notice is published");
        }
    }

    public record Configuration(
            boolean active,
            String finnishText,
            String englishText,
            Instant updatedAt
    ) {
    }
}

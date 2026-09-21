/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ServiceNoticeEntity;
import com.nitramite.porssiohjain.entity.repository.ServiceNoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceNoticeServiceTest {

    @Mock
    ServiceNoticeRepository repository;

    ServiceNoticeService service;

    @BeforeEach
    void setUp() {
        service = new ServiceNoticeService(repository);
    }

    @Test
    void returnsPublishedTextInRequestedLanguage() {
        when(repository.findById(1)).thenReturn(Optional.of(ServiceNoticeEntity.builder()
                .id(1)
                .active(true)
                .finnishText("Huolto on käynnissä")
                .englishText("Maintenance is in progress")
                .build()));

        assertEquals("Huolto on käynnissä", service.getNotice(Locale.forLanguageTag("fi-FI")).text());
        assertEquals("Maintenance is in progress", service.getNotice(Locale.ENGLISH).text());
    }

    @Test
    void inactiveNoticeDoesNotExposeDraftText() {
        when(repository.findById(1)).thenReturn(Optional.of(ServiceNoticeEntity.builder()
                .id(1)
                .active(false)
                .finnishText("Luonnos")
                .englishText("Draft")
                .build()));

        var response = service.getNotice(Locale.ENGLISH);

        assertFalse(response.active());
        assertEquals("", response.text());
    }

    @Test
    void publishedNoticeRequiresBothLanguages() {
        assertThrows(IllegalArgumentException.class, () -> service.save(true, "Suomeksi", ""));
    }

    @Test
    void savesTrimmedBilingualNotice() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var configuration = service.save(true, "  Suomeksi  ", "  In English\n");

        ArgumentCaptor<ServiceNoticeEntity> captor = ArgumentCaptor.forClass(ServiceNoticeEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(1, captor.getValue().getId());
        assertTrue(captor.getValue().isActive());
        assertEquals("Suomeksi", configuration.finnishText());
        assertEquals("In English", configuration.englishText());
    }
}

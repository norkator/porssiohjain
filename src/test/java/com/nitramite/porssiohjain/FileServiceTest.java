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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.FileEntity;
import com.nitramite.porssiohjain.entity.repository.FileRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.FileService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FileServiceTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @MockitoBean
    private MqttService mqttService;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
    }

    @Test
    @DisplayName("Read PEM file bytes from database-backed file entity")
    void getRequiredFileBytesShouldReturnStoredPemContent() {
        fileRepository.save(FileEntity.builder()
                .name(FileService.USER_CA_PEM_FILE_NAME)
                .content("""
                        -----BEGIN CERTIFICATE-----
                        test-ca
                        -----END CERTIFICATE-----
                        """)
                .build());

        byte[] content = fileService.getRequiredFileBytes(FileService.USER_CA_PEM_FILE_NAME);

        String pem = new String(content, StandardCharsets.UTF_8);
        assertTrue(pem.contains("BEGIN CERTIFICATE"));
        assertTrue(pem.contains("test-ca"));
    }

    @Test
    @DisplayName("Missing file throws not found")
    void getRequiredFileBytesShouldThrowWhenMissing() {
        assertThrows(
                EntityNotFoundException.class,
                () -> fileService.getRequiredFileBytes(FileService.USER_CA_PEM_FILE_NAME)
        );
    }
}

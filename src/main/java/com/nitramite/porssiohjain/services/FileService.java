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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.FileEntity;
import com.nitramite.porssiohjain.entity.repository.FileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class FileService {

    public static final String USER_CA_PEM_FILE_NAME = "user_ca.pem";

    private final FileRepository fileRepository;

    @Transactional(readOnly = true)
    public boolean fileExists(String name) {
        return fileRepository.existsByName(name);
    }

    @Transactional(readOnly = true)
    public FileEntity getRequiredFile(String name) {
        return fileRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("File not found: " + name));
    }

    @Transactional(readOnly = true)
    public byte[] getRequiredFileBytes(String name) {
        return getRequiredFile(name).getContent().getBytes(StandardCharsets.UTF_8);
    }
}

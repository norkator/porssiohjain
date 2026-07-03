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

package com.nitramite.porssiohjain.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoConverterTest {

    @Test
    void convertsConcurrentlyWithoutCrossThreadCipherCorruption() throws Exception {
        CryptoConverter converter = new CryptoConverter("test-concurrent-crypto-key");
        int threadCount = 24;
        int iterationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<List<String>>> tasks = new ArrayList<>();

        for (int thread = 0; thread < threadCount; thread++) {
            int threadIndex = thread;
            tasks.add(() -> {
                List<String> failures = new ArrayList<>();
                start.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                    String password = "mqtt-pass-" + threadIndex + "-" + i + "-@-field-device";
                    try {
                        String encrypted = converter.convertToDatabaseColumn(password);
                        String decrypted = converter.convertToEntityAttribute(encrypted);
                        if (!password.equals(decrypted)) {
                            failures.add("Expected '%s' but got '%s'".formatted(password, decrypted));
                        }
                    } catch (RuntimeException e) {
                        failures.add(e.getMessage());
                    }
                }
                return failures;
            });
        }

        List<Future<List<String>>> futures = tasks.stream()
                .map(executor::submit)
                .toList();
        start.countDown();

        List<String> failures = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            failures.addAll(future.get());
        }
        executor.shutdownNow();

        assertThat(failures).isEmpty();
    }
}

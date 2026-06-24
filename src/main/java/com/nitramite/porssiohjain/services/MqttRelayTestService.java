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

import com.nitramite.porssiohjain.services.models.DeviceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttRelayTestService {

    public static final List<Integer> SUPPORTED_INTERVAL_SECONDS = List.of(5, 10, 15, 20, 25, 30);
    private static final int MIN_CHANNEL = 0;
    private static final int MAX_CHANNEL = 3;

    private final ControlService controlService;
    private final Map<TestKey, RelayTest> tests = new ConcurrentHashMap<>();

    public List<RelayTest> listTests() {
        return tests.values().stream()
                .sorted(Comparator
                        .comparing(RelayTest::accountId)
                        .thenComparing(RelayTest::deviceId)
                        .thenComparing(RelayTest::channel))
                .toList();
    }

    public RelayTest getTest(Long accountId, Long deviceId) {
        return tests.values().stream()
                .filter(test -> test.accountId().equals(accountId) && test.deviceId().equals(deviceId))
                .findFirst()
                .orElse(null);
    }

    public boolean isRunning(Long accountId, Long deviceId) {
        return getTest(accountId, deviceId) != null;
    }

    public RelayTest startTest(DeviceResponse device, int channel, int intervalSeconds) {
        validate(device, channel, intervalSeconds);
        TestKey key = new TestKey(device.getAccountId(), device.getId(), channel);

        stopTest(device.getAccountId(), device.getId());

        controlService.sendDebugMqttRelayCommand(device.getAccountId(), device.getId(), channel, true);
        Instant now = Instant.now();

        RelayTest test = new RelayTest(
                device.getAccountId(),
                device.getId(),
                device.getUuid().toString(),
                device.getDeviceName(),
                channel,
                intervalSeconds,
                false,
                now.plusSeconds(intervalSeconds),
                now
        );
        tests.put(key, test);
        return test;
    }

    public void stopTest(Long accountId, Long deviceId) {
        List<RelayTest> removed = tests.entrySet().stream()
                .filter(entry -> entry.getKey().accountId().equals(accountId)
                        && entry.getKey().deviceId().equals(deviceId))
                .map(Map.Entry::getValue)
                .toList();
        tests.keySet().removeIf(key -> key.accountId().equals(accountId) && key.deviceId().equals(deviceId));
        for (RelayTest test : removed) {
            try {
                controlService.sendDebugMqttRelayCommand(accountId, deviceId, test.channel(), false);
            } catch (Exception e) {
                log.warn("Failed to switch MQTT relay test device {} channel {} off", deviceId, test.channel(), e);
            }
        }
    }

    public void runDueTests() {
        Instant now = Instant.now();
        for (Map.Entry<TestKey, RelayTest> entry : tests.entrySet()) {
            RelayTest test = entry.getValue();
            if (test.nextRunAt().isAfter(now)) {
                continue;
            }
            try {
                controlService.sendDebugMqttRelayCommand(
                        test.accountId(),
                        test.deviceId(),
                        test.channel(),
                        test.nextStateOn()
                );
                tests.computeIfPresent(entry.getKey(), (key, current) -> current.withNextRun(
                        !current.nextStateOn(),
                        now.plus(Duration.ofSeconds(current.intervalSeconds()))
                ));
            } catch (Exception e) {
                tests.remove(entry.getKey());
                log.warn("Stopped MQTT relay test for device {} channel {} after command failure",
                        test.deviceId(),
                        test.channel(),
                        e);
            }
        }
    }

    private void validate(DeviceResponse device, int channel, int intervalSeconds) {
        if (device == null || device.getId() == null || device.getAccountId() == null || device.getUuid() == null) {
            throw new IllegalArgumentException("Device is required");
        }
        if (channel < MIN_CHANNEL || channel > MAX_CHANNEL) {
            throw new IllegalArgumentException("Unsupported relay channel: " + channel);
        }
        if (!SUPPORTED_INTERVAL_SECONDS.contains(intervalSeconds)) {
            throw new IllegalArgumentException("Unsupported interval seconds: " + intervalSeconds);
        }
    }

    private record TestKey(Long accountId, Long deviceId, int channel) {
    }

    public record RelayTest(
            Long accountId,
            Long deviceId,
            String deviceUuid,
            String deviceName,
            int channel,
            int intervalSeconds,
            boolean nextStateOn,
            Instant nextRunAt,
            Instant startedAt
    ) {
        private RelayTest withNextRun(boolean nextStateOn, Instant nextRunAt) {
            return new RelayTest(
                    accountId,
                    deviceId,
                    deviceUuid,
                    deviceName,
                    channel,
                    intervalSeconds,
                    nextStateOn,
                    nextRunAt,
                    startedAt
            );
        }
    }
}

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

package com.nitramite.porssiohjain.services.toshiba;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.Message;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.DeviceAcCommandLogService;
import com.nitramite.porssiohjain.services.models.AcLoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToshibaAcAmqpSendService {

    private static final long SEND_TIMEOUT_SECONDS = 30L;
    private static final String SAS_PREFIX = "SharedAccessSignature ";

    private final ToshibaRegisterControllerService toshibaRegisterControllerService;
    private final ToshibaLoginService toshibaLoginService;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceAcCommandLogService deviceAcCommandLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendMessage(DeviceAcDataEntity acData, String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be empty");
        }

        sendMessageInternal(acData, payload, true);
    }

    public void sendHexState(DeviceAcDataEntity acData, String hexState) {
        String payload = buildHexStatePayload(acData, hexState);
        Long deviceId = getDeviceId(acData);
        log.info(
                "Prepared Toshiba AMQP hex command. deviceId={}, acDataId={}, sourceId={}, targetId={}, acConsumerId={}, acDeviceId={}, payload={}",
                deviceId,
                acData.getId(),
                buildClientDeviceId(acData),
                acData.getAcDeviceId(),
                acData.getAcConsumerId(),
                acData.getAcDeviceId(),
                payload
        );
        sendMessage(acData, payload);
        try {
            deviceAcCommandLogService.logSentCommand(deviceId, payload);
        } catch (Exception e) {
            log.error("Failed to persist Toshiba AC command log. deviceId={}, acDataId={}", deviceId, acData.getId(), e);
        }
        acData.setLastSentStateHex(hexState);
        deviceAcDataRepository.save(acData);
        markDeviceReachable(acData);
        log.info(
                "Persisted lastSentStateHex after successful Toshiba send. deviceId={}, acDataId={}",
                deviceId,
                acData.getId()
        );
    }

    private void sendMessageInternal(
            DeviceAcDataEntity acData, String payload, boolean retryWithNewSasToken
    ) {
        ensureSasToken(acData);
        SasTokenConnectionInfo connectionInfo = parseConnectionInfo(acData.getSasToken());
        String connectionString = "HostName=" + connectionInfo.hostName()
                + ";DeviceId=" + connectionInfo.deviceId()
                + ";SharedAccessSignature=" + acData.getSasToken();
        Long deviceId = getDeviceId(acData);
        log.info(
                "Opening Toshiba AMQP connection. deviceId={}, acDataId={}, iotHost={}, iotDeviceId={}, sasTokenPresent={}",
                deviceId,
                acData.getId(),
                connectionInfo.hostName(),
                connectionInfo.deviceId(),
                acData.getSasToken() != null && !acData.getSasToken().isBlank()
        );

        DeviceClient deviceClient = null;
        try {
            deviceClient = new DeviceClient(connectionString, IotHubClientProtocol.AMQPS);
            deviceClient.open(true);

            Message message = new Message(payload);
            message.setProperty("type", "mob");
            message.setContentType("application/json");
            message.setContentEncoding("utf-8");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Exception> sendFailure = new AtomicReference<>();

            deviceClient.sendEventAsync(message, (sentMessage, clientException, callbackContext) -> {
                if (clientException != null) {
                    sendFailure.set(clientException);
                    log.error(
                            "Toshiba AMQP callback reported failure. deviceId={}, acDataId={}",
                            deviceId,
                            acData.getId(),
                            clientException
                    );
                } else {
                    log.info(
                            "Toshiba AMQP callback reported success. deviceId={}, acDataId={}",
                            deviceId,
                            acData.getId()
                    );
                }
                latch.countDown();
            }, null);

            boolean completed = latch.await(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("Timed out while sending Toshiba AMQP message");
            }
            if (sendFailure.get() != null) {
                throw new IllegalStateException("Failed to send Toshiba AMQP message", sendFailure.get());
            }
        } catch (Exception e) {
            if (retryWithNewSasToken) {
                log.warn("Toshiba AMQP send failed, attempting SAS token refresh and retry", e);
                refreshSasToken(acData);
                sendMessageInternal(acData, payload, false);
                return;
            }
            throw new IllegalStateException("Unable to send Toshiba AMQP message", e);
        } finally {
            if (deviceClient != null) {
                try {
                    deviceClient.close();
                } catch (Exception e) {
                    log.warn("Failed to close Toshiba AMQP device client cleanly", e);
                }
            }
        }
    }

    private void ensureSasToken(DeviceAcDataEntity acData) {
        if (acData.getSasToken() == null || acData.getSasToken().isBlank()) {
            refreshSasToken(acData);
        }
    }

    private void refreshSasToken(DeviceAcDataEntity acData) {
        String sasToken = toshibaRegisterControllerService.registerClient(acData);
        if (sasToken != null) {
            return;
        }
        AcLoginResponse acLoginResponse = toshibaLoginService.login(acData);
        acData.setAcAccessToken(acLoginResponse.getAccessToken());
        sasToken = toshibaRegisterControllerService.registerClient(acData);
        if (acLoginResponse.isSuccess() && sasToken != null) {
            return;
        }
        throw new IllegalStateException("Failed to acquire Toshiba SAS token");
    }

    private String buildHexStatePayload(DeviceAcDataEntity acData, String hexState) {
        String sourceId = buildClientDeviceId(acData);
        String targetId = acData.getAcDeviceUniqueId();
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("AC device unique id is missing for Toshiba AMQP send");
        }

        ToshibaAmqpCommandPayload payload = new ToshibaAmqpCommandPayload(
                sourceId,
                UUID.randomUUID().toString(),
                List.of(targetId),
                "CMD_FCU_TO_AC",
                new ToshibaAmqpHexPayload(hexState),
                String.valueOf(Instant.now().toEpochMilli())
        );

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Toshiba AMQP payload", e);
        }
    }

    private String buildClientDeviceId(DeviceAcDataEntity acData) {
        if (acData.getAcUsername() == null || acData.getAcUsername().isBlank()) {
            throw new IllegalArgumentException("AC username missing for Toshiba AMQP send");
        }
        if (acData.getAcClientDeviceSuffix() == null || acData.getAcClientDeviceSuffix().isBlank()) {
            throw new IllegalArgumentException("AC client device suffix missing for Toshiba AMQP send");
        }
        return acData.getAcUsername() + "_" + acData.getAcClientDeviceSuffix();
    }

    private SasTokenConnectionInfo parseConnectionInfo(String sasToken) {
        if (sasToken == null || sasToken.isBlank()) {
            throw new IllegalArgumentException("SAS token cannot be empty");
        }

        String normalizedToken = sasToken.startsWith(SAS_PREFIX)
                ? sasToken.substring(SAS_PREFIX.length())
                : sasToken;

        Map<String, String> tokenParts = Arrays.stream(normalizedToken.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));

        String sr = tokenParts.get("sr");
        if (sr == null || sr.isBlank()) {
            throw new IllegalArgumentException("SAS token did not contain sr field");
        }

        String decodedSr = URLDecoder.decode(sr, StandardCharsets.UTF_8);
        String[] segments = decodedSr.split("/");
        if (segments.length < 3 || !"devices".equalsIgnoreCase(segments[1])) {
            throw new IllegalArgumentException("Unexpected Toshiba SAS token resource URI: " + decodedSr);
        }

        return new SasTokenConnectionInfo(segments[0], segments[2]);
    }

    private record SasTokenConnectionInfo(String hostName, String deviceId) {
    }

    private record ToshibaAmqpCommandPayload(
            String sourceId,
            String messageId,
            List<String> targetId,
            String cmd,
            ToshibaAmqpHexPayload payload,
            String timeStamp
    ) {
    }

    private record ToshibaAmqpHexPayload(String data) {
    }

    private void markDeviceReachable(DeviceAcDataEntity acData) {
        Long deviceId = getDeviceId(acData);
        if (deviceId == null) {
            return;
        }
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElse(null);
        if (device == null) {
            return;
        }
        device.setLastCommunication(Instant.now());
        device.setApiOnline(true);
        deviceRepository.save(device);
    }

    private Long getDeviceId(DeviceAcDataEntity acData) {
        DeviceEntity device = acData.getDevice();
        if (device == null) {
            return null;
        }
        return device.getId();
    }
}

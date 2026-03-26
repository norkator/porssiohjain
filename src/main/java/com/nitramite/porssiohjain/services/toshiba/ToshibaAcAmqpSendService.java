/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.services.toshiba;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.Message;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
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

    public void sendMessage(DeviceAcDataEntity acData, String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be empty");
        }

        sendMessageInternal(acData, payload, true);
    }

    public void sendHexState(DeviceAcDataEntity acData, String hexState) {
        sendMessage(acData, hexState);
    }

    private void sendMessageInternal(DeviceAcDataEntity acData, String payload, boolean retryWithNewSasToken) {
        ensureSasToken(acData);
        SasTokenConnectionInfo connectionInfo = parseConnectionInfo(acData.getSasToken());
        String connectionString = "HostName=" + connectionInfo.hostName()
                + ";DeviceId=" + connectionInfo.deviceId()
                + ";SharedAccessSignature=" + acData.getSasToken();

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
        if (toshibaRegisterControllerService.registerClient(acData)) {
            return;
        }
        if (toshibaLoginService.login(acData) && toshibaRegisterControllerService.registerClient(acData)) {
            return;
        }
        throw new IllegalStateException("Failed to acquire Toshiba SAS token");
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
}

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

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.transport.TransportException;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.DeviceAcCommandLogService;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyTransport;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ToshibaAcAmqpSendServiceTest {
    private final DeviceClient direct = mock(DeviceClient.class);
    private final DeviceClient proxy = mock(DeviceClient.class);
    private final ToshibaAmqpClientFactory factory = mock(ToshibaAmqpClientFactory.class);
    private final ToshibaRegisterControllerService registration = mock(ToshibaRegisterControllerService.class);
    private final ToshibaLoginService login = mock(ToshibaLoginService.class);
    private final DeviceAcDataEntity data = DeviceAcDataEntity.builder()
            .sasToken("SharedAccessSignature sr=test.azure-devices.net%2Fdevices%2Ftest&sig=test&se=9999999999")
            .build();
    private ToshibaAcAmqpSendService service;

    @BeforeEach
    void setUp() {
        OutboundProxyProperties properties = new OutboundProxyProperties();
        properties.setEnabled(true);
        properties.setHost("127.0.0.1");
        service = new ToshibaAcAmqpSendService(registration, login, mock(DeviceAcDataRepository.class),
                mock(DeviceRepository.class), mock(DeviceAcCommandLogService.class),
                mock(DeviceOfflineNotificationService.class), new OutboundProxyTransport(properties), factory);
        when(factory.create(anyString(), eq(false))).thenReturn(direct);
        when(factory.create(anyString(), eq(true))).thenReturn(proxy);
        acknowledge(direct, null);
        acknowledge(proxy, null);
    }

    @Test
    void directSuccessDoesNotCreateProxyClient() throws Exception {
        service.sendMessage(data, "test-command");
        verify(direct).open(false);
        verify(direct).sendEventAsync(any(), any(), isNull());
        verify(factory, never()).create(anyString(), eq(true));
    }

    @Test
    void failedDirectOpenUsesOneProxyAndNextOperationStartsDirect() throws Exception {
        doThrow(new IotHubClientException(IotHubStatusCode.IO_ERROR, new ConnectException("refused")))
                .doNothing().when(direct).open(false);
        service.sendMessage(data, "test-command");
        verify(direct, never()).sendEventAsync(any(), any(), isNull());
        verify(direct).close();
        verify(proxy).open(false);
        verify(proxy).sendEventAsync(any(), any(), isNull());
        service.sendMessage(data, "next-command");
        verify(factory, times(2)).create(anyString(), eq(false));
        verify(factory, times(1)).create(anyString(), eq(true));
    }

    @Test
    void azureOpenTimeoutWithoutSocketCauseCanFallback() throws Exception {
        TransportException timeout = new TransportException("AMQP open timed out");
        timeout.setRetryable(true);
        doThrow(new IotHubClientException(IotHubStatusCode.ERROR, timeout)).when(direct).open(false);
        service.sendMessage(data, "test-command");
        verify(proxy).open(false);
    }

    @Test
    void proxyFailureStopsWithoutTokenRefreshOrAnotherAttempt() throws Exception {
        doThrow(new IotHubClientException(IotHubStatusCode.IO_ERROR)).when(direct).open(false);
        doThrow(new IotHubClientException(IotHubStatusCode.UNAUTHORIZED)).when(proxy).open(false);
        assertThatThrownBy(() -> service.sendMessage(data, "test-command")).isInstanceOf(IllegalStateException.class);
        verify(factory, times(1)).create(anyString(), eq(true));
        verifyNoInteractions(login, registration);
        verify(proxy).close();
    }

    @Test
    void failedAcknowledgementNeverReplaysAnAlreadySubmittedCommand() throws Exception {
        acknowledge(direct, new IotHubClientException(IotHubStatusCode.DEVICE_OPERATION_TIMED_OUT));
        assertThatThrownBy(() -> service.sendMessage(data, "test-command")).isInstanceOf(IllegalStateException.class);
        verify(direct, times(1)).sendEventAsync(any(), any(), isNull());
        verify(factory, never()).create(anyString(), eq(true));
        verifyNoInteractions(login, registration);
    }

    @Test
    void certificateFailureDoesNotFallback() throws Exception {
        doThrow(new IotHubClientException(IotHubStatusCode.IO_ERROR, new SSLHandshakeException("untrusted")))
                .when(direct).open(false);
        assertThatThrownBy(() -> service.sendMessage(data, "test-command")).isInstanceOf(IllegalStateException.class);
        verify(factory, never()).create(anyString(), eq(true));
        verifyNoInteractions(login, registration);
    }

    @Test
    void throttlingDoesNotFallback() throws Exception {
        doThrow(new IotHubClientException(IotHubStatusCode.THROTTLED)).when(direct).open(false);
        assertThatThrownBy(() -> service.sendMessage(data, "test-command")).isInstanceOf(IllegalStateException.class);
        verify(factory, never()).create(anyString(), eq(true));
    }

    @Test
    void rejectedSasTokenIsRefreshedOnceBeforeSendingDirectly() throws Exception {
        doThrow(new IotHubClientException(IotHubStatusCode.UNAUTHORIZED)).doNothing().when(direct).open(false);
        when(registration.registerClient(data)).thenReturn(data.getSasToken());
        service.sendMessage(data, "test-command");
        verify(registration, times(1)).registerClient(data);
        verify(direct, times(2)).open(false);
        verify(direct, times(1)).sendEventAsync(any(), any(), isNull());
        verify(factory, never()).create(anyString(), eq(true));
    }

    private void acknowledge(DeviceClient client, IotHubClientException failure) {
        doAnswer(invocation -> {
            MessageSentCallback callback = invocation.getArgument(1);
            callback.onMessageSent(invocation.getArgument(0, Message.class), failure, null);
            return null;
        }).when(client).sendEventAsync(any(), any(), isNull());
    }
}

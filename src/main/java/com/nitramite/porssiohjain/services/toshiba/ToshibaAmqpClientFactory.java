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

import com.microsoft.azure.sdk.iot.device.ClientOptions;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.ProxySettings;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.transport.NoRetry;
import com.microsoft.azure.sdk.iot.device.transport.TransportException;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyTransport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToshibaAmqpClientFactory {
    private final OutboundProxyTransport cloudTransport;

    static boolean isNetworkOpenFailure(Throwable cause) {
        // Azure sometimes wraps an open timeout without retaining a socket exception.
        return (cause instanceof IotHubClientException clientFailure
                && clientFailure.getStatusCode() == IotHubStatusCode.IO_ERROR)
                || (cause.getClass() == TransportException.class && ((TransportException) cause).isRetryable());
    }

    public DeviceClient create(String connectionString, boolean proxy) {
        ClientOptions options = ClientOptions.builder()
                .amqpAuthenticationSessionTimeout((int) Math.max(1, cloudTransport.connectTimeout().toSeconds()))
                .amqpDeviceSessionTimeout((int) Math.max(1, cloudTransport.readTimeout().toSeconds()))
                .proxySettings(proxy ? new ProxySettings(cloudTransport.httpProxy()) : null)
                .build();
        // Azure's HTTP proxy support requires the WebSocket transport on port 443.
        DeviceClient client = new DeviceClient(connectionString,
                proxy ? IotHubClientProtocol.AMQPS_WS : IotHubClientProtocol.AMQPS, options);
        client.setRetryPolicy(new NoRetry());
        client.setOperationTimeout(30_000);
        return client;
    }
}

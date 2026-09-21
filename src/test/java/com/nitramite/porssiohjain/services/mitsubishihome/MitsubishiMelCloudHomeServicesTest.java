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
package com.nitramite.porssiohjain.services.mitsubishihome;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MitsubishiMelCloudHomeServicesTest {

    @Test
    void loginStoresNewTokensOnTransientSelectionData() {
        MitsubishiMelCloudHomeApiClient apiClient = mock(MitsubishiMelCloudHomeApiClient.class);
        DeviceAcDataRepository repository = mock(DeviceAcDataRepository.class);
        MitsubishiMelCloudHomeLoginService service = new MitsubishiMelCloudHomeLoginService(apiClient, repository);
        when(apiClient.authenticate("user@example.com", "secret"))
                .thenReturn(new MitsubishiMelCloudHomeApiClient.TokenResponse("access", "refresh", 3600));
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                .acUsername("user@example.com")
                .acPassword("secret")
                .build();

        assertTrue(service.login(acData).isSuccess());
        assertEquals("access", acData.getAcAccessToken());
        assertEquals("refresh", acData.getAcRefreshToken());
        verify(repository, never()).save(acData);
    }

    @Test
    void validStoredTokenAvoidsAnotherLogin() {
        MitsubishiMelCloudHomeApiClient apiClient = mock(MitsubishiMelCloudHomeApiClient.class);
        MitsubishiMelCloudHomeLoginService service = new MitsubishiMelCloudHomeLoginService(
                apiClient,
                mock(DeviceAcDataRepository.class)
        );
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder()
                .acAccessToken("existing")
                .acTokenExpiresAt(Instant.now().plusSeconds(600))
                .build();

        assertEquals("existing", service.login(acData).getAccessToken());
        verify(apiClient, never()).authenticate(null, null);
    }

    @Test
    void deviceDiscoveryFlattensAtaAtwAndGuestBuildings() {
        MitsubishiMelCloudHomeApiClient apiClient = mock(MitsubishiMelCloudHomeApiClient.class);
        MitsubishiMelCloudHomeDevicesService service = new MitsubishiMelCloudHomeDevicesService(apiClient);
        MitsubishiMelCloudHomeContextResponse.Unit ata = unit("ata-1", "Living room");
        MitsubishiMelCloudHomeContextResponse.Unit atw = unit("atw-1", "Heat pump");
        MitsubishiMelCloudHomeContextResponse.Unit guestAta = unit("ata-2", "Cabin");
        MitsubishiMelCloudHomeContextResponse context = new MitsubishiMelCloudHomeContextResponse(
                "user-1",
                "user@example.com",
                List.of(new MitsubishiMelCloudHomeContextResponse.Building(
                        "building-1", "Home", "Europe/Helsinki", List.of(ata), List.of(atw)
                )),
                List.of(new MitsubishiMelCloudHomeContextResponse.Building(
                        "building-2", "Cabin", "Europe/Helsinki", List.of(guestAta), List.of()
                ))
        );
        when(apiClient.getContext("access")).thenReturn(context);
        DeviceAcDataEntity acData = DeviceAcDataEntity.builder().acAccessToken("access").build();

        List<MitsubishiMelCloudHomeDevicesService.MitsubishiMelCloudHomeDevice> devices = service.getAcDevices(acData);

        assertEquals(3, devices.size());
        assertEquals(MitsubishiMelCloudHomeDevicesService.UnitType.AIR_TO_AIR, devices.get(0).unitType());
        assertEquals(MitsubishiMelCloudHomeDevicesService.UnitType.AIR_TO_WATER, devices.get(1).unitType());
        assertTrue(devices.get(2).guestAccess());
    }

    private static MitsubishiMelCloudHomeContextResponse.Unit unit(String id, String name) {
        return new MitsubishiMelCloudHomeContextResponse.Unit(
                id, name, true, false, -50, List.of(), null
        );
    }
}

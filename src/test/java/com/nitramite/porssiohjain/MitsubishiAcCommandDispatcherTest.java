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

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcCommandDispatcher;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateResponse;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateService;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiSetAcStateResponse;
import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiSetAcStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MitsubishiAcCommandDispatcherTest {

    @Mock
    private MitsubishiAcStateService mitsubishiAcStateService;

    @Mock
    private MitsubishiSetAcStateService mitsubishiSetAcStateService;

    @Mock
    private DeviceAcDataRepository deviceAcDataRepository;

    @Mock
    private SystemLogService systemLogService;

    private MitsubishiAcCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MitsubishiAcCommandDispatcher(
                mitsubishiAcStateService,
                mitsubishiSetAcStateService,
                deviceAcDataRepository,
                systemLogService
        );
    }

    @Test
    void derivesEffectiveFlagsFromChangedFieldsWhenCommandOmitsThem() {
        DeviceAcDataEntity acData = createAcData();
        MitsubishiAcStateResponse currentState = currentState();
        MitsubishiSetAcStateResponse setResponse = new MitsubishiSetAcStateResponse();
        ArgumentCaptor<MitsubishiAcStateResponse> payloadCaptor = ArgumentCaptor.forClass(MitsubishiAcStateResponse.class);

        when(mitsubishiAcStateService.getAcState(acData)).thenReturn(currentState);
        when(mitsubishiSetAcStateService.setAcState(any(), payloadCaptor.capture())).thenReturn(setResponse);

        dispatcher.dispatchHexState(acData, """
                {
                  "DeviceID": 77,
                  "Power": false,
                  "OperationMode": 1,
                  "SetTemperature": 21.0,
                  "SetFanSpeed": 2,
                  "EffectiveFlags": 0
                }
                """);

        MitsubishiAcStateResponse payload = payloadCaptor.getValue();
        assertEquals(1L, payload.getEffectiveFlags());
        assertEquals(Boolean.FALSE, payload.getPower());
        verify(deviceAcDataRepository).save(acData);
    }

    @Test
    void skipsSetAtaWhenNoWritableFieldChangesExist() {
        DeviceAcDataEntity acData = createAcData();

        when(mitsubishiAcStateService.getAcState(acData)).thenReturn(currentState());

        dispatcher.dispatchHexState(acData, """
                {
                  "DeviceID": 77,
                  "Power": true,
                  "OperationMode": 1,
                  "SetTemperature": 21.0,
                  "SetFanSpeed": 2,
                  "EffectiveFlags": 0
                }
                """);

        verify(mitsubishiSetAcStateService, never()).setAcState(any(), any());
        verify(deviceAcDataRepository, never()).save(any());
        verify(systemLogService).log(contains("Mitsubishi SetAta skipped."));
    }

    private DeviceAcDataEntity createAcData() {
        DeviceEntity device = new DeviceEntity();
        device.setId(12L);

        DeviceAcDataEntity acData = new DeviceAcDataEntity();
        acData.setId(34L);
        acData.setDevice(device);
        acData.setAcType(AcType.MITSUBISHI);
        acData.setAcDeviceId("77");
        acData.setBuildingId("99");
        return acData;
    }

    private MitsubishiAcStateResponse currentState() {
        MitsubishiAcStateResponse state = new MitsubishiAcStateResponse();
        state.setDeviceId(77L);
        state.setPower(true);
        state.setOperationMode(1);
        state.setSetTemperature(21.0);
        state.setSetFanSpeed(2);
        return state;
    }
}

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 8000})
    void derivesEffectiveFlagsAndRecordsAcceptedCommand(Integer errorCode) {
        DeviceAcDataEntity acData = createAcData();
        MitsubishiAcStateResponse currentState = currentState();
        MitsubishiSetAcStateResponse setResponse = new MitsubishiSetAcStateResponse();
        setResponse.setErrorCode(errorCode);
        setResponse.setHasPendingCommand(true);
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
        assertNotNull(acData.getLastSentStateHex());
        assertEquals(acData.getLastSentStateHex(), acData.getLastPolledStateHex());
        verify(deviceAcDataRepository).save(acData);
    }

    @ParameterizedTest
    @ValueSource(ints = {6607, 8001})
    void rejectsDeviceErrorsWithoutRecordingCommandAsSent(int errorCode) {
        DeviceAcDataEntity acData = createAcData();
        acData.setLastSentStateHex("previous sent state");
        acData.setLastPolledStateHex("previous polled state");
        MitsubishiSetAcStateResponse setResponse = new MitsubishiSetAcStateResponse();
        setResponse.setErrorCode(errorCode);

        when(mitsubishiAcStateService.getAcState(acData)).thenReturn(currentState());
        when(mitsubishiSetAcStateService.setAcState(any(), any())).thenReturn(setResponse);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatchHexState(acData, """
                        {"DeviceID": 77, "Power": false}
                        """));

        assertEquals("Mitsubishi SetAta failed with error code " + errorCode, exception.getMessage());
        assertEquals("previous sent state", acData.getLastSentStateHex());
        assertEquals("previous polled state", acData.getLastPolledStateHex());
        verify(deviceAcDataRepository, never()).save(any());
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
        acData.setAcType(AcType.MITSUBISHI_MELCLOUD);
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

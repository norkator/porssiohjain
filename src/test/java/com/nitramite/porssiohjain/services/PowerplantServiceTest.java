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

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.PowerplantElementEntity;
import com.nitramite.porssiohjain.entity.PowerplantRuleEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.PowerplantComparisonType;
import com.nitramite.porssiohjain.entity.enums.PowerplantElementType;
import com.nitramite.porssiohjain.entity.enums.ZigbeeMeasurementType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantElementRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantRuleRepository;
import com.nitramite.porssiohjain.entity.repository.PowerplantSettingsRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PowerplantServiceTest {

    private AccountRepository accountRepository;
    private DeviceRepository deviceRepository;
    private PowerplantElementRepository elementRepository;
    private PowerplantRuleRepository ruleRepository;
    private ZigbeeDeviceMeasurementRepository measurementRepository;
    private DemoAccountGuard demoAccountGuard;
    private ControlService controlService;
    private PowerplantService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        elementRepository = mock(PowerplantElementRepository.class);
        ruleRepository = mock(PowerplantRuleRepository.class);
        measurementRepository = mock(ZigbeeDeviceMeasurementRepository.class);
        demoAccountGuard = mock(DemoAccountGuard.class);
        controlService = mock(ControlService.class);
        service = new PowerplantService(
                accountRepository,
                deviceRepository,
                elementRepository,
                ruleRepository,
                mock(PowerplantSettingsRepository.class),
                measurementRepository,
                demoAccountGuard,
                controlService
        );
    }

    @Test
    void failedCommandDoesNotConsumeMatchedTransition() {
        PowerplantRuleEntity rule = humidityRule();
        ZigbeeDeviceMeasurementEntity measurement = ZigbeeDeviceMeasurementEntity.builder()
                .value(new BigDecimal("70"))
                .measuredAt(Instant.now())
                .build();
        when(ruleRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(rule));
        when(measurementRepository.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                10L, ZigbeeMeasurementType.HUMIDITY, "humidity"
        )).thenReturn(Optional.of(measurement));
        doThrow(new IllegalArgumentException("Device is not connected with MQTT"))
                .doNothing()
                .when(controlService).sendDebugMqttRelayCommand(1L, 20L, 1, true);

        assertEquals(0, service.evaluateEnabledRules());
        assertNull(rule.getLastConditionMatched());
        assertEquals("Device is not connected with MQTT", rule.getLastSkipReason());

        assertEquals(1, service.evaluateEnabledRules());
        assertTrue(rule.getLastConditionMatched());
        assertNull(rule.getLastSkipReason());
        verify(controlService, org.mockito.Mockito.times(2))
                .sendDebugMqttRelayCommand(1L, 20L, 1, true);
    }

    @Test
    void debugRelayCommandDoesNotJoinPowerplantRuleTransaction() throws NoSuchMethodException {
        Transactional transactional = ControlService.class
                .getMethod("sendDebugMqttRelayCommand", Long.class, Long.class, int.class, boolean.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Transactional.TxType.NOT_SUPPORTED, transactional.value());
    }

    @Test
    void changingTargetChannelRearmsAttachedRule() {
        PowerplantRuleEntity rule = humidityRule();
        rule.setLastConditionMatched(true);
        rule.setLastSkipReason("Condition already matched");
        PowerplantElementEntity target = rule.getTargetElement();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(rule.getAccount()));
        when(deviceRepository.findById(20L)).thenReturn(Optional.of(target.getDevice()));
        when(elementRepository.findByIdAndAccountId(200L, 1L)).thenReturn(Optional.of(target));
        when(elementRepository.save(any(PowerplantElementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.findBySourceElement(target)).thenReturn(List.of());
        when(ruleRepository.findByTargetElement(target)).thenReturn(List.of(rule));

        service.saveElement(
                1L, 200L, "Dehumidifier", PowerplantElementType.DEVICE_CONTROL, "power-off",
                null, null, 20L, 2, null, null, 100, 100
        );

        assertNull(rule.getLastConditionMatched());
        assertNull(rule.getLastSkipReason());
    }

    @Test
    void manuallyRearmsEveryAccountRule() {
        AccountEntity account = AccountEntity.builder().id(1L).build();
        PowerplantRuleEntity first = PowerplantRuleEntity.builder()
                .lastConditionMatched(true)
                .lastSkipReason("Condition already matched")
                .build();
        PowerplantRuleEntity second = PowerplantRuleEntity.builder()
                .lastConditionMatched(false)
                .lastSkipReason("Condition does not match")
                .build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(ruleRepository.findByAccountIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));

        assertEquals(2, service.rearmAllRules(1L));

        assertNull(first.getLastConditionMatched());
        assertNull(first.getLastSkipReason());
        assertNull(second.getLastConditionMatched());
        assertNull(second.getLastSkipReason());
        verify(demoAccountGuard).assertWritable(1L);
    }

    private PowerplantRuleEntity humidityRule() {
        AccountEntity account = AccountEntity.builder().id(1L).build();
        DeviceEntity sensor = DeviceEntity.builder()
                .id(10L)
                .account(account)
                .deviceType(DeviceType.TEMPERATURE_SENSOR)
                .build();
        DeviceEntity relay = DeviceEntity.builder()
                .id(20L)
                .account(account)
                .deviceType(DeviceType.STANDARD)
                .build();
        PowerplantElementEntity source = PowerplantElementEntity.builder()
                .id(100L)
                .account(account)
                .elementType(PowerplantElementType.INDICATOR)
                .device(sensor)
                .measurementType(ZigbeeMeasurementType.HUMIDITY)
                .measurementKey("humidity")
                .build();
        PowerplantElementEntity target = PowerplantElementEntity.builder()
                .id(200L)
                .account(account)
                .name("Dehumidifier")
                .elementType(PowerplantElementType.DEVICE_CONTROL)
                .iconName("power-off")
                .device(relay)
                .deviceChannel(1)
                .canvasX(100)
                .canvasY(100)
                .build();
        return PowerplantRuleEntity.builder()
                .id(300L)
                .account(account)
                .sourceElement(source)
                .targetElement(target)
                .comparisonType(PowerplantComparisonType.GREATER_THAN)
                .thresholdValue(new BigDecimal("60"))
                .targetAction(ControlAction.TURN_ON)
                .enabled(true)
                .cooldownSeconds(300)
                .build();
    }
}

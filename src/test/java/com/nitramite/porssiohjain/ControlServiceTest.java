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

import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.ControlThermostatEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.LoadSheddingLinkEntity;
import com.nitramite.porssiohjain.entity.LoadSheddingNodeEntity;
import com.nitramite.porssiohjain.entity.SiteEntity;
import com.nitramite.porssiohjain.entity.SiteWeatherEntity;
import com.nitramite.porssiohjain.entity.WeatherControlDeviceEntity;
import com.nitramite.porssiohjain.entity.WeatherControlEntity;
import com.nitramite.porssiohjain.entity.enums.ComparisonType;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.ControlMode;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.LoadSheddingTriggerState;
import com.nitramite.porssiohjain.entity.enums.Status;
import com.nitramite.porssiohjain.entity.enums.WeatherMetricType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ControlHeatPumpRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlThermostatRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ElectricityContractRepository;
import com.nitramite.porssiohjain.entity.repository.LoadSheddingLinkRepository;
import com.nitramite.porssiohjain.entity.repository.LoadSheddingNodeRepository;
import com.nitramite.porssiohjain.entity.repository.PowerLimitDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ProductionSourceDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ResourceSharingRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.SiteWeatherRepository;
import com.nitramite.porssiohjain.entity.repository.WeatherControlDeviceRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.PushNotificationService;
import com.nitramite.porssiohjain.services.PushNotificationTokenService;
import com.nitramite.porssiohjain.services.ControlPriceService;
import com.nitramite.porssiohjain.services.ThermostatCurveService;
import com.nitramite.porssiohjain.services.models.DeviceThermostatDebugSnapshotResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlServiceTest {

    @Mock
    private ControlRepository controlRepository;

    @Mock
    private ControlDeviceRepository controlDeviceRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ControlTableRepository controlTableRepository;

    @Mock
    private PowerLimitDeviceRepository powerLimitDeviceRepository;

    @Mock
    private ElectricityContractRepository electricityContractRepository;

    @Mock
    private PowerLimitService powerLimitService;

    @Mock
    private ProductionSourceDeviceRepository productionSourceDeviceRepository;

    @Mock
    private WeatherControlDeviceRepository weatherControlDeviceRepository;

    @Mock
    private LoadSheddingNodeRepository loadSheddingNodeRepository;

    @Mock
    private LoadSheddingLinkRepository loadSheddingLinkRepository;

    @Mock
    private SiteWeatherRepository siteWeatherRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private ResourceSharingRepository resourceSharingRepository;

    @Mock
    private ControlHeatPumpRepository controlHeatPumpRepository;

    @Mock
    private ControlThermostatRepository controlThermostatRepository;

    @Mock
    private MqttService mqttService;

    @Mock
    private AccountLimitService accountLimitService;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private PushNotificationTokenService pushNotificationTokenService;

    @Mock
    private ThermostatCurveService thermostatCurveService;

    @Mock
    private ControlPriceService controlPriceService;

    private ControlService controlService;

    @BeforeEach
    void setUp() {
        controlService = new ControlService(
                controlRepository,
                controlDeviceRepository,
                accountRepository,
                deviceRepository,
                controlTableRepository,
                powerLimitDeviceRepository,
                electricityContractRepository,
                powerLimitService,
                productionSourceDeviceRepository,
                weatherControlDeviceRepository,
                loadSheddingNodeRepository,
                loadSheddingLinkRepository,
                siteWeatherRepository,
                siteRepository,
                resourceSharingRepository,
                controlHeatPumpRepository,
                controlThermostatRepository,
                mqttService,
                accountLimitService,
                pushNotificationService,
                pushNotificationTokenService,
                thermostatCurveService,
                controlPriceService
        );
        lenient().when(loadSheddingNodeRepository.findByAccountIdOrderByIdAsc(any())).thenReturn(List.of());
        lenient().when(loadSheddingLinkRepository.findByAccountIdOrderByIdAsc(any())).thenReturn(List.of());
    }

    @Test
    void addDeviceToControlRejectsDeviceFromAnotherAccount() {
        AccountEntity owner = new AccountEntity();
        owner.setId(1L);

        ControlEntity control = new ControlEntity();
        control.setId(10L);
        control.setAccount(owner);

        when(controlRepository.findById(control.getId())).thenReturn(Optional.of(control));
        when(accountRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(deviceRepository.findByIdAndAccount(20L, owner)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> controlService.addDeviceToControl(owner.getId(), control.getId(), 20L, 1, BigDecimal.ONE)
        );
    }

    @Test
    void weatherPriorityRuleOverridesControlRuleForStandardDevice() {
        UUID deviceUuid = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setUuid(deviceUuid);
        device.setEnabled(true);
        device.setDeviceType(DeviceType.STANDARD);
        AccountEntity account = new AccountEntity();
        account.setId(99L);
        device.setAccount(account);

        SiteEntity site = new SiteEntity();
        site.setId(10L);

        WeatherControlEntity weatherControl = new WeatherControlEntity();
        weatherControl.setSite(site);

        WeatherControlDeviceEntity weatherRule = WeatherControlDeviceEntity.builder()
                .id(100L)
                .weatherControl(weatherControl)
                .device(device)
                .deviceChannel(1)
                .weatherMetric(WeatherMetricType.TEMPERATURE)
                .comparisonType(ComparisonType.LESS_THAN)
                .thresholdValue(BigDecimal.valueOf(5))
                .controlAction(ControlAction.TURN_OFF)
                .priorityRule(true)
                .build();

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setMode(ControlMode.MANUAL);
        control.setManualOn(true);
        control.setTimezone("Europe/Helsinki");

        ControlDeviceEntity controlDevice = new ControlDeviceEntity();
        controlDevice.setId(300L);
        controlDevice.setDevice(device);
        controlDevice.setDeviceChannel(1);
        controlDevice.setControl(control);

        SiteWeatherEntity siteWeather = new SiteWeatherEntity();
        siteWeather.setTemperature(BigDecimal.ZERO);

        when(deviceRepository.findByUuid(deviceUuid)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(DeviceEntity.class))).thenReturn(device);
        when(powerLimitDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(device)).thenReturn(List.of(weatherRule));
        when(controlDeviceRepository.findByDevice(device)).thenReturn(List.of(controlDevice));
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeLessThanEqualOrderByForecastTimeDesc(any(SiteEntity.class), any()))
                .thenReturn(Optional.empty());
        when(siteWeatherRepository.findFirstBySiteAndForecastTimeGreaterThanEqualOrderByForecastTimeAsc(any(SiteEntity.class), any()))
                .thenReturn(Optional.of(siteWeather));

        Map<Integer, Integer> controls = controlService.getControlsForDevice(deviceUuid.toString());

        assertEquals(Map.of(1, 0), controls);
        verify(controlDeviceRepository).findByDevice(device);
    }

    @Test
    void mqttLoadSheddingSwitchesRequiredLoadsOffBeforeTurningLoadsOn() {
        UUID sourceDeviceUuid = UUID.randomUUID();
        UUID targetDeviceUuid = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(99L);

        DeviceEntity sourceDevice = new DeviceEntity();
        sourceDevice.setId(1L);
        sourceDevice.setUuid(sourceDeviceUuid);
        sourceDevice.setEnabled(true);
        sourceDevice.setMqttOnline(true);
        sourceDevice.setDeviceType(DeviceType.STANDARD);
        sourceDevice.setAccount(account);

        DeviceEntity targetDevice = new DeviceEntity();
        targetDevice.setId(2L);
        targetDevice.setUuid(targetDeviceUuid);
        targetDevice.setEnabled(true);
        targetDevice.setMqttOnline(true);
        targetDevice.setDeviceType(DeviceType.STANDARD);
        targetDevice.setAccount(account);

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setMode(ControlMode.MANUAL);
        control.setManualOn(true);
        control.setTimezone("Europe/Helsinki");

        ControlDeviceEntity sourceControlDevice = new ControlDeviceEntity();
        sourceControlDevice.setId(300L);
        sourceControlDevice.setDevice(sourceDevice);
        sourceControlDevice.setDeviceChannel(1);
        sourceControlDevice.setControl(control);

        ControlDeviceEntity targetControlDevice = new ControlDeviceEntity();
        targetControlDevice.setId(301L);
        targetControlDevice.setDevice(targetDevice);
        targetControlDevice.setDeviceChannel(2);
        targetControlDevice.setControl(control);

        LoadSheddingNodeEntity sourceNode = LoadSheddingNodeEntity.builder()
                .id(400L)
                .account(account)
                .device(sourceDevice)
                .deviceChannel(1)
                .canvasX(0)
                .canvasY(0)
                .build();

        LoadSheddingNodeEntity targetNode = LoadSheddingNodeEntity.builder()
                .id(401L)
                .account(account)
                .device(targetDevice)
                .deviceChannel(2)
                .canvasX(0)
                .canvasY(0)
                .build();

        LoadSheddingLinkEntity link = LoadSheddingLinkEntity.builder()
                .id(500L)
                .account(account)
                .sourceNode(sourceNode)
                .targetNode(targetNode)
                .triggerState(LoadSheddingTriggerState.TURNED_ON)
                .targetAction(ControlAction.TURN_OFF)
                .build();

        when(deviceRepository.findByMqttOnlineTrue()).thenReturn(List.of(sourceDevice, targetDevice));
        when(deviceRepository.findByUuid(sourceDeviceUuid)).thenReturn(Optional.of(sourceDevice));
        when(deviceRepository.findByUuid(targetDeviceUuid)).thenReturn(Optional.of(targetDevice));
        when(deviceRepository.save(any(DeviceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(powerLimitDeviceRepository.findByDevice(sourceDevice)).thenReturn(List.of());
        when(powerLimitDeviceRepository.findByDevice(targetDevice)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(sourceDevice)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(targetDevice)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(sourceDevice)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(targetDevice)).thenReturn(List.of());
        when(controlDeviceRepository.findByDevice(sourceDevice)).thenReturn(List.of(sourceControlDevice));
        when(controlDeviceRepository.findByDevice(targetDevice)).thenReturn(List.of(targetControlDevice));
        when(loadSheddingNodeRepository.findByAccountIdOrderByIdAsc(account.getId())).thenReturn(List.of(sourceNode, targetNode));
        when(loadSheddingLinkRepository.findByAccountIdOrderByIdAsc(account.getId())).thenReturn(List.of(link));

        controlService.mqttDeviceControls();

        InOrder inOrder = inOrder(mqttService);
        inOrder.verify(mqttService).switchControl(targetDeviceUuid.toString(), 2, false);
        inOrder.verify(mqttService).switchControl(sourceDeviceUuid.toString(), 1, true);
    }

    @Test
    void controlActivationPushMarksContiguousRowsAsSent() {
        UUID deviceUuid = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(99L);
        account.setLocale("en");
        account.setPushNotificationsEnabled(true);
        account.setNotifyControlActivated(true);

        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setUuid(deviceUuid);
        device.setEnabled(true);
        device.setDeviceType(DeviceType.STANDARD);
        device.setAccount(account);

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setAccount(account);
        control.setName("Boiler");
        control.setMode(ControlMode.BELOW_MAX_PRICE);
        control.setTimezone("UTC");

        ControlDeviceEntity controlDevice = new ControlDeviceEntity();
        controlDevice.setId(300L);
        controlDevice.setDevice(device);
        controlDevice.setDeviceChannel(1);
        controlDevice.setControl(control);

        Instant now = Instant.now();
        ControlTableEntity firstRow = ControlTableEntity.builder()
                .id(400L)
                .control(control)
                .startTime(now.minusSeconds(60))
                .endTime(now.plusSeconds(60))
                .priceSnt(BigDecimal.ONE)
                .status(Status.FINAL)
                .build();
        ControlTableEntity secondRow = ControlTableEntity.builder()
                .id(401L)
                .control(control)
                .startTime(firstRow.getEndTime())
                .endTime(firstRow.getEndTime().plusSeconds(60))
                .priceSnt(BigDecimal.ONE)
                .status(Status.FINAL)
                .build();

        when(deviceRepository.findByUuid(deviceUuid)).thenReturn(Optional.of(device));
        when(powerLimitDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(controlDeviceRepository.findByDevice(device)).thenReturn(List.of(controlDevice));
        when(controlTableRepository.findByControlIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                eq(control.getId()),
                eq(Status.FINAL),
                any(Instant.class)
        )).thenReturn(List.of(firstRow));
        when(controlTableRepository.findFirstActiveAtForUpdate(
                eq(control.getId()),
                eq(Status.FINAL),
                any(Instant.class)
        )).thenReturn(Optional.of(firstRow));
        when(controlTableRepository.findByControlIdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                eq(control.getId()),
                eq(Status.FINAL),
                eq(firstRow.getStartTime())
        )).thenReturn(List.of(firstRow, secondRow));
        when(pushNotificationTokenService.hasActivePushToken(account.getId())).thenReturn(true);
        when(accountLimitService.tryConsumeWeeklyPushNotification(eq(account.getId()), any(Instant.class))).thenReturn(true);
        when(pushNotificationService.sendControlActivatedNotification(any(), any(), any(), any())).thenReturn(true);

        Map<Integer, Integer> first = controlService.getControlsForDevice(deviceUuid.toString());
        Map<Integer, Integer> second = controlService.getControlsForDevice(deviceUuid.toString());

        assertEquals(Map.of(1, 1), first);
        assertEquals(Map.of(1, 1), second);
        verify(pushNotificationService).sendControlActivatedNotification(any(), any(), any(), any());
        verify(accountLimitService).tryConsumeWeeklyPushNotification(eq(account.getId()), any(Instant.class));
        assertEquals(firstRow.getActivationPushSentAt(), secondRow.getActivationPushSentAt());
    }

    @Test
    void controlSnapshotDoesNotSendActivationPush() {
        UUID deviceUuid = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(99L);
        account.setLocale("en");
        account.setPushNotificationsEnabled(true);
        account.setNotifyControlActivated(true);

        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setUuid(deviceUuid);
        device.setEnabled(true);
        device.setDeviceType(DeviceType.STANDARD);
        device.setAccount(account);

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setAccount(account);
        control.setName("Boiler");
        control.setMode(ControlMode.BELOW_MAX_PRICE);
        control.setTimezone("UTC");

        ControlDeviceEntity controlDevice = new ControlDeviceEntity();
        controlDevice.setId(300L);
        controlDevice.setDevice(device);
        controlDevice.setDeviceChannel(1);
        controlDevice.setControl(control);

        Instant now = Instant.now();
        ControlTableEntity controlTable = ControlTableEntity.builder()
                .id(400L)
                .control(control)
                .startTime(now.minusSeconds(60))
                .endTime(now.plusSeconds(60))
                .priceSnt(BigDecimal.ONE)
                .status(Status.FINAL)
                .build();

        when(deviceRepository.findByUuid(deviceUuid)).thenReturn(Optional.of(device));
        when(powerLimitDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(controlDeviceRepository.findByDevice(device)).thenReturn(List.of(controlDevice));
        when(controlTableRepository.findByControlIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                eq(control.getId()),
                eq(Status.FINAL),
                any(Instant.class)
        )).thenReturn(List.of(controlTable));

        Map<Integer, Integer> result = controlService.getControlsSnapshotForDevice(deviceUuid.toString());

        assertEquals(Map.of(1, 1), result);
        verify(pushNotificationService, never()).sendControlActivatedNotification(any(), any(), any(), any());
        verify(accountLimitService, never()).tryConsumeWeeklyPushNotification(eq(account.getId()), any(Instant.class));
        verify(controlTableRepository, never()).findFirstActiveAtForUpdate(eq(control.getId()), eq(Status.FINAL), any(Instant.class));
    }

    @Test
    void thermostatDebugSnapshotShowsCurrentPayloadAndThrottleReason() {
        UUID deviceUuid = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(99L);

        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setUuid(deviceUuid);
        device.setDeviceName("Living room thermostat");
        device.setEnabled(true);
        device.setMqttOnline(true);
        device.setDeviceType(DeviceType.THERMOSTAT);
        device.setAccount(account);

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setName("Main control");

        ControlThermostatEntity rule = ControlThermostatEntity.builder()
                .id(300L)
                .control(control)
                .device(device)
                .thermostatChannel(2)
                .curveJson("[]")
                .minTemperature(new BigDecimal("19.00"))
                .maxTemperature(new BigDecimal("23.00"))
                .fallbackTemperature(new BigDecimal("20.00"))
                .enabled(true)
                .lastAppliedTemperature(new BigDecimal("21.40"))
                .lastAppliedAt(Instant.now())
                .build();

        when(deviceRepository.findByUuid(deviceUuid)).thenReturn(Optional.of(device));
        when(controlThermostatRepository.findByDevice(device)).thenReturn(List.of(rule));
        when(controlPriceService.getCurrentCombinedPrice(eq(control), any(Instant.class))).thenReturn(Optional.of(new BigDecimal("5.00")));
        when(thermostatCurveService.evaluate("[]", new BigDecimal("5.00"))).thenReturn(new BigDecimal("21.45"));

        DeviceThermostatDebugSnapshotResponse snapshot =
                controlService.getThermostatDebugSnapshotForDevice(deviceUuid.toString());

        assertEquals("Living room thermostat", snapshot.getDeviceName());
        assertEquals(1, snapshot.getCommands().size());
        assertEquals(false, snapshot.getCommands().getFirst().isWouldSend());
        assertEquals("{\"targetTemperature\":21.45}", snapshot.getCommands().getFirst().getMqttPayload());
        assertEquals("Target temperature was already applied recently", snapshot.getCommands().getFirst().getSkipReason());
    }

    @Test
    void controlActivationPushRespectsAccountSetting() {
        UUID deviceUuid = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(99L);
        account.setLocale("en");
        account.setPushNotificationsEnabled(true);
        account.setNotifyControlActivated(false);

        DeviceEntity device = new DeviceEntity();
        device.setId(1L);
        device.setUuid(deviceUuid);
        device.setEnabled(true);
        device.setDeviceType(DeviceType.STANDARD);
        device.setAccount(account);

        ControlEntity control = new ControlEntity();
        control.setId(200L);
        control.setAccount(account);
        control.setName("Boiler");
        control.setMode(ControlMode.BELOW_MAX_PRICE);
        control.setTimezone("UTC");

        ControlDeviceEntity controlDevice = new ControlDeviceEntity();
        controlDevice.setId(300L);
        controlDevice.setDevice(device);
        controlDevice.setDeviceChannel(1);
        controlDevice.setControl(control);

        Instant now = Instant.now();
        ControlTableEntity controlTable = ControlTableEntity.builder()
                .id(400L)
                .control(control)
                .startTime(now.minusSeconds(60))
                .endTime(now.plusSeconds(60))
                .priceSnt(BigDecimal.ONE)
                .status(Status.FINAL)
                .build();

        when(deviceRepository.findByUuid(deviceUuid)).thenReturn(Optional.of(device));
        when(powerLimitDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(productionSourceDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(weatherControlDeviceRepository.findByDevice(device)).thenReturn(List.of());
        when(controlDeviceRepository.findByDevice(device)).thenReturn(List.of(controlDevice));
        when(controlTableRepository.findByControlIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                eq(control.getId()),
                eq(Status.FINAL),
                any(Instant.class)
        )).thenReturn(List.of(controlTable));

        Map<Integer, Integer> result = controlService.getControlsSnapshotForDevice(deviceUuid.toString());

        assertEquals(Map.of(1, 1), result);
        verify(pushNotificationService, never()).sendControlActivatedNotification(any(), any(), any(), any());
        verify(accountLimitService, never()).tryConsumeWeeklyPushNotification(eq(account.getId()), any(Instant.class));
    }
}

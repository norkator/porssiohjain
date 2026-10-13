package com.nitramite.porssiohjain.services.fingrid;

import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import com.nitramite.porssiohjain.mqtt.MqttReconnectService;
import com.nitramite.porssiohjain.scheduled.Scheduler;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.nordpool.NordpoolDataPortalService;
import com.nitramite.porssiohjain.services.solarman.SolarmanPvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FingridSchedulingTest {

    private FingridDataRepository repository;
    private FingridDataService target;
    private FingridDataService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        repository = mock(FingridDataRepository.class);
        target = new FingridDataService(mock(SystemLogService.class), repository);
        ReflectionTestUtils.setField(target, "apiEnabled", true);
        ReflectionTestUtils.setField(target, "apiUrl", "https://data.fingrid.fi/api/datasets/245/data");
        ReflectionTestUtils.setField(target, "apiKey", "test-key");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(target, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();

        // Reproduce the class proxy introduced by the transactional cleanup method.
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(
                mock(PlatformTransactionManager.class), new AnnotationTransactionAttributeSource()));
        service = (FingridDataService) factory.getProxy();
        assertTrue(AopUtils.isCglibProxy(service));
    }

    @Test
    void startupFetchesMissingForecastWhenProxiedServiceIsEnabled() {
        expectForecast();

        scheduler();

        verify(repository).saveAll(any());
        server.verify();
    }

    @Test
    void dailyFetchRunsWhenProxiedServiceIsEnabled() {
        when(repository.existsByDatasetIdAndStartTimeBetween(eq(245), any(), any())).thenReturn(true);
        Scheduler scheduler = scheduler();
        expectForecast();

        scheduler.fetchFingridDataDaily();

        verify(repository).saveAll(any());
        server.verify();
    }

    @Test
    void startupSkipsFetchingWhenTomorrowForecastExists() {
        when(repository.existsByDatasetIdAndStartTimeBetween(eq(245), any(), any())).thenReturn(true);

        scheduler();

        verify(repository).existsByDatasetIdAndStartTimeBetween(eq(245), any(), any());
        verify(repository, never()).saveAll(any());
        server.verify();
    }

    @Test
    void disabledApiSkipsStartupAndDailyFetching() {
        ReflectionTestUtils.setField(target, "apiEnabled", false);

        Scheduler scheduler = scheduler();
        scheduler.fetchFingridDataDaily();

        verifyNoInteractions(repository);
        server.verify();
    }

    private void expectForecast() {
        when(repository.findAll()).thenReturn(List.of());
        server.expect(request -> assertEquals("/api/datasets/245/data", request.getURI().getPath()))
                .andRespond(withSuccess("""
                        {
                          "data": [{
                            "datasetId": 245,
                            "startTime": "2026-10-14T10:00:00.000Z",
                            "endTime": "2026-10-14T10:15:00.000Z",
                            "value": 1333.7
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private Scheduler scheduler() {
        NordpoolDataPortalService nordpool = mock(NordpoolDataPortalService.class);
        when(nordpool.hasDataForToday()).thenReturn(true);
        PricePredictionDataService predictions = mock(PricePredictionDataService.class);
        when(predictions.hasFuturePredictions(any(ZoneId.class))).thenReturn(true);
        SiteWeatherService weather = mock(SiteWeatherService.class);
        when(weather.hasWeatherDataForTomorrowForConfiguredSites()).thenReturn(true);
        return new Scheduler(
                nordpool,
                mock(ControlSchedulerService.class),
                service,
                mock(PowerLimitService.class),
                mock(SolarmanPvService.class),
                mock(ProductionSourceService.class),
                predictions,
                weather,
                mock(EmailService.class),
                mock(AuthService.class),
                mock(MqttReconnectService.class),
                mock(DeviceService.class),
                mock(ControlService.class),
                mock(HeatPumpControlService.class),
                mock(ThermostatControlService.class),
                mock(ControlNotificationService.class),
                mock(ProductionNotificationService.class),
                mock(PowerLimitNotificationService.class),
                mock(MarketNotificationService.class),
                mock(WindNotificationService.class),
                mock(HeatPumpOnlineCheckService.class),
                mock(MqttRelayTestService.class),
                mock(PowerplantService.class),
                mock(ZigbeeGatewayConnectivityService.class),
                mock(ControlTableRepository.class),
                mock(ZigbeeDeviceMeasurementRepository.class)
        );
    }
}

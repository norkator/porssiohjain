package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.enums.AcType;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyProperties;
import com.nitramite.porssiohjain.services.proxy.OutboundProxyTransport;
import com.nitramite.porssiohjain.services.simulation.DeviceSimulatorClient;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaLoginService;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HeatPumpStatePersistenceTest {
    private static final String HEX = "30431641313200101605FE0B00001002010000";

    @Test
    void pollingWorksAfterRepositorySessionsCloseAndPreservesRealVersusSimulatedRouting() throws Exception {
        var beans = new DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(value -> value.replace("${app.crypto.key}", "heat-pump-state-test-key"));
        var configuration = new Configuration()
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:heat-pump-state-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var entity : scanner.findCandidateComponents("com.nitramite.porssiohjain.entity")) {
            configuration.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
        }
        configuration.getProperties().put("hibernate.resource.beans.container", new SpringBeanContainer(beans));

        try (var factory = configuration.buildSessionFactory()) {
            for (boolean simulated : new boolean[]{false, true}) {
                Long deviceId;
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction();
                    var owner = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString())
                            .demo(simulated).build();
                    session.persist(owner);
                    var device = DeviceEntity.builder().account(owner).deviceName("Heat pump").deviceType(DeviceType.HEAT_PUMP)
                            .timezone("Europe/Helsinki").simulated(simulated).build();
                    session.persist(device);
                    session.persist(DeviceAcDataEntity.builder().device(device).name("Unit").acType(AcType.TOSHIBA)
                            .acDeviceId("pump-id").acAccessToken("test-token").build());
                    tx.commit();
                    deviceId = device.getId();
                }

                // Vaadin repository calls do not share a persistence session with the cloud request.
                DeviceEntity detachedDevice;
                try (var session = factory.openSession()) {
                    detachedDevice = session.find(DeviceEntity.class, deviceId);
                }
                DeviceAcDataEntity data;
                try (var session = factory.openSession()) {
                    data = new JpaRepositoryFactory(session).getRepository(DeviceAcDataRepository.class)
                            .findByDevice(detachedDevice).orElseThrow();
                }

                var simulator = mock(DeviceSimulatorClient.class);
                var login = mock(ToshibaLoginService.class);
                var service = new ToshibaAcStateService(login, new ToshibaAcStateHexDecoderService(),
                        mock(DeviceAcDataRepository.class), mock(DeviceRepository.class), mock(DeviceOfflineNotificationService.class),
                        new OutboundProxyTransport(new OutboundProxyProperties()), simulator);
                var rest = new RestTemplate();
                ReflectionTestUtils.setField(service, "restTemplate", rest);
                var server = MockRestServiceServer.bindTo(rest).build();
                if (simulated) {
                    when(simulator.getState(data)).thenAnswer(invocation -> {
                        assertThat(data.getDevice().getAccount().isDemo()).isTrue();
                        return new DeviceSimulatorClient.State(HEX, Instant.now());
                    });
                } else {
                    server.expect(requestTo("https://mobileapi.toshibahomeaccontrols.com/api/AC/GetCurrentACState?ACId=pump-id"))
                            .andRespond(withSuccess("{\"IsSuccess\":true,\"ResObj\":{\"ACId\":\"pump-id\",\"ACStateData\":\"" + HEX + "\"}}",
                                    MediaType.APPLICATION_JSON));
                }

                var result = service.getAcState(data);

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getResObj().getDecodedAcState().isValid()).isTrue();
                assertThat(data.getLastPolledStateHex()).isEqualTo(HEX);
                if (simulated) verify(simulator).getState(data);
                else verifyNoInteractions(simulator);
                verifyNoInteractions(login);
                server.verify();
            }
        }
    }
}

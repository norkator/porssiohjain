package com.nitramite.porssiohjain.services.tuya;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeviceMeasurementPersistenceTest {
    @Test void migrationAllowsUnknownReportTimeAndCascadesDeviceDeletionWithoutZigbeeTable() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:measurements-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var sql = connection.createStatement();
            sql.execute("create table account (id bigint primary key)");
            sql.execute("create table device (id bigint primary key)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V112__create_device_measurement.sql"));
            sql.execute("insert into account values (1)");
            sql.execute("insert into device values (1)");
            sql.execute("""
                    insert into device_measurement (account_id, device_id, source, source_key, measurement_type,
                    measurement_key, "value", unit, observed_at, received_at)
                    values (1, 1, 'TUYA_SMART', 'va_temperature', 'TEMPERATURE', 'temperature', 20.9, '°C',
                    current_timestamp, current_timestamp)
                    """);
            try (var result = sql.executeQuery("select measured_at from device_measurement")) {
                assertTrue(result.next());
                assertNull(result.getObject(1));
            }
            sql.execute("delete from device where id = 1");
            try (var result = sql.executeQuery("select count(*) from device_measurement")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test void repositoryIsolatesAccountsSelectsLatestAndRetainsRowsAtCleanupBoundary() {
        var beans = new DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(value -> value.replace("${app.crypto.key}", "device-measurement-test-key"));
        var configuration = new Configuration().addAnnotatedClass(AccountEntity.class).addAnnotatedClass(DeviceEntity.class)
                .addAnnotatedClass(DeviceMeasurementEntity.class).addAnnotatedClass(TuyaConnectionEntity.class)
                .addAnnotatedClass(TuyaDeviceEntity.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:measurements-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.getProperties().put("hibernate.resource.beans.container", new SpringBeanContainer(beans));
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var tx = session.beginTransaction();
            var owner = account(session);
            var other = account(session);
            var first = device(session, owner, true);
            var second = device(session, owner, true);
            var foreign = device(session, other, true);
            Instant cutoff = Instant.parse("2026-09-15T09:00:00Z");
            reading(session, owner, first, cutoff.minusSeconds(1), "19");
            reading(session, owner, first, cutoff, "20");
            var latest = reading(session, owner, first, cutoff.plusSeconds(300), "21");
            reading(session, owner, second, cutoff.plusSeconds(300), "22");
            reading(session, other, foreign, cutoff.plusSeconds(300), "99");
            session.flush();
            var repository = new JpaRepositoryFactory(session).getRepository(DeviceMeasurementRepository.class);
            assertEquals(latest.getId(), repository.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                    first.getId(), DeviceMeasurementType.TEMPERATURE, "temperature").orElseThrow().getId());
            assertEquals(2, repository.findLatestDistinctMeasurements(owner.getId(), cutoff).size());
            assertEquals(1, repository.findLatestDistinctMeasurements(other.getId(), cutoff).size());
            assertTrue(repository.findTop500ByAccountIdAndDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                    other.getId(), first.getId(), DeviceMeasurementType.TEMPERATURE, "temperature").isEmpty());
            var history = repository.findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndObservedAtBetweenOrderByObservedAtAscIdAsc(
                    first.getId(), DeviceMeasurementType.TEMPERATURE, "temperature", cutoff, cutoff.plusSeconds(300), PageRequest.of(0, 1));
            assertTrue(history.hasNext());
            assertEquals(cutoff, history.getContent().getFirst().getObservedAt());
            assertEquals(1, repository.deleteOlderThan(cutoff));
            assertEquals(4, repository.count());

            var connected = new TuyaConnectionEntity();
            connected.setAccount(owner);
            connected.setSessionData("test-session");
            session.persist(connected);
            link(session, connected, first, "sensor-1");
            second.setEnabled(false);
            link(session, connected, second, "sensor-2");
            var disconnected = new TuyaConnectionEntity();
            disconnected.setAccount(other);
            session.persist(disconnected);
            link(session, disconnected, foreign, "sensor-3");
            session.flush();
            var tuyaRepository = new JpaRepositoryFactory(session).getRepository(TuyaDeviceRepository.class);
            var targets = tuyaRepository.findPollTargets();
            assertEquals(1, targets.size());
            assertEquals(owner.getId(), targets.getFirst().getAccountId());
            assertEquals(first.getId(), targets.getFirst().getDeviceId());
            tx.rollback();
        }
    }

    private AccountEntity account(Session session) {
        var account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
        session.persist(account);
        return account;
    }

    private DeviceEntity device(Session session, AccountEntity account, boolean enabled) {
        var device = DeviceEntity.builder().account(account).deviceName("Sensor").timezone("Europe/Helsinki").devicePlatform(DevicePlatform.TUYA_SMART)
                .deviceType(DeviceType.TEMPERATURE_SENSOR).enabled(enabled).build();
        session.persist(device);
        return device;
    }

    private DeviceMeasurementEntity reading(Session session, AccountEntity account, DeviceEntity device, Instant at, String value) {
        var reading = DeviceMeasurementEntity.builder().account(account).device(device).source(DevicePlatform.TUYA_SMART)
                .sourceKey("va_temperature").measurementType(DeviceMeasurementType.TEMPERATURE).measurementKey("temperature")
                .value(new BigDecimal(value)).unit("°C").observedAt(at).receivedAt(at).build();
        session.persist(reading);
        return reading;
    }

    private void link(Session session, TuyaConnectionEntity connection, DeviceEntity device, String externalId) {
        var link = new TuyaDeviceEntity();
        link.setConnection(connection);
        link.setDevice(device);
        link.setTuyaDeviceId(externalId);
        session.persist(link);
    }
}

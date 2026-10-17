package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.DeviceMeasurementRepository;
import com.nitramite.porssiohjain.entity.repository.ZigbeeDeviceMeasurementRepository;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementLatestPersistenceTest {
    @Test
    void latestQueriesKeepSeriesKeysTimestampTiesAccountIsolationAndSilentSensors() {
        var beans = new DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(value -> value.replace("${app.crypto.key}", "latest-measurement-test-key"));
        var config = new Configuration().addAnnotatedClass(AccountEntity.class).addAnnotatedClass(DeviceEntity.class)
                .addAnnotatedClass(DeviceMeasurementEntity.class).addAnnotatedClass(ZigbeeDeviceMeasurementEntity.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:latest-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        config.getProperties().put("hibernate.resource.beans.container", new SpringBeanContainer(beans));
        try (var factory = config.buildSessionFactory(); var session = factory.openSession()) {
            var tx = session.beginTransaction();
            var owner = account(session);
            var other = account(session);
            var device = device(session, owner);
            var silentDevice = device(session, owner);
            Instant after = Instant.parse("2026-10-17T10:00:00Z");
            var silent = generic(session, owner, silentDevice, "temperature", after.minusSeconds(1));
            generic(session, owner, device, "temperature", after);
            var differentKey = generic(session, owner, device, "floor", after);
            generic(session, owner, device, "temperature", after.plusSeconds(60));
            var winner = generic(session, owner, device, "temperature", after.plusSeconds(60));
            // A newer foreign-account row for the same device must not suppress the owner's retained value.
            var foreign = generic(session, other, device, "temperature", after.plusSeconds(120));
            var zigbeeSilent = zigbee(session, owner, silentDevice, "1111223344556677", "temperature", after.minusSeconds(1));
            zigbee(session, owner, device, "0011223344556677", "temperature", after);
            var zigbeeKey = zigbee(session, owner, device, "0011223344556677", "floor", after);
            zigbee(session, owner, device, "0011223344556677", "temperature", after.plusSeconds(60));
            // Preserve IEEE identity even when the same sensor is reported by another gateway/device row.
            var zigbeeWinner = zigbee(session, owner, silentDevice, "0011223344556677", "temperature", after.plusSeconds(60));
            var zigbeeForeign = zigbee(session, other, device, "0011223344556677", "temperature", after.plusSeconds(120));
            session.flush();
            session.clear();
            var repositories = new JpaRepositoryFactory(session);
            var generic = repositories.getRepository(DeviceMeasurementRepository.class);
            var zigbee = repositories.getRepository(ZigbeeDeviceMeasurementRepository.class);

            assertThat(generic.findLatestDistinctMeasurements(owner.getId(), after))
                    .extracting(DeviceMeasurementEntity::getId).containsExactly(winner.getId(), differentKey.getId());
            assertThat(generic.findLatestDistinctMeasurements(owner.getId(), Instant.EPOCH))
                    .extracting(DeviceMeasurementEntity::getId).containsExactly(winner.getId(), differentKey.getId(), silent.getId());
            assertThat(generic.findLatestDistinctMeasurements(other.getId(), after))
                    .extracting(DeviceMeasurementEntity::getId).containsExactly(foreign.getId());
            assertThat(zigbee.findLatestDistinctMeasurements(owner.getId(), after))
                    .extracting(ZigbeeDeviceMeasurementEntity::getId).containsExactly(zigbeeWinner.getId(), zigbeeKey.getId());
            assertThat(zigbee.findLatestDistinctMeasurements(owner.getId(), Instant.EPOCH))
                    .extracting(ZigbeeDeviceMeasurementEntity::getId).containsExactly(zigbeeWinner.getId(), zigbeeKey.getId(), zigbeeSilent.getId());
            assertThat(zigbee.findLatestDistinctMeasurements(other.getId(), after))
                    .extracting(ZigbeeDeviceMeasurementEntity::getId).containsExactly(zigbeeForeign.getId());
            assertThat(generic.findLatestDistinctMeasurements(owner.getId(), after.plusSeconds(300))).isEmpty();
            assertThat(zigbee.findLatestDistinctMeasurements(owner.getId(), after.plusSeconds(300))).isEmpty();
            session.clear();
            assertThat(generic.findLatestDistinctMeasurements(owner.getId(), after))
                    .allSatisfy(row -> assertThat(Hibernate.isInitialized(row.getDevice())).isTrue());
            session.clear();
            assertThat(zigbee.findLatestDistinctMeasurements(owner.getId(), after))
                    .allSatisfy(row -> assertThat(Hibernate.isInitialized(row.getDevice())).isTrue());
            tx.rollback();
        }
    }

    @Test
    void newIndexesApplyToExistingMeasurementSchemas() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:latest-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL;NON_KEYWORDS=VALUE")) {
            var sql = connection.createStatement();
            sql.execute("create table account (id bigint primary key)");
            sql.execute("create table device (id bigint primary key)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V82__create_zigbee_measurement_history.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V112__create_device_measurement.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V115__index_latest_measurement_reads.sql"));
            try (var indexes = sql.executeQuery("select count(*) from information_schema.indexes where lower(index_name) in "
                    + "('idx_device_measurement_account_series_latest', 'idx_zigbee_measurement_account_series_latest', 'idx_zigbee_measurement_device_key_time')")) {
                assertThat(indexes.next()).isTrue();
                assertThat(indexes.getInt(1)).isEqualTo(3);
            }
        }
    }

    private AccountEntity account(Session session) {
        var account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
        session.persist(account);
        return account;
    }

    private DeviceEntity device(Session session, AccountEntity owner) {
        var device = DeviceEntity.builder().account(owner).deviceName("Sensor").timezone("Europe/Helsinki").build();
        session.persist(device);
        return device;
    }

    private DeviceMeasurementEntity generic(Session session, AccountEntity owner, DeviceEntity device, String key, Instant at) {
        var row = DeviceMeasurementEntity.builder().account(owner).device(device).source(DevicePlatform.TUYA_SMART)
                .sourceKey(key).measurementType(DeviceMeasurementType.TEMPERATURE).measurementKey(key)
                .value(BigDecimal.TEN).unit("°C").observedAt(at).receivedAt(at).build();
        session.persist(row);
        return row;
    }

    private ZigbeeDeviceMeasurementEntity zigbee(Session session, AccountEntity owner, DeviceEntity device,
                                                String ieee, String key, Instant at) {
        var row = ZigbeeDeviceMeasurementEntity.builder().account(owner).device(device).gatewayId(UUID.randomUUID())
                .zigbeeIeee(ieee).profile("SENSOR").measurementType(ZigbeeMeasurementType.TEMPERATURE)
                .measurementKey(key).value(BigDecimal.TEN).measuredAt(at).receivedAt(at).build();
        session.persist(row);
        return row;
    }
}

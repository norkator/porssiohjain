package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
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
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PublicMeasurementPersistenceTest {
    @Test
    void migrationKeepsExistingAccountsPrivateEnforcesUniqueSelectionsAndCascadesDeletion() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:public-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var sql = connection.createStatement();
            sql.execute("create table account (id bigint primary key)");
            sql.execute("create table device (id bigint primary key)");
            sql.execute("insert into account values (1), (2)");
            sql.execute("insert into device values (1), (2)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V114__create_public_measurements.sql"));
            try (var result = sql.executeQuery("select public_measurements_uuid from account where id = 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
            }
            sql.execute("insert into public_measurement (account_id, device_id, measurement_type, measurement_key) values (1, 1, 'HUMIDITY', 'humidity')");
            assertThatThrownBy(() -> sql.execute("insert into public_measurement (account_id, device_id, measurement_type, measurement_key) values (1, 1, 'HUMIDITY', 'humidity')"))
                    .isInstanceOf(SQLException.class);
            sql.execute("insert into public_measurement (account_id, device_id, measurement_type, measurement_key) values (2, 2, 'HUMIDITY', 'humidity')");
            sql.execute("delete from device where id = 1");
            sql.execute("delete from account where id = 2");
            try (var result = sql.executeQuery("select count(*) from public_measurement")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    @Test
    void repositoriesEnforcePublicOwnershipRevocationAndCompleteHalfOpenHistoryFromBothStores() {
        var beans = new DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(value -> value.replace("${app.crypto.key}", "public-measurement-test-key"));
        var config = new Configuration().addAnnotatedClass(AccountEntity.class).addAnnotatedClass(DeviceEntity.class)
                .addAnnotatedClass(PublicMeasurementEntity.class).addAnnotatedClass(DeviceMeasurementEntity.class)
                .addAnnotatedClass(ZigbeeDeviceMeasurementEntity.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:public-history-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        config.getProperties().put("hibernate.resource.beans.container", new SpringBeanContainer(beans));
        try (var factory = config.buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            var owner = account(session);
            var other = account(session);
            owner.setPublicMeasurementsUuid(UUID.randomUUID());
            var device = DeviceEntity.builder().account(owner).deviceName("Garden").timezone("Europe/Helsinki").build();
            session.persist(device);
            var published = publication(session, owner, device);
            publication(session, other, device); // A foreign row must never make this device public for that account.
            Instant from = Instant.parse("2026-10-24T10:00:00Z");
            Instant to = from.plusSeconds(86400);
            generic(session, owner, device, from.minusSeconds(1));
            generic(session, owner, device, to);
            generic(session, other, device, from);
            for (int i = 0; i < 510; i++) generic(session, owner, device, from.plusSeconds(i * 60L));
            zigbee(session, owner, device, from.minusSeconds(1));
            zigbee(session, owner, device, from);
            zigbee(session, owner, device, to.minusSeconds(1));
            zigbee(session, owner, device, to);
            zigbee(session, other, device, from);
            session.flush();
            var repositories = new JpaRepositoryFactory(session);
            var shares = repositories.getRepository(PublicMeasurementRepository.class);
            var generic = repositories.getRepository(DeviceMeasurementRepository.class);
            var zigbee = repositories.getRepository(ZigbeeDeviceMeasurementRepository.class);
            var accounts = repositories.getRepository(AccountRepository.class);
            var reader = new DeviceMeasurementService(generic, zigbee);
            assertThat(accounts.findByPublicMeasurementsUuidAndBlockedFalse(owner.getPublicMeasurementsUuid())).contains(owner);
            assertThat(shares.findVisibleForAccount(owner.getId())).containsExactly(published);
            assertThat(shares.findVisibleForAccount(other.getId())).isEmpty();
            assertThat(shares.findVisibleById(other.getId(), published.getId())).isEmpty();
            assertThat(reader.existsForAccount(owner.getId(), device.getId(), DeviceMeasurementType.HUMIDITY, "humidity")).isTrue();
            assertThat(reader.existsForAccount(owner.getId(), device.getId(), DeviceMeasurementType.TEMPERATURE, "temperature")).isFalse();
            var history = reader.accountHistory(owner.getId(), device.getId(), DeviceMeasurementType.HUMIDITY, "humidity", from, to);
            assertThat(history).hasSize(512);
            assertThat(history).allSatisfy(row -> {
                assertThat(row.getObservedAt()).isAfterOrEqualTo(from).isBefore(to);
                assertThat(row.getAccount().getId()).isEqualTo(owner.getId());
            });
            assertThat(history.getFirst().getObservedAt()).isEqualTo(from);
            assertThat(history.getLast().getObservedAt()).isEqualTo(to.minusSeconds(1));
            owner.setBlocked(true);
            session.flush();
            assertThat(accounts.findByPublicMeasurementsUuidAndBlockedFalse(owner.getPublicMeasurementsUuid())).isEmpty();
            device.setAccount(other);
            session.flush();
            assertThat(shares.findVisibleForAccount(owner.getId())).isEmpty();
            assertThat(shares.findVisibleById(owner.getId(), published.getId())).isEmpty();
            shares.delete(published);
            session.flush();
            assertThat(shares.findByAccountIdOrderByIdAsc(owner.getId())).isEmpty();
            transaction.rollback();
        }
    }

    private AccountEntity account(Session session) {
        var account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
        session.persist(account);
        return account;
    }

    private PublicMeasurementEntity publication(Session session, AccountEntity account, DeviceEntity device) {
        var item = PublicMeasurementEntity.builder().account(account).device(device)
                .measurementType(DeviceMeasurementType.HUMIDITY).measurementKey("humidity").build();
        session.persist(item);
        return item;
    }

    private void generic(Session session, AccountEntity account, DeviceEntity device, Instant at) {
        session.persist(DeviceMeasurementEntity.builder().account(account).device(device).source(DevicePlatform.TUYA_SMART)
                .sourceKey("va_humidity").measurementType(DeviceMeasurementType.HUMIDITY).measurementKey("humidity")
                .value(BigDecimal.TEN).unit("%").observedAt(at).receivedAt(at).build());
    }

    private void zigbee(Session session, AccountEntity account, DeviceEntity device, Instant at) {
        session.persist(ZigbeeDeviceMeasurementEntity.builder().account(account).device(device).gatewayId(UUID.randomUUID())
                .zigbeeIeee("0011223344556677").profile("SENSOR").measurementType(ZigbeeMeasurementType.HUMIDITY)
                .measurementKey("humidity").value(BigDecimal.TEN).measuredAt(at).receivedAt(at).build());
    }
}

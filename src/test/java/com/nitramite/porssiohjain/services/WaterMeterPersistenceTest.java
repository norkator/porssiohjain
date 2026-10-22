package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.repository.WaterMeterRepository;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaterMeterPersistenceTest {
    private static final String PORTAL = "https://wmd.wrm-systems.fi/water-company";
    @Test
    void bothLoginFieldsAreEncryptedAtRestAndLatestTotalSurvivesANewSession() throws Exception {
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(value -> value.replace("${app.crypto.key}", "water-meter-test-key"));
        var configuration = new Configuration()
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:water-meter-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(jakarta.persistence.Entity.class));
        for (var entity : scanner.findCandidateComponents("com.nitramite.porssiohjain.entity")) {
            configuration.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
        }
        configuration.getProperties().put("hibernate.resource.beans.container",
                new org.springframework.orm.jpa.hibernate.SpringBeanContainer(beans));
        Instant observed = Instant.parse("2026-10-22T09:00:00Z");
        try (var factory = configuration.buildSessionFactory()) {
            Long id;
            try (var session = factory.openSession()) {
                var tx = session.beginTransaction();
                AccountEntity account = AccountEntity.builder().uuid(UUID.randomUUID()).secret("account-secret").build();
                session.persist(account);
                var meter = new WaterMeterEntity();
                meter.setAccount(account);
                meter.setName("Home");
                meter.setPortalUrl(PORTAL);
                meter.setUsername("private-payer");
                meter.setPassword("private-meter");
                meter.setLatestTotalCubicMeters(new BigDecimal("140.8"));
                meter.setObservedAt(observed);
                session.persist(meter);
                session.flush();
                id = meter.getId();
                var repository = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(session)
                        .getRepository(WaterMeterRepository.class);
                assertEquals(1, repository.findByAccountIdOrderByNameAscIdAsc(account.getId()).size());
                assertTrue(repository.findByAccountIdOrderByNameAscIdAsc(-1L).isEmpty());
                assertTrue(repository.lockByIdAndAccountId(id, account.getId()).isPresent());
                assertTrue(repository.lockByIdAndAccountId(id, -1L).isEmpty());
                assertEquals(1, repository.findEnabledForPolling().size());
                assertTrue(repository.findEnabledForNotifications().isEmpty());
                assertTrue(repository.existsByIdAndAccountId(id, account.getId()));
                var history = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(session)
                        .getRepository(com.nitramite.porssiohjain.entity.repository.WaterMeasurementRepository.class);
                var point = new com.nitramite.porssiohjain.entity.WaterMeasurementEntity();
                point.setWaterMeter(meter);
                point.setObservedAt(observed);
                point.setTotalCubicMeters(new BigDecimal("140.8"));
                history.saveAndFlush(point);
                assertEquals(1, history.findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(id, account.getId()).size());
                assertTrue(history.findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(id, -1L).isEmpty());
                Object[] stored = session.createNativeQuery("select username, password from water_meter where id = :id", Object[].class)
                        .setParameter("id", id).getSingleResult();
                assertFalse(stored[0].toString().contains("private"));
                assertFalse(stored[1].toString().contains("private"));
                tx.commit();
            }
            try (var session = factory.openSession()) {
                WaterMeterEntity meter = session.find(WaterMeterEntity.class, id);
                assertEquals("private-payer", meter.getUsername());
                assertEquals("private-meter", meter.getPassword());
                assertEquals(0, new BigDecimal("140.8").compareTo(meter.getLatestTotalCubicMeters()));
                assertEquals(observed, meter.getObservedAt());
            }
        }
    }

    @Test
    void newMigrationPreservesCredentialsSeedsHistoryAndCascadesAccountRemoval() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:water-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var sql = connection.createStatement();
            sql.execute("create table account (id bigint primary key)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V120__create_water_meter.sql"));
            sql.execute("insert into account values (1), (2)");
            sql.execute("insert into water_meter (account_id,name,portal_url,payer_number,water_meter_number,latest_total_cubic_meters,observed_at)"
                    + " values (1,'Home','https://wmd.wrm-systems.fi/water-company','encrypted-payer','encrypted-meter',140.8,current_timestamp)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V121__create_water_measurement.sql"));
            try (var result = sql.executeQuery("select w.username,w.password,w.provider,w.push_enabled,m.total_cubic_meters,"
                    + " m.observed_at = w.observed_at from water_meter w join water_measurement m on m.water_meter_id = w.id")) {
                assertTrue(result.next());
                assertEquals("encrypted-payer", result.getString(1));
                assertEquals("encrypted-meter", result.getString(2));
                assertEquals("WRM_SYSTEMS_WMD", result.getString(3));
                assertFalse(result.getBoolean(4));
                assertEquals(0, new BigDecimal("140.8").compareTo(result.getBigDecimal(5)));
                assertTrue(result.getBoolean(6));
                assertFalse(result.next());
            }
            sql.execute("delete from account where id = 2");
            try (var result = sql.executeQuery("select count(*) from water_meter")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            sql.execute("delete from account where id = 1");
            try (var result = sql.executeQuery("select count(*) from water_meter")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
            try (var result = sql.executeQuery("select count(*) from water_measurement")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }
}

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

package com.nitramite.porssiohjain.services.tuya;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.utils.CryptoConverter;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TuyaPersistenceTest {
    @Test
    void jpaEncryptsConnectionSecretsAndRestoresThemInANewSession() throws Exception {
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.setAutowireCandidateResolver(new org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver());
        beans.addEmbeddedValueResolver(value -> value.replace("${app.crypto.key}", "tuya-test-encryption-key"));
        Configuration configuration = new Configuration()
                .addAnnotatedClass(AccountEntity.class).addAnnotatedClass(DeviceEntity.class)
                .addAnnotatedClass(TuyaConnectionEntity.class).addAnnotatedClass(TuyaDeviceEntity.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:tuya-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.getProperties().put("hibernate.resource.beans.container",
                new org.springframework.orm.jpa.hibernate.SpringBeanContainer(beans));
        try (var factory = configuration.buildSessionFactory()) {
            Long id;
            try (var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                AccountEntity account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
                session.persist(account);
                TuyaConnectionEntity connection = new TuyaConnectionEntity();
                connection.setAccount(account);
                connection.setSessionData("{\"accessToken\":\"private-access\",\"refreshToken\":\"private-refresh\"}");
                session.persist(connection);
                session.flush();
                id = connection.getId();
                String stored = session.createNativeQuery("select session_data from tuya_connection where id = :id", String.class)
                        .setParameter("id", id).getSingleResult();
                assertFalse(stored.contains("private"));
                assertFalse(stored.contains("accessToken"));
                transaction.commit();
            }
            try (var session = factory.openSession()) {
                assertTrue(session.find(TuyaConnectionEntity.class, id).getSessionData().contains("private-refresh"));
            }
        }
    }

    @Test
    void migrationEnforcesUniqueLinksAndCleansUpDeletedDevices() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:tuya-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var sql = connection.createStatement();
            sql.execute("create table account (id bigint primary key)");
            sql.execute("create table device (id bigint primary key)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V111__create_tuya_connection.sql"));
            sql.execute("insert into account values (1)");
            sql.execute("insert into device values (1), (2)");
            sql.execute("insert into tuya_connection (id, account_id) values (1, 1)");
            sql.execute("insert into tuya_device (device_id, connection_id, tuya_device_id) values (1, 1, 'sensor-1')");
            assertThrows(java.sql.SQLException.class, () -> sql.execute("insert into tuya_connection (account_id) values (1)"));
            assertThrows(java.sql.SQLException.class, () -> sql.execute(
                    "insert into tuya_device (device_id, connection_id, tuya_device_id) values (2, 1, 'sensor-1')"));
            sql.execute("delete from device where id = 1");
            try (var result = sql.executeQuery("select count(*) from tuya_device")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }
}

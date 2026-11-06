/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.development;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDevelopmentConfiguration {

    public static final UUID ADMIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "false")
    MqttService localMqttService() {
        // Control management needs the service, but a local command must never imply device delivery.
        return new MqttService((message, timeout) -> {
            throw new IllegalStateException("MQTT commands are unavailable in the local profile.");
        });
    }

    @Bean
    ApplicationRunner localAdminAccount(DataSource dataSource, AccountRepository accounts, PasswordEncoder encoder) {
        return args -> {
            try (var connection = dataSource.getConnection()) {
                if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) {
                    throw new IllegalStateException("The local profile requires an in-memory H2 database.");
                }
            }
            if (accounts.findByUuid(ADMIN_UUID).isEmpty()) {
                accounts.saveAndFlush(AccountEntity.builder()
                        .uuid(ADMIN_UUID)
                        .secret(encoder.encode("admin"))
                        .admin(true)
                        .locale("en")
                        .marketIndexNameConfirmed(true)
                        .agreedTerms(true)
                        .agreedTermsAt(Instant.now())
                        .build());
            }
        };
    }
}

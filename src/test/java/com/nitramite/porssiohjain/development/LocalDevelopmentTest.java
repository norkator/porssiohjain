package com.nitramite.porssiohjain.development;

import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.scheduled.Scheduler;
import com.nitramite.porssiohjain.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.config.location=classpath:/application-local.properties")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class LocalDevelopmentTest {
    @Autowired AccountRepository accounts;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;

    @Test
    void seededAccountAuthenticatesThroughRestAndHasAdminAccess() throws Exception {
        var account = accounts.findByUuid(LocalDevelopmentConfiguration.ADMIN_UUID).orElseThrow();
        assertTrue(account.isAdmin());
        assertFalse(account.isDemo());
        assertTrue(account.isAgreedTerms());
        assertNotEquals("admin", account.getSecret());
        assertTrue(encoder.matches("admin", account.getSecret()));

        mvc.perform(post("/account/login").contentType("application/json")
                        .content("""
                                {"uuid":"00000000-0000-0000-0000-000000000001","secret":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.token").isNotEmpty());
        var login = auth.login("127.0.0.2", account.getUuid(), "admin");
        mvc.perform(get("/api/admin/users").header("Authorization", login.getToken()))
                .andExpect(status().isOk());
        assertThrows(IllegalArgumentException.class,
                () -> auth.login("127.0.0.3", account.getUuid(), "wrong-password"));
    }

    @Test
    void localProfileUsesMemoryDatabaseWithoutJobsOrMqtt() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        assertTrue(context.getBeansOfType(Scheduler.class).isEmpty());
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty());
        assertTrue(context.getBeansOfType(MqttPahoClientFactory.class).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> context.getBean(MqttService.class).publish("test", "test"));
    }

    @Test
    void defaultProfileDoesNotRegisterLocalAccountSeeder() {
        new ApplicationContextRunner().withUserConfiguration(LocalDevelopmentConfiguration.class)
                .run(other -> assertFalse(other.containsBean("localAdminAccount")));
    }

    @Test
    void seederRefusesPersistentDatabase() throws Exception {
        var otherDataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        var otherAccounts = mock(AccountRepository.class);
        when(otherDataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost/real-database");

        var runner = new LocalDevelopmentConfiguration().localAdminAccount(otherDataSource, otherAccounts, encoder);
        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verifyNoInteractions(otherAccounts);
    }
}

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.PushNotificationTokenEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushNotificationTokenServiceTest {
    @Test void onlyAnExplicitlyCapableAndroidRegistrationEnablesStoveActionsAndDowngradesResetIt() {
        PushNotificationTokenRepository tokens = mock();
        AccountRepository accounts = mock();
        DemoAccountGuard guard = mock();
        var account = AccountEntity.builder().id(7L).build();
        var entity = new PushNotificationTokenEntity();
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(tokens.findByToken("fixture-token")).thenReturn(Optional.of(entity));
        when(tokens.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new PushNotificationTokenService(tokens, accounts, guard);
        service.registerPushNotificationToken(7L, "fixture-token", "ANDROID", "Phone", true);
        assertThat(entity.isStoveActionsSupported()).isTrue();
        service.registerPushNotificationToken(7L, "fixture-token", "android", "Phone");
        assertThat(entity.isStoveActionsSupported()).isFalse();
        service.registerPushNotificationToken(7L, "fixture-token", "ios", "Phone", true);
        assertThat(entity.isStoveActionsSupported()).isFalse();
    }

    @Test void migrationKeepsExistingPushTokensOnTheLegacyFormat() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:push-capability-" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            connection.createStatement().execute("create table push_notification_token (id bigint primary key)");
            connection.createStatement().execute("insert into push_notification_token values (1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V126__push_token_stove_actions.sql"));
            try (var result = connection.createStatement().executeQuery("select stove_actions_supported from push_notification_token")) {
                assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isFalse();
            }
        }
    }
}

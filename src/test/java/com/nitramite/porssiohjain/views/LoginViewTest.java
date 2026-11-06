package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.QrLoginService;
import com.nitramite.porssiohjain.development.LocalDevelopmentConfiguration;
import com.nitramite.porssiohjain.entity.enums.AccountActivitySource;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginViewTest {

    private static final String UUID = "00000000-0000-0000-0000-000000000001";

    private AuthService auth;
    private LoginView view;
    private TextField uuid;
    private PasswordField secret;
    private Button login;
    private I18nService i18n;
    private QrLoginService qrLogin;

    @BeforeEach
    void setUp() {
        var session = mock(VaadinSession.class);
        var request = mock(VaadinRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        auth = mock(AuthService.class);
        i18n = mock(I18nService.class, invocation -> invocation.getArgument(0));
        qrLogin = mock(QrLoginService.class);
        VaadinSession.setCurrent(session);
        CurrentInstance.set(VaadinRequest.class, request);
        UI.setCurrent(new UI());
        createView(new MockEnvironment());
    }

    private void createView(MockEnvironment environment) {
        view = new LoginView(auth, i18n, qrLogin, environment);
        uuid = children(view).filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow();
        secret = children(view).filter(PasswordField.class::isInstance).map(PasswordField.class::cast).findFirst().orElseThrow();
        login = button("login.button.login");
    }

    @AfterEach
    void tearDown() {
        CurrentInstance.set(VaadinRequest.class, null);
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " \t "})
    void loginRequiresBothNonBlankCredentials(String blank) {
        assertFalse(login.isEnabled());
        uuid.setValue(UUID);
        secret.setValue(blank);
        assertFalse(login.isEnabled());

        secret.setValue("test-secret");
        assertTrue(login.isEnabled());
        uuid.setValue(blank);
        assertFalse(login.isEnabled());

        uuid.setValue(UUID);
        assertTrue(login.isEnabled());
        secret.clear();
        assertFalse(login.isEnabled());
        verifyNoInteractions(auth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " \t "})
    void submissionRejectsBlankSecretEvenIfButtonIsEnabled(String blank) {
        uuid.setValue(UUID);
        secret.setValue(blank);
        login.setEnabled(true);
        login.click();

        verifyNoInteractions(auth);
    }

    @Test
    void demoFillsCredentialsAndEnablesLoginWhileQrNeedsNoCredentials() {
        assertTrue(button("login.button.qr").isEnabled());
        button("login.demoAccount").click();

        assertFalse(uuid.getValue().isBlank());
        assertFalse(secret.getValue().isBlank());
        assertTrue(login.isEnabled());
        verifyNoInteractions(auth);
    }

    @Test
    void adminAliasUsesNormalPasswordAuthenticationInLocalProfile() {
        createView(new MockEnvironment().withProperty("spring.profiles.active", "local"));
        uuid.setValue("admin");
        secret.setValue("wrong-password");
        login.click();

        verify(auth).login("127.0.0.1", LocalDevelopmentConfiguration.ADMIN_UUID,
                "wrong-password", AccountActivitySource.VAADIN);
    }

    @Test
    void adminAliasIsRejectedOutsideLocalProfile() {
        uuid.setValue("admin");
        secret.setValue("admin");
        login.click();

        verifyNoInteractions(auth);
    }

    private Button button(String label) {
        return children(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(label)).findFirst().orElseThrow();
    }

    private Stream<Component> children(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::children));
    }
}

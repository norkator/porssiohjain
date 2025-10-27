package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Should create account in real DB and return JSON")
    void createAccountShouldReturnJson() throws Exception {
        mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "10.10.10.10")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.uuid").isString())
                .andExpect(jsonPath("$.secret").isString())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(accountRepository.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should hit account creation rate limit after 2 attempts")
    void shouldReturnTooManyRequestsAfterRateLimit() throws Exception {
        mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "20.20.20.20")
                        .contentType("application/json"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/account/create")
                        .header("X-Forwarded-For", "20.20.20.20")
                        .contentType("application/json"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Too many account creations. Try again later."));
    }

    @Test
    @DisplayName("Should login successfully and return JSON token")
    void loginShouldReturnJson() throws Exception {
        String password = "supersecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String requestBody = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(account.getUuid(), password);

        mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "30.30.30.30")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    @DisplayName("Should hit login rate limit after some attempts")
    void shouldReturnTooManyRequestsAfterLoginLimit() throws Exception {
        String password = "supersecret";
        AccountEntity account = new AccountEntity();
        account.setUuid(UUID.randomUUID());
        account.setSecret(passwordEncoder.encode(password));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String requestBody = """
                {
                    "uuid": "%s",
                    "secret": "%s"
                }
                """.formatted(account.getUuid(), password);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/account/login")
                            .header("X-Forwarded-For", "40.40.40.40")
                            .contentType("application/json")
                            .content(requestBody))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/account/login")
                        .header("X-Forwarded-For", "40.40.40.40")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Too many login attempts. Try again later."));
    }

}
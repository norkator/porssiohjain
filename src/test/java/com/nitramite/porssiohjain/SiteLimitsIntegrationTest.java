package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.TokenEntity;
import com.nitramite.porssiohjain.entity.enums.AccountTier;
import com.nitramite.porssiohjain.entity.enums.SiteType;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.SiteRepository;
import com.nitramite.porssiohjain.entity.repository.TokenRepository;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.SiteWeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SiteLimitsIntegrationTest {
    private static final String SITE = """
            {"name":"Home","type":"HOME","enabled":false,"timezone":"Europe/Helsinki"}
            """;
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired SiteRepository sites;
    @Autowired TokenRepository tokens;
    @Autowired SiteService service;
    @MockitoBean MqttService mqttService;
    @MockitoBean SiteWeatherService weatherService;

    @ParameterizedTest
    @CsvSource({"FREE, 1", "PRO, 5"})
    void apiCountsDisabledSitesAndRejectsCreationPastTheTierAllowance(AccountTier tier, int limit) throws Exception {
        AccountEntity account = account(tier);
        String token = token(account);
        for (int i = 0; i < limit; i++) {
            mvc.perform(post("/api/sites").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(SITE))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/sites").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(SITE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Site limit reached for this account (" + limit + ")."));
        assertThat(sites.countByAccountId(account.getId())).isEqualTo(limit);
        mvc.perform(get("/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.siteLimit").value(limit));
        // Another account's sites do not consume this account's allowance.
        AccountEntity other = account(AccountTier.FREE);
        service.createSite(other.getId(), "Other home", SiteType.HOME, true, null, "Europe/Helsinki", null);
        assertThat(sites.countByAccountId(other.getId())).isEqualTo(1);
    }

    @Test
    void businessCanCreateMoreThanFiveSitesAndReportsAnUnlimitedAllowance() throws Exception {
        AccountEntity account = account(AccountTier.BUSINESS);
        String token = token(account);
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/sites").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(SITE))
                    .andExpect(status().isOk());
        }
        assertThat(sites.countByAccountId(account.getId())).isEqualTo(8);
        mvc.perform(get("/me").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.siteLimit").hasJsonPath())
                .andExpect(jsonPath("$.siteLimit").value(nullValue()));
    }

    @Test
    void downgradePreservesSitesAndAllowsEditingButRejectsNewSites() throws Exception {
        AccountEntity account = account(AccountTier.PRO);
        var first = service.createSite(account.getId(), "Home", SiteType.HOME, true, null, "Europe/Helsinki", null);
        service.createSite(account.getId(), "Cottage", SiteType.HOME, false, null, "Europe/Helsinki", null);
        account.setTier(AccountTier.FREE);
        accounts.saveAndFlush(account);
        String token = token(account);
        mvc.perform(put("/api/sites/" + first.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(SITE))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sites").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(SITE))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sites").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(sites.countByAccountId(account.getId())).isEqualTo(2);
    }

    @Test
    void concurrentCreatesCannotBothConsumeTheLastSiteSlot() throws Exception {
        AccountEntity account = account(AccountTier.FREE);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> create = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            try {
                service.createSite(account.getId(), "Home", SiteType.HOME, true, null, "Europe/Helsinki", null);
                return true;
            } catch (IllegalArgumentException exception) {
                if (!exception.getMessage().startsWith("Site limit reached")) throw exception;
                return false;
            }
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(create);
            var second = workers.submit(create);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(sites.countByAccountId(account.getId())).isEqualTo(1);
    }

    private AccountEntity account(AccountTier tier) {
        return accounts.saveAndFlush(AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString())
                .tier(tier).build());
    }

    private String token(AccountEntity account) {
        String value = UUID.randomUUID().toString();
        tokens.saveAndFlush(TokenEntity.builder().account(account).token(value).build());
        return value;
    }
}

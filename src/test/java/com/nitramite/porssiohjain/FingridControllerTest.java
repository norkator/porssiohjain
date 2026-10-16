package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.AuthInterceptor;
import com.nitramite.porssiohjain.auth.WebConfig;
import com.nitramite.porssiohjain.contollers.FingridController;
import com.nitramite.porssiohjain.contollers.WindNotificationsController;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FingridService;
import com.nitramite.porssiohjain.services.WindNotificationService;
import com.nitramite.porssiohjain.services.models.WindForecastChartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(FingridControllerTest.Config.class)
class FingridControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired FingridService fingrid;
    @Autowired AuthService auth;
    @Autowired WindNotificationService notifications;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(fingrid, auth, notifications);
        mvc = webAppContextSetup(context).build();
    }

    @Test
    void marketingVisitorsCanReadTheWholeForecastWithoutCredentials() throws Exception {
        when(fingrid.getWindForecastChart("Europe/Helsinki")).thenReturn(WindForecastChartResponse.builder()
                .timezone("Europe/Helsinki").todayAverage(new BigDecimal("2000.00"))
                .tomorrowAverage(new BigDecimal("1000.00")).tomorrowDropPercent(new BigDecimal("50.00"))
                .points(List.of(
                        point("2026-10-15T21:00:00Z", "2000"),
                        point("2026-10-19T06:00:00Z", "5000")))
                .build());

        for (String origin : List.of("https://www.porssiohjain.fi", "https://www.energiaohjain.fi", "http://localhost:4202")) {
            mvc.perform(get("/fingrid/wind-forecast").param("timezone", "Europe/Helsinki").header("Origin", origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                    .andExpect(jsonPath("$.timezone").value("Europe/Helsinki"))
                    .andExpect(jsonPath("$.todayAverage").value(2000))
                    .andExpect(jsonPath("$.tomorrowAverage").value(1000))
                    .andExpect(jsonPath("$.tomorrowDropPercent").value(50))
                    .andExpect(jsonPath("$.points.length()").value(2))
                    .andExpect(jsonPath("$.points[1].startTime").value("2026-10-19T06:00:00Z"))
                    .andExpect(jsonPath("$.points[1].megawatts").value(5000));
        }
        verifyNoInteractions(auth, notifications);
    }

    @Test
    void publicCorsAllowsForecastReadsAndRejectsWrites() throws Exception {
        mvc.perform(options("/fingrid/wind-forecast").header("Origin", "https://www.energiaohjain.fi")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,HEAD,OPTIONS"));
        mvc.perform(options("/fingrid/wind-forecast").header("Origin", "https://www.energiaohjain.fi")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fingrid, auth, notifications);
    }

    @Test
    void accountWindNotificationsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/wind-notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wind-notifications")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/wind-notifications/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/wind-notifications/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(fingrid, auth, notifications);
    }

    private WindForecastChartResponse.Point point(String start, String megawatts) {
        return WindForecastChartResponse.Point.builder().startTime(Instant.parse(start))
                .endTime(Instant.parse(start).plusSeconds(900)).megawatts(new BigDecimal(megawatts)).build();
    }

    @Configuration
    @EnableWebMvc
    @Import({WebConfig.class, AuthInterceptor.class, AuthContext.class, FingridController.class, WindNotificationsController.class})
    static class Config {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean FingridService fingridService() { return mock(FingridService.class); }
        @Bean WindNotificationService windNotificationService() { return mock(WindNotificationService.class); }
    }
}

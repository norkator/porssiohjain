package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.FingridService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.TodayPriceChartPointResponse;
import com.nitramite.porssiohjain.services.models.TodayPriceChartResponse;
import com.nitramite.porssiohjain.services.models.WindForecastChartResponse;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ForecastsViewTest {

    private final AuthService auth = mock(AuthService.class);
    private final NordpoolService nordpool = mock(NordpoolService.class);
    private final FingridService fingrid = mock(FingridService.class);
    private I18nService i18n;
    private MockedStatic<ViewAuthUtils> accounts;

    @BeforeEach
    void setup() {
        UI ui = new UI();
        ui.setLocale(Locale.ENGLISH);
        UI.setCurrent(ui);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        i18n = new I18nService(messages);
        accounts = mockStatic(ViewAuthUtils.class);
        accounts.when(() -> ViewAuthUtils.findAuthenticatedAccount(auth)).thenReturn(AccountEntity.builder().id(7L).build());
        when(nordpool.getTodayChart(null, "Europe/Helsinki", null)).thenReturn(chart(point("2026-11-06T00:00:00Z", "5")));
        when(nordpool.getTomorrowChart(null, "Europe/Helsinki", null)).thenReturn(chart());
        when(fingrid.getWindForecastChart("Europe/Helsinki")).thenReturn(WindForecastChartResponse.builder()
                .todayAverage(new BigDecimal("2000")).tomorrowAverage(new BigDecimal("1000"))
                .tomorrowDropPercent(new BigDecimal("50"))
                .points(List.of(WindForecastChartResponse.Point.builder().startTime(Instant.parse("2026-11-06T00:00:00Z"))
                        .endTime(Instant.parse("2026-11-09T00:15:00Z")).megawatts(new BigDecimal("2000")).build())).build());
    }

    @AfterEach
    void cleanup() {
        accounts.close();
        UI.setCurrent(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fi"})
    void reusesHybridForecastsAndExplainsPendingTomorrowPricesInBothLanguages(String language) {
        UI.getCurrent().setLocale(Locale.of(language));
        var view = open();

        String text = view.getElement().getTextRecursively();
        assertThat(text).contains(i18n.t("home.forecasts"), i18n.t("forecasts.prices.tomorrowPending"),
                i18n.t("forecasts.wind.title"), "Europe/Helsinki", "2", "50");
        assertThat(text).doesNotContain("forecasts.");
        verify(nordpool).getTodayChart(null, "Europe/Helsinki", null);
        verify(nordpool).getTomorrowChart(null, "Europe/Helsinki", null);
        verify(fingrid).getWindForecastChart("Europe/Helsinki");
    }

    @Test
    void windRemainsAvailableWhenPricesFail() {
        when(nordpool.getTodayChart(null, "Europe/Helsinki", null)).thenThrow(new IllegalStateException("unavailable"));
        when(nordpool.getTomorrowChart(null, "Europe/Helsinki", null)).thenThrow(new IllegalStateException("unavailable"));
        assertThat(open().getElement().getTextRecursively()).contains(i18n.t("forecasts.prices.failed"), "2,000");
    }

    @Test
    void todayPricesRemainAvailableWhenTomorrowAndWindFail() {
        when(nordpool.getTomorrowChart(null, "Europe/Helsinki", null)).thenThrow(new IllegalStateException("unavailable"));
        when(fingrid.getWindForecastChart("Europe/Helsinki")).thenThrow(new IllegalStateException("unavailable"));
        assertThat(open().getElement().getTextRecursively()).contains("5.00", i18n.t("forecasts.prices.partial"),
                i18n.t("forecasts.wind.failed"));
    }

    @Test
    void unauthenticatedNavigationLoadsNoForecasts() {
        var event = mock(BeforeEnterEvent.class);
        accounts.when(() -> ViewAuthUtils.rerouteToLoginIfUnauthenticated(event, auth)).thenReturn(true);
        new ForecastsView(auth, i18n, nordpool, fingrid).beforeEnter(event);
        verifyNoInteractions(nordpool, fingrid);
    }

    @Test
    void mergingPricesSortsByInstantAndDeduplicatesOverlappingTimestamps() {
        var earlier = point("2026-10-25T00:00:00Z", "3");
        var repeatedLocalHour = point("2026-10-25T01:00:00Z", "4");
        var later = point("2026-10-26T00:00:00Z", "5");
        assertThat(ForecastsView.mergePrices(List.of(chart(later, repeatedLocalHour), chart(earlier, repeatedLocalHour))))
                .containsExactly(earlier, repeatedLocalHour, later);
    }

    private ForecastsView open() {
        var view = new ForecastsView(auth, i18n, nordpool, fingrid);
        view.beforeEnter(mock(BeforeEnterEvent.class));
        return view;
    }

    private TodayPriceChartResponse chart(TodayPriceChartPointResponse... points) {
        return TodayPriceChartResponse.builder().points(List.of(points)).build();
    }

    private TodayPriceChartPointResponse point(String time, String price) {
        return TodayPriceChartPointResponse.builder().timestamp(Instant.parse(time)).price(new BigDecimal(price)).build();
    }
}

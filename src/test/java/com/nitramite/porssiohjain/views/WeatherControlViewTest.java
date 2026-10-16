package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.services.AuthService;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.HeatPumpStateDialogService;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.SiteService;
import com.nitramite.porssiohjain.services.WeatherControlService;
import com.nitramite.porssiohjain.services.models.SiteResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastPointResponse;
import com.nitramite.porssiohjain.services.models.SiteWeatherForecastResponse;
import com.nitramite.porssiohjain.services.models.WeatherControlResponse;
import com.nitramite.porssiohjain.views.components.SiteWeatherForecastChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeatherControlViewTest {
    private final WeatherControlService controls = mock(WeatherControlService.class);
    private final SiteService sites = mock(SiteService.class);
    private WeatherControlView view;
    private Component weatherSection;

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        VaadinSession.setCurrent(mock(VaadinSession.class));
        view = new WeatherControlView(mock(AuthService.class), controls, sites, mock(DeviceService.class),
                mock(HeatPumpStateDialogService.class), mock(I18nService.class, invocation -> invocation.getArgument(0)));
        ReflectionTestUtils.setField(view, "accountId", 7L);
        ReflectionTestUtils.setField(view, "weatherControlId", 8L);
        ReflectionTestUtils.setField(view, "weatherControl", WeatherControlResponse.builder()
                .id(8L).siteId(9L).siteTimezone("Europe/Helsinki").build());
        weatherSection = ReflectionTestUtils.invokeMethod(view, "createWeatherInfoSection");
    }

    @AfterEach
    void tearDown() {
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void showsTodaysForecastBelowCurrentConditionsForOwnedAndSharedControls(boolean shared) {
        ReflectionTestUtils.setField(view, "weatherControl", WeatherControlResponse.builder()
                .id(8L).siteId(9L).siteTimezone("America/Los_Angeles").shared(shared).build());
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        when(controls.getStoredWeatherForecast(7L, 8L, start, end)).thenReturn(forecast(9L));

        ReflectionTestUtils.invokeMethod(view, "refreshWeatherInfo", 9L);

        verify(controls).getStoredWeatherForecast(7L, 8L, start, end);
        verifyNoInteractions(sites);
        assertThat(descendants(weatherSection).filter(SiteWeatherForecastChart.class::isInstance).count()).isEqualTo(1);
        assertThat(weatherSection.getChildren().findFirst().orElseThrow()).isInstanceOf(FormLayout.class);
        assertThat(field("temperatureField").getValue()).isEqualTo("7 °C");
        assertThat(field("windSpeedField").getValue()).isEqualTo("4 m/s");
        assertThat(field("humidityField").getValue()).isEqualTo("70 %");
    }

    @Test
    void changingSiteUsesSelectedSitesForecastAndClearingSelectionRemovesChart() {
        ReflectionTestUtils.setField(view, "sites", List.of(SiteResponse.builder()
                .id(10L).timezone("America/New_York").build()));
        ZoneId zone = ZoneId.of("America/New_York");
        Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        when(sites.getStoredSiteWeatherForecast(7L, 10L, start, end)).thenReturn(forecast(10L));

        ReflectionTestUtils.invokeMethod(view, "refreshWeatherInfo", 10L);

        verify(sites).getStoredSiteWeatherForecast(7L, 10L, start, end);
        verifyNoInteractions(controls);
        assertThat(descendants(weatherSection).filter(SiteWeatherForecastChart.class::isInstance).count()).isEqualTo(1);

        ReflectionTestUtils.invokeMethod(view, "refreshWeatherInfo", (Object) null);

        assertThat(field("temperatureField").isEmpty()).isTrue();
        assertThat(descendants(weatherSection).filter(SiteWeatherForecastChart.class::isInstance)).isEmpty();
        assertThat(weatherSection.getElement().getTextRecursively()).contains("weatherControl.weather.chart.selectSite");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingTodaysForecastShowsEmptyStateInsteadOfTomorrowsWeather(boolean missingPoints) {
        ZoneId zone = ZoneId.of("Europe/Helsinki");
        Instant end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        when(controls.getStoredWeatherForecast(eq(7L), eq(8L), any(), any())).thenReturn(
                SiteWeatherForecastResponse.builder().points(missingPoints ? null
                        : List.of(SiteWeatherForecastPointResponse.builder().time(end).temperature(BigDecimal.TEN).build())).build());

        ReflectionTestUtils.invokeMethod(view, "refreshWeatherInfo", 9L);

        assertThat(descendants(weatherSection).filter(SiteWeatherForecastChart.class::isInstance)).isEmpty();
        assertThat(weatherSection.getElement().getTextRecursively()).contains("weatherControl.weather.chart.empty");
        assertThat(field("weatherTimestampField").getValue()).isEqualTo("weatherControl.weather.notAvailable");
        assertThat(field("temperatureField").getValue()).isEqualTo("-");
    }

    private SiteWeatherForecastResponse forecast(Long siteId) {
        return SiteWeatherForecastResponse.builder().siteId(siteId).points(List.of(
                SiteWeatherForecastPointResponse.builder().time(Instant.now()).temperature(BigDecimal.valueOf(7))
                        .windSpeedMs(BigDecimal.valueOf(4)).humidity(BigDecimal.valueOf(70)).build())).build();
    }

    private TextField field(String name) {
        return (TextField) ReflectionTestUtils.getField(view, name);
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}

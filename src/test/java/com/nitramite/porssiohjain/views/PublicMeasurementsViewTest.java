package com.nitramite.porssiohjain.views;

import com.nitramite.porssiohjain.entity.enums.DeviceMeasurementType;
import com.nitramite.porssiohjain.services.I18nService;
import com.nitramite.porssiohjain.services.PublicMeasurementService;
import com.nitramite.porssiohjain.services.PublicMeasurementService.*;
import com.nitramite.porssiohjain.views.components.DeviceMeasurementHistoryChart;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicMeasurementsViewTest {
    private final PublicMeasurementService service = mock();
    private final UUID pageId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-25T10:00:00Z");
    private PublicMeasurementsView view;

    @BeforeEach
    void setup() {
        UI.setCurrent(new UI());
        UI.getCurrent().setLocale(Locale.ENGLISH);
        VaadinSession.setCurrent(null);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("translations/messages");
        messages.setDefaultEncoding("UTF-8");
        view = new PublicMeasurementsView(service, new I18nService(messages));
    }

    @AfterEach
    void cleanup() {
        UI.setCurrent(null);
        VaadinSession.setCurrent(null);
    }

    @Test
    void anonymousOverviewShowsSeparateChartsAndLinksToIndividualWeekViews() {
        when(service.publicPage(eq(pageId), isNull(), eq(1), any())).thenReturn(Optional.of(
                new PublicPage(pageId, now.minusSeconds(86400), now, List.of(series(42L), series(43L)))));
        enter(pageId.toString(), null);
        assertThat(PublicMeasurementsView.class.isAnnotationPresent(AnonymousAllowed.class)).isTrue();
        assertThat(PublicMeasurementsView.class.getAnnotation(Route.class).layout())
                .isNotEqualTo(MainLayout.class);
        assertThat(descendants(view).filter(DeviceMeasurementHistoryChart.class::isInstance)).hasSize(2);
        assertThat(descendants(view).filter(Anchor.class::isInstance).map(Anchor.class::cast).map(Anchor::getHref))
                .containsExactly("public-measurements/" + pageId + "/42", "public-measurements/" + pageId + "/43");
        assertThat(view.getElement().getTextRecursively()).contains("last 24 hours", "Garden · Humidity",
                "Europe/Helsinki", "sensor report time is unknown");
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailDefaultsToWeekAndOffersShorterRangesWithBackLink() {
        when(service.publicPage(eq(pageId), eq(42L), anyInt(), any())).thenReturn(Optional.of(
                new PublicPage(pageId, now.minusSeconds(7 * 86400), now, List.of(series(42L)))));
        enter(pageId.toString(), "42");
        verify(service).publicPage(eq(pageId), eq(42L), eq(7), any());
        var range = (Select<Integer>) descendants(view).filter(Select.class::isInstance).findFirst().orElseThrow();
        assertThat(range.getListDataView().getItems()).containsExactly(1, 3, 7);
        range.setValue(3);
        verify(service).publicPage(eq(pageId), eq(42L), eq(3), any());
        assertThat(descendants(view).filter(DeviceMeasurementHistoryChart.class::isInstance)).hasSize(1);
        assertThat(descendants(view).filter(Anchor.class::isInstance).map(Anchor.class::cast).map(Anchor::getHref))
                .containsExactly("public-measurements/" + pageId);
    }

    @Test
    void malformedLinkAndRevokedMeasurementShowNoCharts() {
        enter("not-a-uuid", null);
        verifyNoInteractions(service);
        assertThat(view.getElement().getTextRecursively()).contains("unavailable");
        enter(pageId.toString(), "42");
        assertThat(descendants(view).filter(DeviceMeasurementHistoryChart.class::isInstance)).isEmpty();
        assertThat(view.getElement().getTextRecursively()).contains("unavailable");
    }

    @Test
    void visitorCanSwitchLanguageWithoutSigningIn() {
        when(service.publicPage(eq(pageId), isNull(), eq(1), any())).thenReturn(Optional.of(
                new PublicPage(pageId, now.minusSeconds(86400), now, List.of())));
        enter(pageId.toString(), null);
        descendants(view).filter(c -> c instanceof Button b && b.getText().equals("Suomeksi"))
                .map(Button.class::cast).findFirst().orElseThrow().click();
        assertThat(view.getElement().getTextRecursively()).contains("Jaetut mittaukset", "Tällä sivulla ei ole");
    }

    private PublicSeries series(Long id) {
        return new PublicSeries(id, "Garden", DeviceMeasurementType.HUMIDITY, "humidity", "%", "Europe/Helsinki",
                List.of(new Reading(now.minusSeconds(60), new BigDecimal("45.5"))), true);
    }

    private void enter(String page, String measurement) {
        BeforeEnterEvent event = mock();
        Map<String, String> params = new HashMap<>();
        params.put("pageId", page);
        if (measurement != null) params.put("measurementId", measurement);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(params));
        view.beforeEnter(event);
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}

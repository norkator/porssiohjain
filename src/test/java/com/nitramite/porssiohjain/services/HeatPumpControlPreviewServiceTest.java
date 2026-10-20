package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse;
import com.nitramite.porssiohjain.services.models.HeatPumpControlPreviewResponse.Reason;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeatPumpControlPreviewServiceTest {
    private final ControlService controls = mock(ControlService.class);
    private final ControlRepository controlRepository = mock(ControlRepository.class);
    private final ControlHeatPumpRepository rules = mock(ControlHeatPumpRepository.class);
    private final ControlTableRepository schedule = mock(ControlTableRepository.class);
    private final NordpoolRepository market = mock(NordpoolRepository.class);
    private final DeviceAcDataRepository acData = mock(DeviceAcDataRepository.class);
    private final WeatherControlHeatPumpRepository weather = mock(WeatherControlHeatPumpRepository.class);
    private final ProductionSourceHeatPumpRepository production = mock(ProductionSourceHeatPumpRepository.class);
    private final HeatPumpControlPreviewService service = new HeatPumpControlPreviewService(controls,
            controlRepository, rules, schedule, market, acData, weather, production, new ControlPriceService(market),
            new HeatPumpPreviewStateDecoder(new ToshibaAcStateHexDecoderService()));
    private final Instant now = Instant.parse("2026-10-20T09:07:00Z");
    private ControlEntity control;
    private DeviceEntity pump;

    @BeforeEach
    void setup() {
        AccountEntity account = new AccountEntity();
        account.setMarketIndexName("FI");
        control = ControlEntity.builder().id(5L).account(account).timezone("Europe/Helsinki")
                .mode(ControlMode.BELOW_MAX_PRICE).taxPercent(BigDecimal.ZERO).build();
        pump = new DeviceEntity();
        pump.setId(9L);
        pump.setDeviceName("Living room");
        pump.setEnabled(true);
        pump.setDeviceType(DeviceType.HEAT_PUMP);
        when(controlRepository.findById(5L)).thenReturn(Optional.of(control));
        when(acData.findByDevice(pump)).thenReturn(Optional.of(DeviceAcDataEntity.builder()
                .device(pump).acType(AcType.MITSUBISHI_MELCLOUD_HOME).build()));
    }

    @Test
    void usesCombinedPriceAndFirstMatchingRuleWithoutRequiringActiveControl() {
        control.setTaxPercent(new BigDecimal("25.5"));
        control.setTransferContract(ElectricityContractEntity.builder().staticPrice(new BigDecimal("2"))
                .taxAmount(new BigDecimal("1")).build());
        var heat = rule(10L, ComparisonType.LESS_THAN, "20", true, 22);
        var competing = rule(11L, ComparisonType.LESS_THAN, "20", false, 18);
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of(heat, competing));
        prices(List.of(price(now.minusSeconds(420), now.plusSeconds(480), "100")));

        var preview = service.preview(7L, 5L, now);

        assertThat(preview.prices().stream().filter(p -> p.start().equals(now)).findFirst().orElseThrow().price())
                .isEqualByComparingTo("15.55");
        var first = preview.pumps().getFirst().periods().getFirst();
        assertThat(first.start()).isEqualTo(now);
        assertThat(first.reason()).isEqualTo(Reason.PRICE);
        assertThat(first.ruleId()).isEqualTo(10L);
        assertThat(first.state().temperature()).isEqualTo(22);
        assertThat(first.state().power()).isTrue();
        assertThat(first.assumed()).isFalse();
    }

    @Test
    void noMatchingRuleCarriesProjectedSettingWithoutInventingOffAndExplicitOffRemainsOff() {
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of(
                rule(10L, ComparisonType.LESS_THAN, "10", true, 22),
                rule(11L, ComparisonType.GREATER_THAN, "20", false, 18)));
        Instant boundary = now.plusSeconds(480);
        prices(List.of(price(now.minusSeconds(420), boundary, "50"),
                price(boundary, boundary.plusSeconds(900), "150"),
                price(boundary.plusSeconds(900), boundary.plusSeconds(1800), "250")));

        var periods = service.preview(7L, 5L, now).pumps().getFirst().periods();

        assertThat(periods.get(0).state().power()).isTrue();
        assertThat(periods.get(1).reason()).isEqualTo(Reason.NO_MATCH);
        assertThat(periods.get(1).state().power()).isTrue();
        assertThat(periods.get(1).assumed()).isTrue();
        assertThat(periods.get(2).state().power()).isFalse();
        assertThat(periods.get(2).ruleId()).isEqualTo(11L);
        assertThat(periods.get(2).assumed()).isFalse();
        assertThat(periods.get(3).reason()).isEqualTo(Reason.MISSING_PRICE);
        assertThat(periods.get(3).state().power()).isFalse();
    }

    @Test
    void thresholdEqualityAndMissingPricesProduceUnknownUntilFirstMatchingCommand() {
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of(
                rule(10L, ComparisonType.LESS_THAN, "10", true, 22),
                rule(11L, ComparisonType.GREATER_THAN, "10", false, 18)));
        prices(List.of(price(now.minusSeconds(420), now.plusSeconds(480), "100")));

        var periods = service.preview(7L, 5L, now).pumps().getFirst().periods();

        assertThat(periods.getFirst().reason()).isEqualTo(Reason.NO_MATCH);
        assertThat(periods.getFirst().state()).isNull();
        assertThat(periods.getLast().reason()).isEqualTo(Reason.MISSING_PRICE);
        assertThat(periods.getLast().state()).isNull();
        assertThat(periods.getLast().stateHex()).isNull();
    }

    @Test
    void manualRuleWorksWithoutPricesAndOnlyFinalScheduleRowsActivatePriceModes() {
        var rule = rule(10L, null, null, true, 21);
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of(rule));
        control.setMode(ControlMode.MANUAL);
        control.setManualOn(true);
        assertThat(service.preview(7L, 5L, now).pumps().getFirst().periods())
                .allSatisfy(period -> assertThat(period.reason()).isEqualTo(Reason.SCHEDULE));

        control.setMode(ControlMode.CHEAPEST_HOURS);
        var row = ControlTableEntity.builder().startTime(now.minusSeconds(420)).endTime(now.plusSeconds(480))
                .status(Status.PLANNED).build();
        when(schedule.findActivePeriodsOverlapping(eq(5L), eq(Status.FINAL), any(), any())).thenReturn(List.of(row));
        assertThat(service.preview(7L, 5L, now).pumps().getFirst().periods().getFirst().reason()).isEqualTo(Reason.NO_MATCH);
        row.setStatus(Status.FINAL);
        var periods = service.preview(7L, 5L, now).pumps().getFirst().periods();
        assertThat(periods.getFirst().reason()).isEqualTo(Reason.SCHEDULE);
        assertThat(periods.get(1).start()).isEqualTo(row.getEndTime());
        assertThat(periods.get(1).reason()).isEqualTo(Reason.NO_MATCH);
    }

    @Test
    void disabledPumpAndMissingAcDataDoNotGetProjectedCommands() {
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of(rule(10L, null, null, true, 21)));
        pump.setEnabled(false);
        assertThat(service.preview(7L, 5L, now).pumps().getFirst().periods())
                .allSatisfy(period -> {
                    assertThat(period.reason()).isEqualTo(Reason.DISABLED);
                    assertThat(period.state()).isNull();
                });
        pump.setEnabled(true);
        when(acData.findByDevice(pump)).thenReturn(Optional.empty());
        assertThat(service.preview(7L, 5L, now).pumps().getFirst().periods())
                .allSatisfy(period -> assertThat(period.reason()).isEqualTo(Reason.NO_AC_DATA));
    }

    @Test
    void flagsCompetingAutomationAndKeepsPumpsIndependent() {
        var otherPump = new DeviceEntity();
        otherPump.setId(12L);
        otherPump.setEnabled(false);
        otherPump.setDeviceType(DeviceType.HEAT_PUMP);
        var first = rule(10L, ComparisonType.LESS_THAN, "10", true, 22);
        var second = rule(11L, null, null, false, 18);
        second.setDevice(otherPump);
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of(first, second));
        when(weather.findByDevice(pump)).thenReturn(List.of(new WeatherControlHeatPumpEntity()));
        prices(List.of(price(now.minusSeconds(420), now.plusSeconds(480), "50")));

        var pumps = service.preview(7L, 5L, now).pumps();

        assertThat(pumps).hasSize(2);
        assertThat(pumps.getFirst().competingRules()).isTrue();
        assertThat(pumps.getFirst().periods().getFirst().state().power()).isTrue();
        assertThat(pumps.getLast().periods().getFirst().state()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29, Europe/Helsinki, 188", "2026-10-25, Europe/Helsinki, 196",
            "2026-10-20, America/Los_Angeles, 192"})
    void usesTwoLocalCalendarDaysAndAuthoritativeMarketInstants(LocalDate day, String timezone, int quarters) {
        control.setTimezone(timezone);
        ZoneId zone = ZoneId.of(timezone);
        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(2).atStartOfDay(zone).toInstant();
        when(rules.findByControlIdOrderByIdAsc(5L)).thenReturn(List.of());
        prices(IntStream.range(0, quarters).mapToObj(i -> price(start.plusSeconds(i * 900L),
                start.plusSeconds((i + 1) * 900L), "50")).toList());

        HeatPumpControlPreviewResponse preview = service.preview(7L, 5L, start);

        verify(market).findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan("FI", start, end);
        assertThat(preview.start()).isEqualTo(start);
        assertThat(preview.end()).isEqualTo(end);
        assertThat(preview.prices()).hasSize(quarters);
        assertThat(preview.prices().getFirst().start()).isEqualTo(start);
        assertThat(preview.prices().getLast().end()).isEqualTo(end);
        assertThat(preview.prices()).allSatisfy(p -> assertThat(p.price()).isEqualByComparingTo("5"));
    }

    @Test
    void authorizesBeforeReadingAnyDeviceOrPrice() {
        doThrow(new SecurityException("Not accessible")).when(controls).getControl(7L, 5L);

        assertThatThrownBy(() -> service.preview(7L, 5L, now)).isInstanceOf(SecurityException.class);

        verifyNoInteractions(controlRepository, rules, schedule, market, acData, weather, production);
    }

    private void prices(List<NordpoolEntity> values) {
        when(market.findByMarketIndexNameAndDeliveryStartGreaterThanEqualAndDeliveryStartLessThan(any(), any(), any()))
                .thenReturn(values);
    }

    private NordpoolEntity price(Instant start, Instant end, String eurPerMwh) {
        NordpoolEntity result = new NordpoolEntity();
        result.setDeliveryStart(start);
        result.setDeliveryEnd(end);
        result.setPriceFi(new BigDecimal(eurPerMwh));
        return result;
    }

    private ControlHeatPumpEntity rule(Long id, ComparisonType comparison, String limit, boolean on, int temperature) {
        return ControlHeatPumpEntity.builder().id(id).control(control).device(pump)
                .controlAction(ControlAction.SET_TEMPERATURE).comparisonType(comparison)
                .priceLimit(limit == null ? null : new BigDecimal(limit))
                .stateHex("{\"power\":" + on + ",\"operationMode\":\"Heat\",\"setTemperature\":" + temperature + "}").build();
    }
}

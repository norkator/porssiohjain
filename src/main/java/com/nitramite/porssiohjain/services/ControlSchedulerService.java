package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.ControlTableResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ControlSchedulerService {

    private final NordpoolRepository nordpoolRepository;
    private final ControlRepository controlRepository;
    private final ControlTableRepository controlTableRepository;
    private final SystemLogService systemLogService;

    public List<ControlTableResponse> findByControlId(
            Long controlId
    ) {
        Optional<ControlEntity> controlEntity = controlRepository.findById(controlId);
        ZoneId controlZone = ZoneId.of(controlEntity.get().getTimezone());

        ZonedDateTime startOfDayLocal = LocalDate.now(controlZone).atStartOfDay(controlZone);
        Instant cutoffUtc = startOfDayLocal.toInstant();

        return controlTableRepository.findByControlIdAndStartTimeAfterOrderByStartTimeAsc(controlId,
                        cutoffUtc).stream()
                .map(this::toResponse)
                .toList();
    }

    private ControlTableResponse toResponse(ControlTableEntity e) {
        return ControlTableResponse.builder()
                .id(e.getId())
                .controlId(e.getControl().getId())
                .priceSnt(e.getPriceSnt())
                .status(e.getStatus())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .build();
    }

    @Transactional
    public void generateForControl(
            Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new IllegalArgumentException("Control not found: " + controlId));

        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(2, ChronoUnit.DAYS);

        generateInternal(List.of(control), startOfDay, endOfDay, Status.FINAL);
    }

    @Transactional
    public void generatePlannedForTomorrow() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(2, ChronoUnit.DAYS);
        List<ControlEntity> controls = controlRepository.findAll();
        log.info("generatePlannedForTomorrow for time {} - {}", startOfDay, endOfDay.toString());
        generateInternal(controls, startOfDay, endOfDay, Status.FINAL);
        systemLogService.log("Scheduled run of function 'generatePlannedForTomorrow' completed.");
    }

    private void generateInternal(
            List<ControlEntity> controls,
            Instant startTime,
            Instant endTime,
            Status status
    ) {
        List<NordpoolEntity> prices = nordpoolRepository.findByDeliveryStartBetween(startTime, endTime);

        for (ControlEntity control : controls) {
            BigDecimal taxMultiplier = BigDecimal.ONE.add(control.getTaxPercent().divide(BigDecimal.valueOf(100)));
            ControlMode controlMode = control.getMode();

            controlTableRepository.deleteByControlAndStartTimeBetween(control, startTime, endTime);
            controlTableRepository.flush();

            if (controlMode.equals(ControlMode.BELOW_MAX_PRICE)) {
                for (NordpoolEntity priceEntry : prices) {
                    BigDecimal priceSnt = priceEntry.getPriceFi().multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier);
                    if (priceSnt.compareTo(control.getMaxPriceSnt()) <= 0) {
                        ControlTableEntity entry = ControlTableEntity.builder()
                                .control(control)
                                .startTime(priceEntry.getDeliveryStart())
                                .endTime(priceEntry.getDeliveryEnd())
                                .priceSnt(priceSnt)
                                .status(status)
                                .build();

                        controlTableRepository.save(entry);
                    }
                }
            } else if (controlMode.equals(ControlMode.CHEAPEST_HOURS)) {
                Integer dailyOnMinutes = control.getDailyOnMinutes();
                BigDecimal maxPriceSnt = control.getMaxPriceSnt();

                Map<LocalDate, List<NordpoolEntity>> pricesByDay = prices.stream()
                        .collect(Collectors.groupingBy(p ->
                                p.getDeliveryStart().atZone(ZoneId.systemDefault()).toLocalDate()
                        ));

                for (Map.Entry<LocalDate, List<NordpoolEntity>> dayEntry : pricesByDay.entrySet()) {
                    List<NordpoolEntity> dailyPrices = dayEntry.getValue();

                    List<NordpoolEntity> eligiblePrices = dailyPrices.stream()
                            .filter(p -> {
                                BigDecimal priceSnt = p.getPriceFi()
                                        .multiply(BigDecimal.valueOf(0.1))
                                        .multiply(taxMultiplier);
                                return priceSnt.compareTo(maxPriceSnt) <= 0;
                            })
                            .sorted(Comparator.comparing(p ->
                                    p.getPriceFi().multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier)))
                            .toList();

                    int accumulatedMinutes = 0;

                    for (NordpoolEntity priceEntry : eligiblePrices) {
                        if (accumulatedMinutes >= dailyOnMinutes) break;

                        BigDecimal priceSnt = priceEntry.getPriceFi()
                                .multiply(BigDecimal.valueOf(0.1))
                                .multiply(taxMultiplier);

                        int periodMinutes = (int) Duration.between(priceEntry.getDeliveryStart(), priceEntry.getDeliveryEnd()).toMinutes();
                        int minutesLeft = dailyOnMinutes - accumulatedMinutes;
                        int minutesToUse = Math.min(periodMinutes, minutesLeft);

                        minutesToUse = (minutesToUse / 15) * 15;

                        if (minutesToUse <= 0) continue;

                        Instant end = priceEntry.getDeliveryStart().plus(Duration.ofMinutes(minutesToUse));

                        ControlTableEntity controlEntry = ControlTableEntity.builder()
                                .control(control)
                                .startTime(priceEntry.getDeliveryStart())
                                .endTime(end)
                                .priceSnt(priceSnt)
                                .status(status)
                                .build();

                        controlTableRepository.save(controlEntry);

                        accumulatedMinutes += minutesToUse;
                    }
                }

            } else if (controlMode.equals(ControlMode.MANUAL)) {
                for (NordpoolEntity priceEntry : prices) {
                    BigDecimal priceSnt = priceEntry.getPriceFi().multiply(BigDecimal.valueOf(0.1)).multiply(taxMultiplier);
                    ControlTableEntity entry = ControlTableEntity.builder()
                            .control(control)
                            .startTime(priceEntry.getDeliveryStart())
                            .endTime(priceEntry.getDeliveryEnd())
                            .priceSnt(priceSnt)
                            .status(status)
                            .build();
                    controlTableRepository.save(entry);
                }
            }

        }
    }

}

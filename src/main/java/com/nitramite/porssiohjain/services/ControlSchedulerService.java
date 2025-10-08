package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import com.nitramite.porssiohjain.entity.NordpoolEntity;
import com.nitramite.porssiohjain.entity.Status;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.ControlTableRepository;
import com.nitramite.porssiohjain.entity.repository.NordpoolRepository;
import com.nitramite.porssiohjain.services.models.ControlTableResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ControlSchedulerService {

    private final NordpoolRepository nordpoolRepository;
    private final ControlRepository controlRepository;
    private final ControlTableRepository controlTableRepository;

    public List<ControlTableResponse> findByControlId(Long controlId) {
        return controlTableRepository.findByControlId(controlId).stream()
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
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        generateInternal(List.of(control), startOfDay, endOfDay, Status.FINAL);
    }

    @Transactional
    public void generateForAllControls() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        List<ControlEntity> controls = controlRepository.findAll();
        generateInternal(controls, startOfDay, endOfDay, Status.FINAL);
    }

    @Transactional
    public void generatePlannedForTomorrow() {
        Instant startOfTomorrow = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
        Instant endOfTomorrow = startOfTomorrow.plus(1, ChronoUnit.DAYS);

        List<ControlEntity> controls = controlRepository.findAll();
        generateInternal(controls, startOfTomorrow, endOfTomorrow, Status.PLANNED);
    }

    private void generateInternal(
            List<ControlEntity> controls,
            Instant startTime,
            Instant endTime,
            Status status
    ) {
        // 1. Get Nordpool prices for the given period
        List<NordpoolEntity> prices = nordpoolRepository.findByDeliveryStartBetween(startTime, endTime);

        // 2. For each control, evaluate prices
        for (ControlEntity control : controls) {
            for (NordpoolEntity priceEntry : prices) {

                // Convert €/MWh -> snt/kWh
                BigDecimal priceSnt = priceEntry.getPriceFi().multiply(BigDecimal.valueOf(0.1));

                // Compare against maxPriceSnt
                if (priceSnt.compareTo(control.getMaxPriceSnt()) <= 0) {
                    boolean exists = controlTableRepository.existsByControlAndStartTimeAndEndTime(
                            control,
                            priceEntry.getDeliveryStart(),
                            priceEntry.getDeliveryEnd()
                    );

                    if (!exists) {
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

}

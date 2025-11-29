package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import com.nitramite.porssiohjain.entity.repository.FingridDataRepository;
import com.nitramite.porssiohjain.services.models.FingridWindForecastResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FingridService {

    private final FingridDataRepository fingridDataRepository;

    public List<FingridWindForecastResponse> getFingridWindForecastData() {
        Instant now = Instant.now();

        List<FingridDataEntity> forecastEntities = fingridDataRepository.findByDatasetIdAndStartTimeAfter(245, now);

        return forecastEntities.stream()
                .map(n -> FingridWindForecastResponse.builder()
                        .startTime(n.getStartTime())
                        .endTime(n.getEndTime())
                        .value(n.getValue())
                        .build())
                .toList();
    }

}

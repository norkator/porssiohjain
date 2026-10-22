package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.WaterMeasurementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterMeasurementRepository extends JpaRepository<WaterMeasurementEntity, Long> {
    List<WaterMeasurementEntity> findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(Long meterId, Long accountId);
}

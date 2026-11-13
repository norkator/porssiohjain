package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.WaterMeasurementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterMeasurementRepository extends JpaRepository<WaterMeasurementEntity, Long> {
    List<WaterMeasurementEntity> findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(Long meterId, Long accountId);
    java.util.Optional<WaterMeasurementEntity> findFirstByWaterMeterIdAndWaterMeterAccountIdAndConsumptionDateIsNotNullAndConsumptionCubicMetersIsNotNullOrderByConsumptionDateDescIdDesc(
            Long meterId, Long accountId);

    @org.springframework.data.jpa.repository.Query("""
            select w from WaterMeasurementEntity w
            where w.waterMeter.id = :meterId and w.waterMeter.account.id = :accountId
            and ((w.consumptionDate is null and w.observedAt >= :from and w.observedAt < :to)
              or (w.consumptionDate >= :fromDate and w.consumptionDate < :toDate))
            order by w.observedAt, w.id
            """)
    List<WaterMeasurementEntity> findWidgetHistory(Long meterId, Long accountId, java.time.Instant from,
            java.time.Instant to, java.time.LocalDate fromDate, java.time.LocalDate toDate);
}

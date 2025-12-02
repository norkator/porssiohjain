package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.FingridDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FingridDataRepository extends JpaRepository<FingridDataEntity, Long> {

    List<FingridDataEntity> findByDatasetIdAndStartTimeAfter(Integer datasetId, Instant startTime);

    boolean existsByDatasetIdAndStartTimeBetween(Integer datasetId, Instant start, Instant end);

}
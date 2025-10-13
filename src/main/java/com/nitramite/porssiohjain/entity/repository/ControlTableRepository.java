package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ControlTableRepository extends JpaRepository<ControlTableEntity, Long> {

    boolean existsByControlAndStartTimeAndEndTime(
            ControlEntity control, Instant start, Instant end
    );

    Optional<ControlTableEntity> findByControlAndStartTimeAndEndTime(
            ControlEntity control,
            Instant startTime,
            Instant endTime
    );

    List<ControlTableEntity> findByControlId(Long controlId);

    void deleteByControlAndStartTimeBetween(ControlEntity control, Instant startTime, Instant endTime);

    // List<ControlTableEntity> findByControlIdAndStartTimeAfter(Long controlId, Instant startTime);

    List<ControlTableEntity> findByControlIdAndStartTimeAfterOrderByStartTimeAsc(Long controlId, Instant startTime);

}
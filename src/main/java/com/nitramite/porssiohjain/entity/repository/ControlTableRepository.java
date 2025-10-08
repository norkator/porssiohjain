package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.ControlTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ControlTableRepository extends JpaRepository<ControlTableEntity, Long> {

    boolean existsByControlAndStartTimeAndEndTime(ControlEntity control, Instant start, Instant end);

    List<ControlTableEntity> findByControlId(Long controlId);

}
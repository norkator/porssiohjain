package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionHistoryRepository extends JpaRepository<ProductionHistoryEntity, Long> {

    List<ProductionHistoryEntity> findByProductionSourceIdOrderByMeasuredAtDesc(Long sourceId);

}
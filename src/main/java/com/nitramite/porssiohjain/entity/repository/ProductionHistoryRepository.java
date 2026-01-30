package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductionHistoryRepository extends JpaRepository<ProductionHistoryEntity, Long> {

    List<ProductionHistoryEntity> findAllByProductionSource(ProductionSourceEntity source);

    @Modifying
    @Query("""
                DELETE FROM ProductionHistoryEntity ph
                WHERE ph.createdAt < :cutoff
            """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductionHistoryRepository extends JpaRepository<ProductionHistoryEntity, Long> {

    @Query("""
                SELECT ph
                FROM ProductionHistoryEntity ph
                WHERE ph.productionSource.id = :sourceId
                  AND ph.createdAt >= :from
                ORDER BY ph.createdAt ASC
            """)
    List<ProductionHistoryEntity> findLastHoursBySourceId(
            @Param("sourceId") Long sourceId,
            @Param("from") Instant from
    );

    default List<ProductionHistoryEntity> findLastHoursBySourceId(Long sourceId, int hours) {
        Instant from = Instant.now().minusSeconds(hours * 3600L);
        return findLastHoursBySourceId(sourceId, from);
    }

}
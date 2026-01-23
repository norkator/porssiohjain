package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PowerLimitHistoryRepository extends JpaRepository<PowerLimitHistoryEntity, Long> {

    @Query("""
                SELECT h
                FROM PowerLimitHistoryEntity h
                WHERE h.powerLimit = :powerLimit
                  AND h.createdAt >= :from
                  AND h.createdAt < :to
                ORDER BY h.createdAt DESC
            """)
    Optional<PowerLimitHistoryEntity> findForMinute(
            @Param("powerLimit") PowerLimitEntity powerLimit,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Modifying
    @Query("""
                DELETE FROM PowerLimitHistoryEntity h
                WHERE h.createdAt < :cutoff
            """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
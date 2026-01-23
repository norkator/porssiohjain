package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface PowerLimitHistoryRepository extends JpaRepository<PowerLimitHistoryEntity, Long> {

    @Modifying
    @Query("""
                DELETE FROM PowerLimitHistoryEntity h
                WHERE h.createdAt < :cutoff
            """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
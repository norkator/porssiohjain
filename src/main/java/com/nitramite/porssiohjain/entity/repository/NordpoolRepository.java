package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.NordpoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NordpoolRepository extends JpaRepository<NordpoolEntity, Long> {

    List<NordpoolEntity> findByDeliveryStartBetween(Instant start, Instant end);

    @Query("SELECT n FROM NordpoolEntity n " +
            "WHERE n.deliveryStart >= :start AND n.deliveryStart <= :end " +
            "ORDER BY n.deliveryStart ASC")
    List<NordpoolEntity> findPricesBetween(@Param("start") Instant start, @Param("end") Instant end);

    boolean existsByDeliveryStartBetween(Instant start, Instant end);

    void deleteByDeliveryStartBefore(Instant cutoff);

}
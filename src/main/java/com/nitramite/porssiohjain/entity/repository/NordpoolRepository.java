package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.NordpoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NordpoolRepository extends JpaRepository<NordpoolEntity, Long> {

    List<NordpoolEntity> findByDeliveryStartBetween(Instant start, Instant end);

}
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ProductionSourceDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionSourceDeviceRepository extends JpaRepository<ProductionSourceDeviceEntity, Long> {

    List<ProductionSourceDeviceEntity> findByProductionSourceId(Long productionSourceId);

    void deleteByIdAndProductionSourceId(Long id, Long productionSourceId);

    boolean existsByProductionSourceIdAndDeviceIdAndDeviceChannel(
            Long productionSourceId,
            Long deviceId,
            Integer deviceChannel
    );

}
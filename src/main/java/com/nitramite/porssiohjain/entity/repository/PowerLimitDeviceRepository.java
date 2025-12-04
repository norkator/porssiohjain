package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.PowerLimitDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PowerLimitDeviceRepository extends JpaRepository<PowerLimitDeviceEntity, Long> {

    List<PowerLimitDeviceEntity> findByPowerLimitId(Long powerLimitId);

    @Query("select d.device.id from PowerLimitDeviceEntity d where d.powerLimit.id = :powerLimitId")
    List<Long> findDeviceIdsByPowerLimitId(Long powerLimitId);

}
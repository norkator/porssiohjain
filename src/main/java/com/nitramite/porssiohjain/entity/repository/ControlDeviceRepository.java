package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlDeviceRepository extends JpaRepository<ControlDeviceEntity, Long> {

    boolean existsByControlIdAndDeviceIdAndDeviceChannel(Long controlId, Long deviceId, Integer deviceChannel);

}
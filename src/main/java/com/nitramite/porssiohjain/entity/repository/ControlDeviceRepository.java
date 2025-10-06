package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ControlDeviceRepository extends JpaRepository<ControlDeviceEntity, Long> {

    boolean existsByControlIdAndDeviceIdAndDeviceChannel(Long controlId, Long deviceId, Integer deviceChannel);

    List<ControlDeviceEntity> findByDevice(DeviceEntity device);

}
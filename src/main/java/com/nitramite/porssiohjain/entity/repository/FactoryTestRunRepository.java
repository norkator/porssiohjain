package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.FactoryTestRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FactoryTestRunRepository extends JpaRepository<FactoryTestRunEntity, Long> {

    List<FactoryTestRunEntity> findByFactoryDeviceOrderByStartedAtDesc(DeviceEntity factoryDevice);
}

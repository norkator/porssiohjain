package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.OtaDeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtaDeploymentRepository extends JpaRepository<OtaDeploymentEntity, Long> {

    List<OtaDeploymentEntity> findByFactoryDeviceOrderByRequestedAtDesc(DeviceEntity factoryDevice);
}

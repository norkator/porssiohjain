package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.FactoryDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FactoryDeviceRepository extends JpaRepository<FactoryDeviceEntity, Long> {

    List<FactoryDeviceEntity> findAllByOrderByIdDesc();

    Optional<FactoryDeviceEntity> findBySerialNumber(String serialNumber);

    Optional<FactoryDeviceEntity> findByMqttUsername(String mqttUsername);

    Optional<FactoryDeviceEntity> findByMqttTopicRoot(String mqttTopicRoot);

    Optional<FactoryDeviceEntity> findByClaimCode(String claimCode);
}

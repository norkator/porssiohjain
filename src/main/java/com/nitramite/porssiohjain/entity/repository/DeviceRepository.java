/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    List<DeviceEntity> findByAccountId(Long accountId);

    List<DeviceEntity> findByAccountIdOrderByIdAsc(Long accountId);

    long countByAccountId(Long accountId);

    Optional<DeviceEntity> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = "account")
    Optional<DeviceEntity> findWithAccountByUuid(UUID uuid);

    Optional<DeviceEntity> findByIdAndAccount(Long id, AccountEntity account);

    @EntityGraph(attributePaths = "account")
    Optional<DeviceEntity> findWithAccountById(Long id);

    Optional<DeviceEntity> findByMqttUsername(String mqttUsername);

    List<DeviceEntity> findBySerialNumberIsNotNullOrderByIdDesc();

    Optional<DeviceEntity> findBySerialNumber(String serialNumber);

    Optional<DeviceEntity> findByClaimCode(String claimCode);

    Optional<DeviceEntity> findByUuidAndSerialNumberIsNotNull(UUID uuid);

    List<DeviceEntity> findByFactoryDeviceStatus(FactoryDeviceStatus factoryDeviceStatus);

    List<DeviceEntity> findByApiOnlineTrue();

    @EntityGraph(attributePaths = "account")
    List<DeviceEntity> findWithAccountByApiOnlineTrue();

    List<DeviceEntity> findByApiOnlineTrueAndLastCommunicationBefore(Instant threshold);

    List<DeviceEntity> findByMqttOnlineTrueAndLastCommunicationBefore(Instant threshold);

    @EntityGraph(attributePaths = "account")
    List<DeviceEntity> findWithAccountByMqttOnlineTrueAndLastCommunicationBefore(Instant threshold);

    List<DeviceEntity> findByMqttOnlineTrue();

}

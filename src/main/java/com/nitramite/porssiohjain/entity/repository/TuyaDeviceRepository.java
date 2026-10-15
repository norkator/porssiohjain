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

import com.nitramite.porssiohjain.entity.TuyaDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TuyaDeviceRepository extends JpaRepository<TuyaDeviceEntity, Long> {
    Optional<TuyaDeviceEntity> findByDeviceIdAndConnectionAccountId(Long deviceId, Long accountId);
    boolean existsByConnectionIdAndTuyaDeviceId(Long connectionId, String tuyaDeviceId);
    boolean existsByConnectionId(Long connectionId);

    List<TuyaDeviceEntity> findByConnectionId(Long connectionId);

    @Query("""
            select t.device.id as deviceId, t.connection.account.id as accountId from TuyaDeviceEntity t
            where t.device.enabled = true and t.device.devicePlatform = com.nitramite.porssiohjain.entity.enums.DevicePlatform.TUYA_SMART
              and t.connection.sessionData is not null
            """)
    List<PollTarget> findPollTargets();

    interface PollTarget {
        Long getDeviceId();
        Long getAccountId();
    }
}

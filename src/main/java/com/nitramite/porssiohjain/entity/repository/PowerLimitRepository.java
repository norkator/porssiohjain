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

import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PowerLimitRepository extends JpaRepository<PowerLimitEntity, Long> {

    List<PowerLimitEntity> findByAccountId(Long accountId);

    List<PowerLimitEntity> findByAccountIdAndSiteId(Long accountId, Long siteId);

    Optional<PowerLimitEntity> findByAccountIdAndId(Long accountId, Long powerLimitId);

    Optional<PowerLimitEntity> findByUuid(UUID uuid);

}
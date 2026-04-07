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

import com.nitramite.porssiohjain.entity.enums.ProductionApiType;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionSourceRepository extends JpaRepository<ProductionSourceEntity, Long> {

    List<ProductionSourceEntity> findByEnabledTrueAndApiType(ProductionApiType apiType);

    List<ProductionSourceEntity> findByAccountId(Long accountId);

    long countByAccountId(Long accountId);

    Optional<ProductionSourceEntity> findByIdAndAccountId(Long id, Long accountId);

    Optional<ProductionSourceEntity> findByUuidAndAccountId(UUID uuid, Long accountId);

}

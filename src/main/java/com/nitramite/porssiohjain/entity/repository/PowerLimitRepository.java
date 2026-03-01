/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
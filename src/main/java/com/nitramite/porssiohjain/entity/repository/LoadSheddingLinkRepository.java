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

import com.nitramite.porssiohjain.entity.LoadSheddingLinkEntity;
import com.nitramite.porssiohjain.entity.LoadSheddingNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoadSheddingLinkRepository extends JpaRepository<LoadSheddingLinkEntity, Long> {

    List<LoadSheddingLinkEntity> findByAccountIdOrderByIdAsc(Long accountId);

    Optional<LoadSheddingLinkEntity> findByIdAndAccountId(Long id, Long accountId);

    List<LoadSheddingLinkEntity> findBySourceNode(LoadSheddingNodeEntity sourceNode);

    List<LoadSheddingLinkEntity> findByTargetNode(LoadSheddingNodeEntity targetNode);

}

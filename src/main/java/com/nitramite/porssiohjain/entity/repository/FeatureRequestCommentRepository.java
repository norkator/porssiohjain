/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.FeatureRequestCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeatureRequestCommentRepository extends JpaRepository<FeatureRequestCommentEntity, Long> {
    List<FeatureRequestCommentEntity> findByFeatureRequestIdOrderByIdAsc(Long requestId);
    boolean existsByIdAndFeatureRequestId(Long id, Long requestId);
}

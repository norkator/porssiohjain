/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 * Licensed under the Pörssiohjain Personal Use License v1.0.
 */
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureRequestRepository extends JpaRepository<FeatureRequestEntity, Long> {
}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */

package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ServiceNoticeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceNoticeRepository extends JpaRepository<ServiceNoticeEntity, Integer> {
}

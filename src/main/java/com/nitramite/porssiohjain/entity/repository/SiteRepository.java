package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.SiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

    List<SiteEntity> findByAccountId(Long accountId);

}

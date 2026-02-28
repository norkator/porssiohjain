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
package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.OtaReleaseEntity;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaReleaseRepository extends JpaRepository<OtaReleaseEntity, Long> {

    List<OtaReleaseEntity> findAllByOrderByIdDesc();

    Optional<OtaReleaseEntity> findByPlatformAndProductModelAndVersion(
            DevicePlatform platform,
            String productModel,
            String version
    );
}

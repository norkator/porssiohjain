package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.WindNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WindNotificationRepository extends JpaRepository<WindNotificationEntity, Long> {
    List<WindNotificationEntity> findByAccountIdOrderByIdAsc(Long accountId);
    List<WindNotificationEntity> findByEnabledTrueOrderByIdAsc();
    Optional<WindNotificationEntity> findByIdAndAccountId(Long id, Long accountId);
}

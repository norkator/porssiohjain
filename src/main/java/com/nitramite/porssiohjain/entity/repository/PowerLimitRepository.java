package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.PowerLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PowerLimitRepository extends JpaRepository<PowerLimitEntity, Long> {

    List<PowerLimitEntity> findByAccountId(Long accountId);

}
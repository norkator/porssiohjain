package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ProductionApiType;
import com.nitramite.porssiohjain.entity.ProductionSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionSourceRepository extends JpaRepository<ProductionSourceEntity, Long> {

    List<ProductionSourceEntity> findByEnabledTrueAndApiType(ProductionApiType apiType);

    List<ProductionSourceEntity> findByAccountId(Long accountId);

}
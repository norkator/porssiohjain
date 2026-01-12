package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ElectricityContractEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElectricityContractRepository extends JpaRepository<ElectricityContractEntity, Long> {

    List<ElectricityContractEntity> findByAccountId(Long accountId);

}

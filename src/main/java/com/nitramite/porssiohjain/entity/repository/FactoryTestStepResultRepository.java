package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.FactoryTestRunEntity;
import com.nitramite.porssiohjain.entity.FactoryTestStepResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FactoryTestStepResultRepository extends JpaRepository<FactoryTestStepResultEntity, Long> {

    List<FactoryTestStepResultEntity> findByFactoryTestRunOrderByCreatedAtAsc(FactoryTestRunEntity factoryTestRun);
}

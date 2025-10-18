package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControlRepository extends JpaRepository<ControlEntity, Long> {

    List<ControlEntity> findAllByAccountOrderByIdAsc(AccountEntity account);

    Optional<ControlEntity> findFirstByAccountId(Long accountId);

}
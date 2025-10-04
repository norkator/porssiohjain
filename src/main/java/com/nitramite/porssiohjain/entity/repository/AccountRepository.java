package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByUuid(UUID uuid);

}
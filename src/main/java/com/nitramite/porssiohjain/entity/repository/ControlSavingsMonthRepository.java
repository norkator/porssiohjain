package com.nitramite.porssiohjain.entity.repository;

import com.nitramite.porssiohjain.entity.ControlSavingsMonthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ControlSavingsMonthRepository extends JpaRepository<ControlSavingsMonthEntity, Long> {
    Optional<ControlSavingsMonthEntity> findByAccountIdAndMonthStartAndTimezone(
            Long accountId, LocalDate monthStart, String timezone);

    @Query("select distinct m.timezone from ControlSavingsMonthEntity m where m.account.id = :accountId order by m.timezone")
    List<String> findTimezonesByAccountId(Long accountId);
}

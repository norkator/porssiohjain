package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.mqtt.MqttService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseRetentionTest {
    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");
    @MockitoBean MqttService mqttService;
    @Autowired EntityManager em;
    @Autowired ControlSavingsService savings;
    @Autowired UserTokenCleanupService tokens;
    @Autowired ControlSavingsMonthRepository months;
    @Autowired ControlTableRepository periods;
    @Autowired QrLoginChallengeRepository challenges;
    @Autowired PushNotificationTokenRepository pushTokens;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void qrCleanupIncludesAbandonedChallengesAndPreservesCutoffAndLiveChallenges() {
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(1));
        var abandoned = challenge(QrLoginStatus.PENDING, cutoff.minusSeconds(1));
        var consumed = challenge(QrLoginStatus.CONSUMED, cutoff.minusSeconds(1));
        var cancelled = challenge(QrLoginStatus.CANCELLED, cutoff.minusSeconds(1));
        var boundary = challenge(QrLoginStatus.EXPIRED, cutoff);
        var approved = challenge(QrLoginStatus.APPROVED, now.plusSeconds(120));
        assertThat(tokens.cleanupQrChallenges(now)).isEqualTo(3);
        assertThat(challenges.existsById(abandoned.getId())).isFalse();
        assertThat(challenges.existsById(consumed.getId())).isFalse();
        assertThat(challenges.existsById(cancelled.getId())).isFalse();
        assertThat(challenges.existsById(boundary.getId())).isTrue();
        assertThat(challenges.existsById(approved.getId())).isTrue();
    }

    @Test
    void pushCleanupOnlyRemovesRegistrationsInvalidatedMoreThanThirtyDaysAgo() {
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(30));
        var account = account();
        var old = pushToken(account, cutoff.minusSeconds(1));
        var boundary = pushToken(account, cutoff);
        var recent = pushToken(account, now.minusSeconds(1));
        var active = pushToken(account, null);
        assertThat(tokens.cleanupPushTokens(now)).isEqualTo(1);
        assertThat(pushTokens.existsById(old.getId())).isFalse();
        assertThat(pushTokens.existsById(boundary.getId())).isTrue();
        assertThat(pushTokens.existsById(recent.getId())).isTrue();
        assertThat(pushTokens.existsById(active.getId())).isTrue();
    }

    @Test
    void completedMonthSurvivesSourceDeletionAndChangesToCurrentControls() {
        var control = control(account());
        Instant march = start("2026-03-01");
        Instant april = start("2026-04-01");
        prices(LocalDate.parse("2026-03-01"));
        var old = period(control, march, march.plusSeconds(3600));
        period(control, april, april.plusSeconds(3600));
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        var saved = savings.getSavings(control.getAccount().getId(), march, april, HELSINKI.getId(), now);
        assertThat(saved.getEstimatedUsageKwh()).isEqualByComparingTo("1.000");
        assertThat(saved.getEstimatedSavingsEur()).isEqualByComparingTo("0.05");
        assertThat(saved.getScheduleEntryCount()).isEqualTo(1); // April midnight is excluded.
        long monthId = months.findByAccountIdAndMonthStartAndTimezone(control.getAccount().getId(),
                LocalDate.parse("2026-03-01"), HELSINKI.getId()).orElseThrow().getId();
        periods.delete(old);
        control.setName("Renamed later");
        control.getControlDevices().iterator().next().setEstimatedPowerKw(new BigDecimal("9"));
        em.flush();
        em.clear();
        // A different range bypasses the old response cache and combines two stored monthly reports.
        var history = savings.getSavings(control.getAccount().getId(), start("2026-02-01"), april,
                HELSINKI.getId(), now);
        assertThat(history.getEstimatedUsageKwh()).isEqualByComparingTo("1.000");
        assertThat(history.getEstimatedSavingsEur()).isEqualByComparingTo("0.05");
        assertThat(history.getControls().getFirst().getControlName()).isEqualTo("Test control");
        assertThat(months.findById(monthId)).isPresent();
    }

    @Test
    void currentMonthStillUsesChangingSourcePeriodsWithoutSavingAMonthlyReport() {
        var control = control(account());
        Instant may = start("2026-05-01");
        prices(LocalDate.parse("2026-05-01"));
        period(control, may, may.plusSeconds(3600));
        var first = savings.getSavings(control.getAccount().getId(), may, may.plusSeconds(7200),
                HELSINKI.getId(), Instant.parse("2026-05-20T12:00:00Z"));
        period(control, may.plusSeconds(3600), may.plusSeconds(7200));
        var second = savings.getSavings(control.getAccount().getId(), may, may.plusSeconds(10800),
                HELSINKI.getId(), Instant.parse("2026-05-20T12:00:00Z"));
        assertThat(first.getEstimatedUsageKwh()).isEqualByComparingTo("1.000");
        assertThat(second.getEstimatedUsageKwh()).isEqualByComparingTo("2.000");
        assertThat(months.findByAccountIdAndMonthStartAndTimezone(control.getAccount().getId(),
                LocalDate.parse("2026-05-01"), HELSINKI.getId())).isEmpty();
    }

    @Test
    void catchupArchivesOlderMonthsBeforeDeletingOnlyPeriodsBeyondFortyFiveDays() {
        var control = control(account());
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(45));
        Instant march = start("2026-03-01");
        prices(LocalDate.parse("2026-03-01"));
        prices(LocalDate.parse("2026-04-20"));
        prices(cutoff.atZone(HELSINKI).toLocalDate());
        var old = period(control, march, march.plusSeconds(3600));
        var recent = period(control, start("2026-04-20"), start("2026-04-20").plusSeconds(3600));
        var boundary = period(control, cutoff.minusSeconds(3600), cutoff);
        em.flush();
        assertThat(savings.archiveAndCleanupAccount(control.getAccount().getId(), now)).isEqualTo(1);
        assertThat(periods.existsById(old.getId())).isFalse();
        assertThat(periods.existsById(recent.getId())).isTrue();
        assertThat(periods.existsById(boundary.getId())).isTrue();
        var marchReport = savings.getSavings(control.getAccount().getId(), march, start("2026-04-01"), HELSINKI.getId(), now);
        assertThat(marchReport.getEstimatedUsageKwh()).isEqualByComparingTo("1.000");
        assertThat(savings.archiveAndCleanupAccount(control.getAccount().getId(), now)).isZero();
        assertThatThrownBy(() -> savings.getSavings(control.getAccount().getId(), march, march.plusSeconds(86400),
                HELSINKI.getId(), now)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(410));
    }

    @Test
    void incompleteBaselinePricesPreventFinalizationAndSourceDeletion() {
        var control = control(account());
        var old = period(control, start("2026-03-01"), start("2026-03-01").plusSeconds(3600));
        em.flush();
        assertThatThrownBy(() -> savings.archiveAndCleanupAccount(control.getAccount().getId(),
                Instant.parse("2026-05-20T12:00:00Z")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("incomplete Nord Pool prices");
        assertThat(periods.existsById(old.getId())).isTrue();
        assertThat(control.getAccount().getControlHistoryDeletedBefore()).isNull();
    }

    @Test
    void monthlySavingsUseActualTwentyThreeAndTwentyFiveHourLocalDays() {
        var control = control(account());
        for (String day : new String[]{"2026-03-29", "2026-10-25"}) {
            LocalDate date = LocalDate.parse(day);
            prices(date);
            period(control, date.atStartOfDay(HELSINKI).toInstant(), date.plusDays(1).atStartOfDay(HELSINKI).toInstant());
        }
        Instant now = Instant.parse("2026-11-12T12:00:00Z");
        var spring = savings.getSavings(control.getAccount().getId(), start("2026-03-01"), start("2026-04-01"), HELSINKI.getId(), now);
        var autumn = savings.getSavings(control.getAccount().getId(), start("2026-10-01"), start("2026-11-01"), HELSINKI.getId(), now);
        assertThat(spring.getEstimatedUsageKwh()).isEqualByComparingTo("23.000");
        assertThat(autumn.getEstimatedUsageKwh()).isEqualByComparingTo("25.000");
        assertThat(spring.getFrom()).isEqualTo(Instant.parse("2026-02-28T22:00:00Z"));
        assertThat(spring.getTo()).isEqualTo(Instant.parse("2026-03-31T21:00:00Z"));
    }

    @Test
    void snapshotsAreIsolatedByAccount() {
        var first = control(account());
        var second = control(account());
        prices(LocalDate.parse("2026-03-01"));
        period(first, start("2026-03-01"), start("2026-03-01").plusSeconds(3600));
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        var other = savings.getSavings(second.getAccount().getId(), start("2026-03-01"), start("2026-04-01"), HELSINKI.getId(), now);
        var own = savings.getSavings(first.getAccount().getId(), start("2026-03-01"), start("2026-04-01"), HELSINKI.getId(), now);
        assertThat(other.getEstimatedUsageKwh()).isEqualByComparingTo("0");
        assertThat(own.getEstimatedUsageKwh()).isEqualByComparingTo("1");
    }

    @Test
    void savedAccountSavingsSurviveDeletionOfTheOriginalControl() {
        var control = control(account());
        Long accountId = control.getAccount().getId();
        prices(LocalDate.parse("2026-03-01"));
        period(control, start("2026-03-01"), start("2026-03-01").plusSeconds(3600));
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        assertThat(savings.archiveAndCleanupAccount(accountId, now)).isEqualTo(1);
        Long controlId = control.getId();
        em.flush();
        em.clear(); // The daily job and a later user deletion run in separate persistence contexts.
        em.remove(em.find(ControlEntity.class, controlId));
        em.flush();
        em.clear();
        var history = savings.getSavings(accountId, start("2026-03-01"), start("2026-04-01"), null, now);
        assertThat(history.getTimezone()).isEqualTo(HELSINKI.getId());
        assertThat(history.getEstimatedSavingsEur()).isEqualByComparingTo("0.05");
        assertThat(history.getControls().getFirst().getControlName()).isEqualTo("Test control");
    }

    @Test
    void oneReportCanCombineArchivedMonthsAndLiveCurrentMonth() {
        var control = control(account());
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        prices(LocalDate.parse("2026-03-01"));
        prices(LocalDate.parse("2026-05-01"));
        period(control, start("2026-03-01"), start("2026-03-01").plusSeconds(3600));
        period(control, start("2026-05-01"), start("2026-05-01").plusSeconds(3600));
        savings.archiveAndCleanupAccount(control.getAccount().getId(), now);
        var combined = savings.getSavings(control.getAccount().getId(), start("2026-03-01"), now, HELSINKI.getId(), now);
        assertThat(combined.getEstimatedUsageKwh()).isEqualByComparingTo("2.000");
        assertThat(combined.getEstimatedSavingsEur()).isEqualByComparingTo("0.10");
        assertThat(combined.getEstimatedPowerKw()).isEqualByComparingTo("1.000");
        assertThat(combined.getControlCount()).isEqualTo(1);
        assertThat(months.findByAccountIdAndMonthStartAndTimezone(control.getAccount().getId(),
                LocalDate.parse("2026-05-01"), HELSINKI.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedFinalizationRollsBackEarlierMonthInsertsAndRetainsSourcesInTheDatabase() {
        var transaction = new TransactionTemplate(transactionManager);
        long[] ids = transaction.execute(status -> {
            var control = control(account());
            var row = period(control, start("2026-03-01"), start("2026-03-01").plusSeconds(3600));
            em.flush();
            return new long[]{control.getAccount().getId(), row.getId()};
        });
        try {
            assertThatThrownBy(() -> savings.archiveAndCleanupAccount(ids[0], Instant.parse("2026-05-20T12:00:00Z")))
                    .isInstanceOf(IllegalStateException.class);
            transaction.executeWithoutResult(status -> {
                assertThat(periods.existsById(ids[1])).isTrue();
                assertThat(months.findTimezonesByAccountId(ids[0])).isEmpty();
                assertThat(em.find(AccountEntity.class, ids[0]).getControlHistoryDeletedBefore()).isNull();
            });
        } finally {
            transaction.executeWithoutResult(status -> {
                em.createQuery("delete from ControlTableEntity row where row.control.account.id = :id").setParameter("id", ids[0]).executeUpdate();
                em.createQuery("delete from ControlDeviceEntity link where link.control.account.id = :id").setParameter("id", ids[0]).executeUpdate();
                em.createQuery("delete from ControlEntity control where control.account.id = :id").setParameter("id", ids[0]).executeUpdate();
                em.createQuery("delete from DeviceEntity device where device.account.id = :id").setParameter("id", ids[0]).executeUpdate();
                em.createQuery("delete from SiteEntity site where site.account.id = :id").setParameter("id", ids[0]).executeUpdate();
                em.createQuery("delete from AccountEntity account where account.id = :id").setParameter("id", ids[0]).executeUpdate();
            });
        }
    }

    @Test
    void newAccountsDoNotAccumulateEmptyReportsForMonthsBeforeRegistration() {
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        var account = account();
        account.setCreatedAt(now.minusSeconds(3600));
        em.createNativeQuery("update account set created_at = :createdAt where id = :id")
                .setParameter("createdAt", account.getCreatedAt()).setParameter("id", account.getId()).executeUpdate();
        control(account);
        savings.archiveAndCleanupAccount(account.getId(), now);
        var report = savings.getSavings(account.getId(), start("2026-03-01"), start("2026-04-01"), HELSINKI.getId(), now);
        assertThat(report.getEstimatedSavingsEur()).isEqualByComparingTo("0");
        assertThat(months.findTimezonesByAccountId(account.getId())).isEmpty();
    }

    private AccountEntity account() {
        var account = AccountEntity.builder().uuid(UUID.randomUUID()).secret(UUID.randomUUID().toString()).build();
        em.persist(account);
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        em.createNativeQuery("update account set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", account.getId()).executeUpdate();
        account.setCreatedAt(createdAt);
        return account;
    }

    private ControlEntity control(AccountEntity account) {
        var site = SiteEntity.builder().account(account).name("Test site").type(SiteType.HOME).build();
        em.persist(site);
        var device = DeviceEntity.builder().account(account).deviceName("Test relay").timezone(HELSINKI.getId()).build();
        em.persist(device);
        var control = ControlEntity.builder().account(account).site(site).name("Test control").timezone(HELSINKI.getId())
                .maxPriceSnt(BigDecimal.TEN).minPriceSnt(BigDecimal.ZERO).taxPercent(BigDecimal.ZERO)
                .dailyOnMinutes(60).mode(ControlMode.CHEAPEST_HOURS).controlDevices(new HashSet<>()).build();
        em.persist(control);
        var link = ControlDeviceEntity.builder().control(control).device(device).deviceChannel(0).estimatedPowerKw(BigDecimal.ONE).build();
        em.persist(link);
        control.getControlDevices().add(link);
        return control;
    }

    private ControlTableEntity period(ControlEntity control, Instant from, Instant to) {
        var row = ControlTableEntity.builder().control(control).startTime(from).endTime(to)
                .priceSnt(new BigDecimal("5")).status(Status.FINAL).build();
        em.persist(row);
        return row;
    }

    private void prices(LocalDate day) {
        Instant end = day.plusDays(1).atStartOfDay(HELSINKI).toInstant();
        for (Instant cursor = day.atStartOfDay(HELSINKI).toInstant(); cursor.isBefore(end); cursor = cursor.plusSeconds(3600)) {
            var price = new NordpoolEntity();
            price.setMarketIndexName("FI");
            price.setDeliveryStart(cursor);
            price.setDeliveryEnd(cursor.plusSeconds(3600));
            price.setPriceFi(new BigDecimal("100"));
            em.persist(price);
        }
    }

    private QrLoginChallengeEntity challenge(QrLoginStatus status, Instant expiry) {
        return challenges.saveAndFlush(QrLoginChallengeEntity.builder().challengeId(UUID.randomUUID())
                .browserSecretHash("test-hash").scanSecretHash("test-hash").status(status).expiresAt(expiry).build());
    }

    private PushNotificationTokenEntity pushToken(AccountEntity account, Instant invalidatedAt) {
        return pushTokens.saveAndFlush(PushNotificationTokenEntity.builder().account(account).platform("android")
                .token(UUID.randomUUID().toString()).lastSeenAt(Instant.parse("2020-01-01T00:00:00Z"))
                .invalidatedAt(invalidatedAt).build());
    }

    private Instant start(String date) {
        return LocalDate.parse(date).atStartOfDay(HELSINKI).toInstant();
    }
}

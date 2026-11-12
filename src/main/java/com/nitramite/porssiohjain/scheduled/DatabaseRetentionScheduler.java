package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.services.ControlSavingsService;
import com.nitramite.porssiohjain.services.UserTokenCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("!test & !local")
@RequiredArgsConstructor
@Slf4j
public class DatabaseRetentionScheduler {
    private final ControlRepository controls;
    private final ControlSavingsService savings;
    private final UserTokenCleanupService tokens;

    @Scheduled(cron = "0 5 12 * * *", zone = "Europe/Helsinki")
    public void cleanupQrChallenges() {
        log.info("Deleted {} expired QR login challenges", tokens.cleanupQrChallenges(Instant.now()));
    }

    @Scheduled(cron = "0 6 12 * * *", zone = "Europe/Helsinki")
    public void cleanupInvalidatedPushTokens() {
        log.info("Deleted {} invalidated push tokens", tokens.cleanupPushTokens(Instant.now()));
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Helsinki")
    public void archiveSavingsAndCleanupControlTimings() {
        Instant now = Instant.now();
        for (Long accountId : controls.findAccountIdsWithControls()) {
            try {
                int deleted = savings.archiveAndCleanupAccount(accountId, now);
                if (deleted > 0) log.info("Deleted {} archived control periods for account {}", deleted, accountId);
            } catch (RuntimeException exception) {
                // Each account has its own transaction. A failed report must not discard its source history.
                log.error("Savings finalization failed for account {}; retaining its control timings", accountId, exception);
            }
        }
    }
}

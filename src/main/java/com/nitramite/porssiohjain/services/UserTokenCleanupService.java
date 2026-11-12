package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.repository.PushNotificationTokenRepository;
import com.nitramite.porssiohjain.entity.repository.QrLoginChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserTokenCleanupService {
    private final QrLoginChallengeRepository challenges;
    private final PushNotificationTokenRepository pushTokens;

    @Transactional
    public int cleanupQrChallenges(Instant now) {
        // Covers abandoned PENDING challenges too; status is only updated when a client returns.
        return challenges.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
    }

    @Transactional
    public int cleanupPushTokens(Instant now) {
        // Active registrations are preserved regardless of their last-seen timestamp.
        return pushTokens.deleteInvalidatedBefore(now.minus(Duration.ofDays(30)));
    }
}

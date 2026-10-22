/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.entity.repository.WaterMeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WaterMeterNotificationService {
    private final WaterMeterRepository meters;
    private final PushNotificationService push;
    private final PushNotificationTokenService tokens;
    private final AccountLimitService limits;

    @Transactional(readOnly = true)
    public List<WaterMeterService.MeterReference> enabledForNotifications() {
        return meters.findEnabledForNotifications().stream()
                .map(meter -> new WaterMeterService.MeterReference(meter.getAccount().getId(), meter.getId())).toList();
    }

    /** An independent transaction and meter lock isolate accounts and prevent concurrent duplicate deliveries. */
    @Transactional
    public void sendIfDue(Long accountId, Long meterId, Instant now) {
        var meter = meters.lockByIdAndAccountId(meterId, accountId).orElse(null);
        if (meter == null || !meter.isEnabled() || !meter.isPushEnabled()
                || meter.getLatestTotalCubicMeters() == null || meter.getObservedAt() == null) return;
        var account = meter.getAccount();
        if (account.isDemo() || !account.isPushNotificationsEnabled()) return;
        ZoneId zone = ZoneId.of(meter.getPushTimezone());
        LocalDate date = now.atZone(zone).toLocalDate();
        if (meter.getPushFrequency() == WaterMeterNotificationFrequency.WEEKLY
                && date.getDayOfWeek().getValue() != meter.getPushWeekday()) return;
        Instant scheduled = date.atTime(meter.getPushTime()).atZone(zone).toInstant();
        if (now.isBefore(scheduled) || !now.isBefore(scheduled.plusSeconds(60))) return;
        if (meter.getLastPushSentAt() != null) {
            LocalDate lastDate = meter.getLastPushSentAt().atZone(zone).toLocalDate();
            if (meter.getPushFrequency() == WaterMeterNotificationFrequency.DAILY && !lastDate.isBefore(date)) return;
            if (meter.getPushFrequency() == WaterMeterNotificationFrequency.WEEKLY
                    && !lastDate.isBefore(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) return;
        }
        if (!tokens.hasActivePushToken(accountId) || !limits.tryConsumeWeeklyPushNotification(accountId, now)) return;
        // Use the cached total exactly as requested; notification delivery never polls the provider.
        if (push.sendWaterMeterNotification(account, meter, now.atZone(zone))) {
            meter.setLastPushSentAt(now);
            meters.save(meter);
        }
    }
}

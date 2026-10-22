/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.WaterMeterEntity;
import com.nitramite.porssiohjain.entity.WaterMeasurementEntity;
import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;
import com.nitramite.porssiohjain.entity.enums.WaterMeterNotificationFrequency;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.WaterMeterRepository;
import com.nitramite.porssiohjain.entity.repository.WaterMeasurementRepository;
import com.nitramite.porssiohjain.services.watermeters.WaterMeterClient;
import com.nitramite.porssiohjain.services.watermeters.WaterMeterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WaterMeterService {
    private static final ZoneId POLLING_ZONE = ZoneId.of("Europe/Helsinki");
    private final WaterMeterRepository meters;
    private final WaterMeasurementRepository measurements;
    private final AccountRepository accounts;
    private final DemoAccountGuard demoGuard;
    private final List<WaterMeterClient> clients;

    @Transactional(readOnly = true)
    public List<Meter> list(Long accountId) {
        requireAccount(accountId);
        return meters.findByAccountIdOrderByNameAscIdAsc(accountId).stream().map(this::summary).toList();
    }

    @Transactional
    public Meter save(Long accountId, Long id, Settings settings) {
        requireAccount(accountId);
        demoGuard.assertWritable(accountId);
        if (settings == null || settings.name() == null || settings.name().isBlank()
                || settings.name().strip().length() > 255) throw error("name");
        String portal = client(settings.provider()).normalizePortalUrl(settings.portalUrl());
        NotificationSettings notification = validateNotifications(settings.notifications());
        WaterMeterEntity meter = id == null ? new WaterMeterEntity() : owned(accountId, id);
        if (id == null) meter.setAccount(accounts.findById(accountId).orElseThrow(() -> error("notFound")));
        String username = credential(settings.username(), meter.getUsername());
        String password = credential(settings.password(), meter.getPassword());
        boolean changedSource = settings.provider() != meter.getProvider() || !Objects.equals(portal, meter.getPortalUrl())
                || !Objects.equals(username, meter.getUsername()) || !Objects.equals(password, meter.getPassword());
        meter.setName(settings.name().strip());
        meter.setProvider(settings.provider());
        meter.setPortalUrl(portal);
        meter.setUsername(username);
        meter.setPassword(password);
        meter.setEnabled(settings.enabled());
        meter.setPushEnabled(notification.enabled());
        meter.setPushFrequency(notification.frequency());
        meter.setPushTime(notification.time());
        meter.setPushWeekday(notification.weekday().getValue());
        meter.setPushTimezone(notification.timezone());
        if (changedSource) {
            meter.setLatestTotalCubicMeters(null);
            meter.setObservedAt(null);
            meter.setLastPollAttemptAt(null);
            meter.setLastErrorKey(null);
            meter.setLastPushSentAt(null);
        }
        return summary(meters.save(meter));
    }

    @Transactional(readOnly = true)
    public List<Measurement> history(Long accountId, Long id) {
        requireAccount(accountId);
        if (!meters.existsByIdAndAccountId(id, accountId)) throw error("notFound");
        return measurements.findByWaterMeterIdAndWaterMeterAccountIdOrderByObservedAtAscIdAsc(id, accountId).stream()
                .map(row -> new Measurement(row.getId(), row.getTotalCubicMeters(), row.getObservedAt())).toList();
    }

    @Transactional
    public void delete(Long accountId, Long id) {
        requireAccount(accountId);
        demoGuard.assertWritable(accountId);
        meters.delete(owned(accountId, id));
    }

    @Transactional(readOnly = true)
    public List<MeterReference> enabledForPolling() {
        return meters.findEnabledForPolling().stream()
                .map(meter -> new MeterReference(meter.getAccount().getId(), meter.getId())).toList();
    }

    /** The row lock also makes daily deduplication work across multiple application instances. */
    @Transactional
    public Meter pollDaily(Long accountId, Long id) {
        return poll(accountId, id, false);
    }

    @Transactional
    public Meter pollNow(Long accountId, Long id) {
        return poll(accountId, id, true);
    }

    private Meter poll(Long accountId, Long id, boolean manual) {
        requireAccount(accountId);
        demoGuard.assertWritable(accountId);
        WaterMeterEntity meter = owned(accountId, id);
        if (!meter.isEnabled()) {
            if (manual) throw error("disabled");
            return summary(meter);
        }
        Instant now = Instant.now();
        if (!manual && meter.getLastPollAttemptAt() != null
                && !meter.getLastPollAttemptAt().atZone(POLLING_ZONE).toLocalDate()
                .isBefore(now.atZone(POLLING_ZONE).toLocalDate())) return summary(meter);
        meter.setLastPollAttemptAt(now);
        try {
            var reading = client(meter.getProvider()).readLatest(meter.getPortalUrl(), meter.getUsername(), meter.getPassword());
            meter.setLatestTotalCubicMeters(reading.totalCubicMeters());
            meter.setObservedAt(reading.observedAt());
            meter.setLastErrorKey(null);
            WaterMeasurementEntity measurement = new WaterMeasurementEntity();
            measurement.setWaterMeter(meter);
            measurement.setTotalCubicMeters(reading.totalCubicMeters());
            measurement.setObservedAt(reading.observedAt());
            measurements.save(measurement);
        } catch (WaterMeterException exception) {
            // Failed polling creates no history point and preserves the last successful total and fetch time.
            meter.setLastErrorKey(exception.getMessage());
        }
        return summary(meters.save(meter));
    }

    private WaterMeterClient client(WaterMeterProvider provider) {
        return clients.stream().filter(client -> client.provider() == provider).findFirst().orElseThrow(() -> error("provider"));
    }

    private WaterMeterEntity owned(Long accountId, Long id) {
        return meters.lockByIdAndAccountId(id, accountId).orElseThrow(() -> error("notFound"));
    }

    private static void requireAccount(Long accountId) {
        if (accountId == null) throw error("notFound");
    }

    private static String credential(String value, String existing) {
        String result = value == null || value.isBlank() ? existing : value.strip();
        if (result == null || result.isBlank() || result.length() > 255
                || result.chars().anyMatch(Character::isISOControl)) throw error("credentials");
        return result;
    }

    private NotificationSettings validateNotifications(NotificationSettings value) {
        NotificationSettings settings = value == null ? NotificationSettings.defaults() : value;
        if (settings.frequency() == null || settings.time() == null || settings.weekday() == null
                || settings.time().getSecond() != 0 || settings.time().getNano() != 0) throw error("notificationSettings");
        try {
            if (settings.timezone() == null || settings.timezone().length() > 64) throw error("notificationSettings");
            ZoneId.of(settings.timezone());
        } catch (DateTimeException exception) {
            throw error("notificationSettings");
        }
        return settings;
    }

    private Meter summary(WaterMeterEntity meter) {
        return new Meter(meter.getId(), meter.getName(), meter.getPortalUrl(), meter.isEnabled(),
                meter.getLatestTotalCubicMeters(), meter.getObservedAt(), meter.getLastPollAttemptAt(), meter.getLastErrorKey(),
                meter.getProvider(), new NotificationSettings(meter.isPushEnabled(), meter.getPushFrequency(),
                meter.getPushTime(), DayOfWeek.of(meter.getPushWeekday()), meter.getPushTimezone()));
    }

    private static WaterMeterException error(String key) {
        return new WaterMeterException("waterMeters.error." + key);
    }

    /** Login credentials and session cookies are deliberately absent from UI responses. */
    public record Meter(Long id, String name, String portalUrl, boolean enabled, BigDecimal latestTotalCubicMeters,
                        Instant observedAt, Instant lastPollAttemptAt, String lastErrorKey, WaterMeterProvider provider,
                        NotificationSettings notifications) { }
    public record MeterReference(Long accountId, Long id) { }
    public record Measurement(Long id, BigDecimal totalCubicMeters, Instant observedAt) { }
    public record NotificationSettings(boolean enabled, WaterMeterNotificationFrequency frequency, LocalTime time,
                                       DayOfWeek weekday, String timezone) {
        public static NotificationSettings defaults() {
            return new NotificationSettings(false, WaterMeterNotificationFrequency.DAILY, LocalTime.of(12, 5),
                    DayOfWeek.MONDAY, "Europe/Helsinki");
        }
    }
    public record Settings(String name, WaterMeterProvider provider, String portalUrl, String username, String password,
                           boolean enabled, NotificationSettings notifications) {
        @Override
        public String toString() {
            return "WaterMeterSettings[credentials=redacted]";
        }
    }
}

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.WindNotificationRuleType;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor @Transactional
public class WindNotificationService {
    private final WindNotificationRepository repository;
    private final AccountRepository accountRepository;
    private final FingridService fingridService;
    private final EmailService emailService;
    private final PushNotificationService pushService;
    private final PushNotificationTokenService tokenService;
    private final AccountLimitService limitService;
    private final DemoAccountGuard demoGuard;

    public List<WindNotificationResponse> list(Long accountId) { return repository.findByAccountIdOrderByIdAsc(accountId).stream().map(this::response).toList(); }
    public WindNotificationResponse create(Long accountId, WindNotificationRequest request) {
        demoGuard.assertWritable(accountId); validate(request);
        AccountEntity account = accountRepository.findById(accountId).orElseThrow(() -> new EntityNotFoundException("Account not found"));
        return response(repository.save(WindNotificationEntity.builder().account(account).name(request.getName().trim())
                .description(text(request.getDescription())).ruleType(request.getRuleType()).threshold(BigDecimal.valueOf(request.getThreshold()))
                .timezone(zone(request.getTimezone()).getId()).enabled(request.getEnabled() == null || request.getEnabled()).build()));
    }
    public WindNotificationResponse update(Long accountId, Long id, WindNotificationRequest request) {
        demoGuard.assertWritable(accountId); validate(request); WindNotificationEntity e = owned(accountId, id);
        e.setName(request.getName().trim()); e.setDescription(text(request.getDescription())); e.setRuleType(request.getRuleType());
        e.setThreshold(BigDecimal.valueOf(request.getThreshold())); e.setTimezone(zone(request.getTimezone()).getId());
        e.setEnabled(request.getEnabled() == null || request.getEnabled()); e.setLastSentAt(null); return response(repository.save(e));
    }
    public void delete(Long accountId, Long id) { demoGuard.assertWritable(accountId); repository.delete(owned(accountId, id)); }
    public void sendDueNotifications() { sendDueNotifications(Instant.now()); }
    void sendDueNotifications(Instant now) {
        for (WindNotificationEntity e : repository.findByEnabledTrueOrderByIdAsc()) try { send(e, now); }
        catch (Exception ex) { log.error("Failed to process wind notification {}", e.getId(), ex); }
    }
    private void send(WindNotificationEntity e, Instant now) {
        ZoneId zone = zone(e.getTimezone()); LocalDate date = now.atZone(zone).toLocalDate();
        if (e.getLastSentAt() != null && e.getLastSentAt().atZone(zone).toLocalDate().equals(date)) return;
        WindForecastChartResponse chart = fingridService.getWindForecastChart(zone.getId());
        if (chart.getTomorrowAverage() == null) return;
        BigDecimal observed = e.getRuleType() == WindNotificationRuleType.TOMORROW_AVERAGE_BELOW
                ? chart.getTomorrowAverage() : chart.getTomorrowDropPercent();
        if (observed == null || observed.compareTo(e.getThreshold()) < 0 && e.getRuleType() == WindNotificationRuleType.TOMORROW_DROP_PERCENT
                || observed.compareTo(e.getThreshold()) >= 0 && e.getRuleType() == WindNotificationRuleType.TOMORROW_AVERAGE_BELOW) return;
        AccountEntity account = e.getAccount(); Locale locale = Locale.forLanguageTag(account.getLocale() == null ? "en" : account.getLocale());
        boolean sent = false;
        if (account.isEmailNotificationsEnabled() && account.getEmail() != null && !account.getEmail().isBlank()
                && limitService.tryConsumeWeeklyEmailNotification(account.getId(), now)) {
            emailService.sendWindNotificationEmail(account.getEmail(), e.getName(), e.getDescription(), e.getRuleType().name(), observed,
                    e.getThreshold(), chart.getTodayAverage(), chart.getTomorrowAverage(), now.atZone(zone)); sent = true;
        }
        if (account.isPushNotificationsEnabled() && tokenService.hasActivePushToken(account.getId())
                && limitService.tryConsumeWeeklyPushNotification(account.getId(), now)) {
            sent |= pushService.sendWindNotification(account, e, observed, chart.getTodayAverage(), chart.getTomorrowAverage(), now.atZone(zone));
        }
        if (sent) { e.setLastSentAt(now); repository.save(e); }
    }
    private void validate(WindNotificationRequest r) {
        if (r.getName() == null || r.getName().isBlank()) throw new IllegalArgumentException("Notification name cannot be empty");
        if (r.getRuleType() == null || r.getThreshold() == null || !Double.isFinite(r.getThreshold()) || r.getThreshold() < 0)
            throw new IllegalArgumentException("Valid rule and non-negative threshold are required");
        zone(r.getTimezone());
    }
    private ZoneId zone(String value) { return ZoneId.of(value == null || value.isBlank() ? ZoneId.systemDefault().getId() : value.trim()); }
    private String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private WindNotificationEntity owned(Long accountId, Long id) { return repository.findByIdAndAccountId(id, accountId).orElseThrow(() -> new EntityNotFoundException("Wind notification not found: " + id)); }
    private WindNotificationResponse response(WindNotificationEntity e) { return WindNotificationResponse.builder().id(e.getId()).name(e.getName()).description(e.getDescription())
            .ruleType(e.getRuleType()).threshold(e.getThreshold()).timezone(e.getTimezone()).enabled(e.isEnabled()).lastSentAt(e.getLastSentAt())
            .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build(); }
}

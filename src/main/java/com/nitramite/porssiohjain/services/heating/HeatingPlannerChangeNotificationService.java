package com.nitramite.porssiohjain.services.heating;

import com.nitramite.porssiohjain.entity.HeatingPlannerRoomHeatSourceEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerSettingsEntity;
import com.nitramite.porssiohjain.entity.enums.HeatingPlannerHeatSourceType;
import com.nitramite.porssiohjain.entity.repository.HeatingPlannerRoomHeatSourceRepository;
import com.nitramite.porssiohjain.services.AccountLimitService;
import com.nitramite.porssiohjain.services.PushNotificationService;
import com.nitramite.porssiohjain.services.PushNotificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HeatingPlannerChangeNotificationService {
    private final HeatingPlannerRoomHeatSourceRepository sources;
    private final PushNotificationService push;
    private final PushNotificationTokenService tokens;
    private final AccountLimitService limits;
    private final MessageSource messages;

    public void thermostatReported(Long deviceId, BigDecimal temperature, Instant now) {
        sources.findByControllingDeviceIdAndEnabledTrueOrderByIdAsc(deviceId).stream()
                .filter(source -> source.getSourceType() == HeatingPlannerHeatSourceType.FLOOR_HEATING)
                .findFirst().ifPresent(source -> send(source, false, temperature, now));
    }

    public void heatPumpApplied(Long sourceId, BigDecimal temperature, Instant now) {
        sources.findById(sourceId).ifPresent(source -> {
            if (source.isEnabled() && source.getSourceType() == HeatingPlannerHeatSourceType.HEAT_PUMP) {
                send(source, true, temperature, now);
            }
        });
    }

    private void send(HeatingPlannerRoomHeatSourceEntity source, boolean heatPump, BigDecimal temperature, Instant now) {
        HeatingPlannerSettingsEntity settings = source.getRoom().getSettings();
        var account = source.getAccount();
        if (!settings.isEnabled() || !(heatPump ? settings.isNotifyHeatPumpChanges() : settings.isNotifyThermostatChanges())
                || !account.isPushNotificationsEnabled() || !tokens.hasActivePushToken(account.getId())
                || !limits.tryConsumeWeeklyPushNotification(account.getId(), now)) return;
        String target = temperature.stripTrailingZeros().toPlainString();
        Locale locale = account.getLocale() == null || account.getLocale().isBlank()
                ? Locale.ENGLISH : Locale.of(account.getLocale());
        String prefix = heatPump ? "push.heatingPlanner.heatPump" : "push.heatingPlanner.thermostat";
        String title = messages.getMessage(prefix + ".title", null, locale);
        String body = messages.getMessage(prefix + ".body", new Object[]{source.getRoom().getName(),
                source.getControllingDevice() == null ? source.getName() : source.getControllingDevice().getDeviceName(),
                target}, locale);
        push.sendToAccount(account.getId(), title, body, Map.of(
                "type", heatPump ? "HEATING_PLANNER_HEAT_PUMP_CHANGED" : "HEATING_PLANNER_THERMOSTAT_CHANGED",
                "siteId", String.valueOf(source.getSite().getId()),
                "roomId", String.valueOf(source.getRoom().getId()),
                "targetTemperature", target));
    }
}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.mqtt.MqttReconnectService;
import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.fingrid.FingridDataService;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import com.nitramite.porssiohjain.services.models.WindForecastResponse;
import com.nitramite.porssiohjain.services.nordpool.NordpoolDataPortalService;
import com.nitramite.porssiohjain.services.solarman.SolarmanPvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Locale;

@Slf4j
@Profile("!test")
@Component
public class Scheduler {

    private final NordpoolDataPortalService nordpoolDataPortalService;
    private final ControlSchedulerService controlSchedulerService;
    private final FingridDataService fingridDataService;
    private final PowerLimitService powerLimitService;
    private final SolarmanPvService solarmanPvService;
    private final ProductionSourceService productionSourceService;
    private final PricePredictionDataService pricePredictionDataService;
    private final SiteWeatherService siteWeatherService;
    private final EmailService emailService;
    private final AuthService authService;
    private final DeviceService deviceService;
    private final ControlService controlService;
    private final HeatPumpControlService heatPumpControlService;
    private final ControlNotificationService controlNotificationService;
    private final HeatPumpOnlineCheckService heatPumpOnlineCheckService;

    private boolean firstRun = true;

    public Scheduler(
            NordpoolDataPortalService nordpoolDataPortalService,
            ControlSchedulerService controlSchedulerService,
            FingridDataService fingridDataService,
            PowerLimitService powerLimitService,
            SolarmanPvService solarmanPvService,
            ProductionSourceService productionSourceService,
            PricePredictionDataService pricePredictionDataService,
            SiteWeatherService siteWeatherService,
            EmailService emailService,
            AuthService authService,
            MqttReconnectService mqttReconnectService,
            DeviceService deviceService,
            ControlService controlService,
            HeatPumpControlService heatPumpControlService,
            ControlNotificationService controlNotificationService,
            HeatPumpOnlineCheckService heatPumpOnlineCheckService
    ) {
        this.nordpoolDataPortalService = nordpoolDataPortalService;
        this.controlSchedulerService = controlSchedulerService;
        this.fingridDataService = fingridDataService;
        this.solarmanPvService = solarmanPvService;
        this.powerLimitService = powerLimitService;
        this.productionSourceService = productionSourceService;
        this.pricePredictionDataService = pricePredictionDataService;
        this.siteWeatherService = siteWeatherService;
        this.emailService = emailService;
        this.authService = authService;
        this.deviceService = deviceService;
        this.controlService = controlService;
        this.heatPumpControlService = heatPumpControlService;
        this.controlNotificationService = controlNotificationService;
        this.heatPumpOnlineCheckService = heatPumpOnlineCheckService;

        if (!nordpoolDataPortalService.hasDataForToday()) {
            nordpoolDataPortalService.fetchData(Day.TODAY);
        } else {
            log.info("No need to fetch Nordpool data");
        }
        if (fingridDataService.apiEnabled && !fingridDataService.hasFingridDataForTomorrow()) {
            fingridDataService.fetchData();
        } else {
            log.info("No need to fetch Fingrid data");
        }
        if (!pricePredictionDataService.hasFuturePredictions(ZoneId.systemDefault())) {
            pricePredictionDataService.fetchData();
        } else {
            log.info("No need to fetch price prediction data");
        }
        if (!siteWeatherService.hasWeatherDataForTomorrowForConfiguredSites()) {
            siteWeatherService.fetchForecastsForConfiguredSites();
        } else {
            log.info("No need to fetch site weather data");
        }

        mqttReconnectService.reconnect();
    }

    @Scheduled(cron = "0 0 */4 * * *", zone = "Europe/Helsinki")
    public void fetchNordpoolDataEveryTwoHours() {
        try {
            if (!nordpoolDataPortalService.hasDataForToday()) {
                NordpoolResponse response = nordpoolDataPortalService.fetchData(Day.TODAY);
                log.info("Nordpool data (2h interval) fetched and saved successfully: {}", response.getMultiIndexEntries().size());
            }
        } catch (Exception e) {
            String msg = "Error fetching Nordpool data (4h interval)";
            log.error(msg, e);
            this.emailService.sendSystemErrorEmail(msg + e, Locale.getDefault());
        }
    }


    @Scheduled(cron = "0 0 14 * * *", zone = "Europe/Helsinki")
    public void fetchFingridDataDaily() {
        if (fingridDataService.apiEnabled) {
            try {
                WindForecastResponse response = fingridDataService.fetchData();
                log.info("Fingrid wind forecast data fetched and saved {} rows successfully", response.getData().size());
            } catch (Exception e) {
                log.error("Error fetching Fingrid wind forecast data", e);
            }
        }
    }

    @Scheduled(cron = "0 30 14 * * *", zone = "Europe/Helsinki")
    public void fetchNordpoolDataDaily() {
        try {
            NordpoolResponse response = nordpoolDataPortalService.fetchData(Day.TOMORROW);
            log.info("Nordpool day ahead data fetched and saved {} rows successfully", response.getMultiIndexEntries().size());
        } catch (Exception e) {
            String msg = "Error fetching Nordpool data";
            log.error(msg, e);
            this.emailService.sendSystemErrorEmail(msg + e, Locale.getDefault());
        }
    }

    @Scheduled(cron = "0 40 14 * * *", zone = "Europe/Helsinki")
    public void runAfterNordpoolImport() {
        try {
            controlSchedulerService.generatePlannedForTomorrow();
        } catch (Exception e) {
            log.error(e.toString());
            this.emailService.sendSystemErrorEmail(e.toString(), Locale.getDefault());
        }
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Helsinki")
    public void fetchNordpoolDataDailyBackup() {
        try {
            NordpoolResponse response = nordpoolDataPortalService.fetchData(Day.TOMORROW);
            log.info("Nordpool day ahead data fetched and saved successfully: {}", response.getMultiIndexEntries().size());
        } catch (Exception e) {
            log.error("Error fetching Nordpool data", e);
        }
    }

    @Scheduled(cron = "0 40 18 * * *", zone = "Europe/Helsinki")
    public void runAfterNordpoolImportBackup() {
        try {
            controlSchedulerService.generatePlannedForTomorrow();
        } catch (Exception e) {
            log.error(e.toString());
            this.emailService.sendSystemErrorEmail(e.toString(), Locale.getDefault());
        }
    }


    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Helsinki")
    public void fetchPricePredictionData() {
        pricePredictionDataService.fetchData();
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Helsinki")
    public void fetchSiteWeatherDataDaily() {
        try {
            int fetchedSites = siteWeatherService.fetchForecastsForConfiguredSites();
            log.info("Site weather data fetched for {} sites", fetchedSites);
        } catch (Exception e) {
            log.error("Error fetching site weather data", e);
        }
    }

    @Scheduled(cron = "0 0 13 * * *", zone = "Europe/Helsinki")
    public void fetchSiteWeatherDataDailyBackup() {
        try {
            int fetchedSites = siteWeatherService.fetchForecastsForConfiguredSites();
            log.info("Site weather data backup fetch completed for {} sites", fetchedSites);
        } catch (Exception e) {
            log.error("Error fetching site weather data on backup schedule", e);
        }
    }

    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Helsinki")
    public void deleteOldData() {
        nordpoolDataPortalService.deleteOldNordpoolData();
        fingridDataService.deleteOldFingridData();
        powerLimitService.deleteOldPowerLimitHistory();
        productionSourceService.deleteOldProductionHistory();
        pricePredictionDataService.deleteOldData();
        siteWeatherService.deleteOldSiteWeatherData();
        authService.deleteExpiredTokens();
    }

    @Scheduled(fixedDelayString = "${solarman.poll-interval}")
    public void pollSolarmanSources() {
        if (firstRun) {
            firstRun = false;
            return;
        }
        solarmanPvService.fetchGenerationData();
    }

    @Scheduled(fixedDelayString = "5m")
    public void checkOfflineDevices() {
        deviceService.checkOfflineDevices();
    }

    @Scheduled(cron = "1 0/1 * * * *", zone = "Europe/Helsinki")
    public void mqttDeviceControls() {
        controlService.mqttDeviceControls();
    }

    @Scheduled(cron = "15 0/1 * * * *", zone = "Europe/Helsinki")
    public void controlNotifications() {
        controlNotificationService.sendDueNotifications();
    }

    @Scheduled(fixedDelayString = "${heatpump.control-interval}")
    public void scheduledHeatPumpControls() {
        heatPumpControlService.runScheduledHeatPumpControls();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Europe/Helsinki")
    public void refreshHeatPumpOnlineStates() {
        heatPumpOnlineCheckService.refreshHeatPumpApiOnlineStates();
    }

}

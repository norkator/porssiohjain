package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import com.nitramite.porssiohjain.services.models.WindForecastResponse;
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
    private final EmailService emailService;

    private boolean firstRun = true;

    public Scheduler(
            NordpoolDataPortalService nordpoolDataPortalService,
            ControlSchedulerService controlSchedulerService,
            FingridDataService fingridDataService,
            PowerLimitService powerLimitService,
            SolarmanPvService solarmanPvService,
            ProductionSourceService productionSourceService,
            PricePredictionDataService pricePredictionDataService,
            EmailService emailService
    ) {
        this.nordpoolDataPortalService = nordpoolDataPortalService;
        this.controlSchedulerService = controlSchedulerService;
        this.fingridDataService = fingridDataService;
        this.solarmanPvService = solarmanPvService;
        this.powerLimitService = powerLimitService;
        this.productionSourceService = productionSourceService;
        this.pricePredictionDataService = pricePredictionDataService;
        this.emailService = emailService;

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

    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Helsinki")
    public void deleteOldData() {
        nordpoolDataPortalService.deleteOldNordpoolData();
        fingridDataService.deleteOldFingridData();
        powerLimitService.deleteOldPowerLimitHistory();
        productionSourceService.deleteOldProductionHistory();
        pricePredictionDataService.deleteOldData();
    }

    @Scheduled(fixedDelayString = "${solarman.poll-interval}")
    public void pollSolarmanSources() {
        if (firstRun) {
            firstRun = false;
            return;
        }
        solarmanPvService.fetchGenerationData();
    }

}

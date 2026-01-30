package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.*;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import com.nitramite.porssiohjain.services.models.WindForecastResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("!test")
@Component
public class Scheduler {

    private final NordpoolDataPortalService nordpoolDataPortalService;
    private final ControlSchedulerService controlSchedulerService;
    private final FingridDataService fingridDataService;
    private final PowerLimitService powerLimitService;
    private final SolarmanPvService solarmanPvService;

    public Scheduler(
            NordpoolDataPortalService nordpoolDataPortalService,
            ControlSchedulerService controlSchedulerService,
            FingridDataService fingridDataService,
            PowerLimitService powerLimitService,
            SolarmanPvService solarmanPvService
    ) {
        this.nordpoolDataPortalService = nordpoolDataPortalService;
        this.controlSchedulerService = controlSchedulerService;
        this.fingridDataService = fingridDataService;
        this.solarmanPvService = solarmanPvService;

        if (!nordpoolDataPortalService.hasDataForToday()) {
            nordpoolDataPortalService.fetchData(Day.TODAY);
        } else {
            log.info("No need to fetch Nordpool data");
        }
        if (!fingridDataService.hasFingridDataForTomorrow()) {
            fingridDataService.fetchData();
        } else {
            log.info("No need to fetch Fingrid data");
        }
        this.powerLimitService = powerLimitService;
    }

    @Scheduled(cron = "0 0 */2 * * *", zone = "Europe/Helsinki")
    public void fetchNordpoolDataEveryTwoHours() {
        try {
            if (!nordpoolDataPortalService.hasDataForToday()) {
                NordpoolResponse response = nordpoolDataPortalService.fetchData(Day.TODAY);
                log.info("Nordpool data (2h interval) fetched and saved successfully: {}", response.getMultiIndexEntries().size());
            }
        } catch (Exception e) {
            log.error("Error fetching Nordpool data (2h interval)", e);
        }
    }


    @Scheduled(cron = "0 0 14 * * *", zone = "Europe/Helsinki")
    public void fetchFingridDataDaily() {
        try {
            WindForecastResponse response = fingridDataService.fetchData();
            log.info("Fingrid wind forecast data fetched and saved {} rows successfully", response.getData().size());
        } catch (Exception e) {
            log.error("Error fetching Fingrid wind forecast data", e);
        }
    }

    @Scheduled(cron = "0 30 14 * * *", zone = "Europe/Helsinki")
    public void fetchNordpoolDataDaily() {
        try {
            NordpoolResponse response = nordpoolDataPortalService.fetchData(Day.TOMORROW);
            log.info("Nordpool day ahead data fetched and saved {} rows successfully", response.getMultiIndexEntries().size());
        } catch (Exception e) {
            log.error("Error fetching Nordpool data", e);
        }
    }

    @Scheduled(cron = "0 40 14 * * *", zone = "Europe/Helsinki")
    public void runAfterNordpoolImport() {
        controlSchedulerService.generatePlannedForTomorrow();
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
        controlSchedulerService.generatePlannedForTomorrow();
    }


    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Helsinki")
    public void deleteOldData() {
        nordpoolDataPortalService.deleteOldNordpoolData();
        fingridDataService.deleteOldFingridData();
        powerLimitService.deleteOldPowerLimitHistory();
    }

    @Scheduled(fixedDelayString = "${solarman.poll-interval}")
    public void pollSolarmanSources() {
        solarmanPvService.fetchGenerationData();
    }

}

package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.FingridDataService;
import com.nitramite.porssiohjain.services.NordpoolDataPortalService;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import com.nitramite.porssiohjain.services.models.WindForecastResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Scheduler {

    private final NordpoolDataPortalService nordpoolDataPortalService;
    private final ControlSchedulerService controlSchedulerService;
    // private final SystemLogService systemLogService;
    private final FingridDataService fingridDataService;

    public Scheduler(
            NordpoolDataPortalService nordpoolDataPortalService,
            ControlSchedulerService controlSchedulerService,
            // SystemLogService systemLogService,
            FingridDataService fingridDataService
    ) {
        this.nordpoolDataPortalService = nordpoolDataPortalService;
        this.controlSchedulerService = controlSchedulerService;
        // this.systemLogService = systemLogService;
        this.fingridDataService = fingridDataService;
        // nordpoolDataPortalService.fetchData();
        // fingridDataService.fetchData();
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
            NordpoolResponse response = nordpoolDataPortalService.fetchData();
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
            NordpoolResponse response = nordpoolDataPortalService.fetchData();
            log.info("Nordpool day ahead data fetched and saved successfully: {}", response);
        } catch (Exception e) {
            log.error("Error fetching Nordpool data", e);
        }
    }

    @Scheduled(cron = "0 40 18 * * *", zone = "Europe/Helsinki")
    public void runAfterNordpoolImportBackup() {
        controlSchedulerService.generatePlannedForTomorrow();
    }

}

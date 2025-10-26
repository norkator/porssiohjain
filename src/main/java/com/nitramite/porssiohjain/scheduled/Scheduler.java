package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.ControlSchedulerService;
import com.nitramite.porssiohjain.services.NordpoolDataPortalService;
import com.nitramite.porssiohjain.services.SystemLogService;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Scheduler {

    private final NordpoolDataPortalService nordpoolDataPortalService;
    private final ControlSchedulerService controlSchedulerService;
    private final SystemLogService systemLogService;

    public Scheduler(
            NordpoolDataPortalService nordpoolDataPortalService,
            ControlSchedulerService controlSchedulerService,
            SystemLogService systemLogService
    ) {
        this.nordpoolDataPortalService = nordpoolDataPortalService;
        this.controlSchedulerService = controlSchedulerService;
        this.systemLogService = systemLogService;
        // nordpoolDataPortalService.fetchData();
    }

    @Scheduled(cron = "0 30 14 * * *", zone = "Europe/Helsinki")
    public void fetchNordpoolDataDaily() {
        try {
            NordpoolResponse response = nordpoolDataPortalService.fetchData();
            log.info("Nordpool day ahead data fetched and saved successfully: {}", response);
        } catch (Exception e) {
            log.error("Error fetching Nordpool data", e);
        }
    }

    @Scheduled(cron = "0 40 14 * * *")
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

    @Scheduled(cron = "0 40 18 * * *")
    public void runAfterNordpoolImportBackup() {
        controlSchedulerService.generatePlannedForTomorrow();
    }

}

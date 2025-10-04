package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.NordpoolDataPortalService;
import com.nitramite.porssiohjain.services.models.NordpoolResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Scheduler {

    private final NordpoolDataPortalService nordpoolDataPortalService;

    public Scheduler(
            NordpoolDataPortalService nordpoolDataPortalService
    ) {
        this.nordpoolDataPortalService = nordpoolDataPortalService;
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

}

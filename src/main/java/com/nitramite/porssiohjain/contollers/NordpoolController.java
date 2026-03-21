package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.TodayPriceStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/nordpool")
@RequiredArgsConstructor
public class NordpoolController {

    private final NordpoolService nordpoolService;

    @GetMapping("/today-stats")
    public TodayPriceStatsResponse getTodayStats(
            @RequestParam(required = false) String timezone
    ) {
        return nordpoolService.getTodayStats(null, timezone);
    }
}
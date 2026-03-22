package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.NordpoolService;
import com.nitramite.porssiohjain.services.models.TodayPriceStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/nordpool")
@RequiredArgsConstructor
public class NordpoolController {

    private final NordpoolService nordpoolService;

    @CrossOrigin(origins = "https://www.porssiohjain.fi")
    @GetMapping("/today-stats")
    public TodayPriceStatsResponse getTodayStats(
            @RequestParam(required = false) String timezone
    ) {
        return nordpoolService.getTodayStats(null, timezone);
    }
}
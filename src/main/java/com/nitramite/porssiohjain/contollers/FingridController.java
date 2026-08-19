package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.FingridService;
import com.nitramite.porssiohjain.services.models.WindForecastChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/fingrid") @RequiredArgsConstructor @RequireAuth
public class FingridController {
    private final FingridService fingridService;
    @GetMapping("/wind-forecast")
    public WindForecastChartResponse forecast(@RequestParam(required = false) String timezone) {
        return fingridService.getWindForecastChart(timezone);
    }
}

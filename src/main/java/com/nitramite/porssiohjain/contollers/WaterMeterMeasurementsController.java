package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.WaterMeterWidgetQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/measurements/water-meters")
@RequiredArgsConstructor
@RequireAuth
public class WaterMeterMeasurementsController {
    private final AuthContext authContext;
    private final WaterMeterWidgetQueryService query;

    @GetMapping("/latest")
    public List<WaterMeterWidgetQueryService.Reading> latest() {
        return query.latest(authContext.getAccountId());
    }

    @GetMapping("/history")
    public WaterMeterWidgetQueryService.History history(@RequestParam Long waterMeterId,
            @RequestParam WaterMeterWidgetQueryService.Type type, @RequestParam String key,
            @RequestParam(defaultValue = "24") int hours) {
        return query.history(authContext.getAccountId(), waterMeterId, type, key, hours, Instant.now());
    }
}

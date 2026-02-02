package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.PowerLimitService;
import com.nitramite.porssiohjain.services.models.CurrentKwRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/power")
@RequiredArgsConstructor
public class PowerController {

    private final PowerLimitService powerLimitService;

    @PostMapping("/{deviceUuid}")
    public void updateCurrentKw(
            @PathVariable String deviceUuid,
            @RequestBody CurrentKwRequest request
    ) {
        powerLimitService.updateCurrentKw(
                deviceUuid, request
        );
    }

}
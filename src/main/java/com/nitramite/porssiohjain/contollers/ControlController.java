package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.services.ControlService;
import com.nitramite.porssiohjain.services.models.ControlDeviceResponse;
import com.nitramite.porssiohjain.services.models.ControlResponse;
import com.nitramite.porssiohjain.services.models.CreateControlRequest;
import com.nitramite.porssiohjain.services.models.UpdateControlRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/control")
@RequiredArgsConstructor
public class ControlController {

    private final ControlService controlService;
    private final AuthContext authContext;

    @GetMapping("/{deviceUuid}")
    public Map<Integer, Integer> controlsForDevice(
            @PathVariable String deviceUuid
    ) {
        return controlService.getControlsForDevice(deviceUuid);
    }

    @RequireAuth
    @PostMapping("/create")
    public ControlEntity createControl(
            @RequestBody CreateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.createControl(
                accountId,
                request.getName(),
                request.getTimezone(),
                request.getMaxPriceSnt(),
                request.getDailyOnMinutes(),
                request.getTaxPercent(),
                request.getMode(),
                request.getManualOn()
        );
    }

    @RequireAuth
    @PutMapping("/update/{id}")
    public ControlEntity updateControl(
            @PathVariable Long id,
            @RequestBody UpdateControlRequest request
    ) {
        Long accountId = authContext.getAccountId();
        return controlService.updateControl(
                accountId,
                id,
                request.getName(),
                request.getMaxPriceSnt(),
                request.getDailyOnMinutes(),
                request.getTaxPercent(),
                request.getMode(),
                request.getManualOn()
        );
    }

    @RequireAuth
    @DeleteMapping("/delete/{id}")
    public void deleteControl(
            @PathVariable Long id
    ) {
        Long accountId = authContext.getAccountId();
        controlService.deleteControl(accountId, id);
    }

    @RequireAuth
    @GetMapping("/controls")
    public List<ControlResponse> getAllControls() {
        Long accountId = authContext.getAccountId();
        return controlService.getAllControls(accountId);
    }

    @RequireAuth
    @PostMapping("/{controlId}/create/device")
    public ControlDeviceResponse addDeviceToControl(
            @PathVariable Long controlId,
            @RequestParam Long deviceId,
            @RequestParam Integer deviceChannel
    ) {
        return controlService.addDeviceToControl(controlId, deviceId, deviceChannel);
    }

    @RequireAuth
    @PutMapping("/update/device/{id}")
    public ControlDeviceResponse updateControlDevice(
            @PathVariable Long id,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Integer deviceChannel
    ) {
        return controlService.updateControlDevice(id, deviceId, deviceChannel);
    }

    @RequireAuth
    @DeleteMapping("/delete/device/{id}")
    public void deleteControlDevice(
            @PathVariable Long id
    ) {
        controlService.deleteControlDevice(id);
    }

    @RequireAuth
    @GetMapping("/{controlId}/devices")
    public List<ControlDeviceEntity> getDevicesByControl(
            @PathVariable Long controlId
    ) {
        return controlService.getDevicesByControl(controlId);
    }

}

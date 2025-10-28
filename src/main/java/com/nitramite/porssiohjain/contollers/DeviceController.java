package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.AuthContext;
import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.models.CreateDeviceRequest;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final AuthContext authContext;

    @RequireAuth
    @PostMapping("/create/{accountId}")
    public ResponseEntity<DeviceResponse> createDevice(
            @PathVariable Long accountId,
            @RequestBody CreateDeviceRequest request
    ) {
        Long authAccountId = authContext.getAccountId();
        DeviceResponse device = deviceService.createDevice(authAccountId, accountId, request.getDeviceName(), request.getTimezone());
        return ResponseEntity.ok(device);
    }

    @RequireAuth
    @GetMapping("/list/{accountId}")
    public ResponseEntity<List<DeviceResponse>> listDevices(
            @PathVariable Long accountId
    ) {
        Long authAccountId = authContext.getAccountId();
        return ResponseEntity.ok(deviceService.listDevices(authAccountId, accountId));
    }

    @RequireAuth
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(
            @PathVariable Long deviceId
    ) {
        Long accountId = authContext.getAccountId();
        return ResponseEntity.ok(deviceService.getDevice(accountId, deviceId));
    }

}
package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.auth.RequireAuth;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.models.CreateDeviceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @RequireAuth
    @PostMapping("/create/{accountId}")
    public ResponseEntity<DeviceEntity> createDevice(
            @PathVariable Long accountId,
            @RequestBody CreateDeviceRequest request
    ) {
        DeviceEntity device = deviceService.createDevice(accountId, request.getDeviceName(), request.getTimezone());
        return ResponseEntity.ok(device);
    }

    @RequireAuth
    @GetMapping("/list/{accountId}")
    public ResponseEntity<List<DeviceEntity>> listDevices(
            @PathVariable Long accountId
    ) {
        return ResponseEntity.ok(deviceService.listDevices(accountId));
    }

    @RequireAuth
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceEntity> getDevice(
            @PathVariable Long deviceId
    ) {
        return ResponseEntity.ok(deviceService.getDevice(deviceId));
    }

}
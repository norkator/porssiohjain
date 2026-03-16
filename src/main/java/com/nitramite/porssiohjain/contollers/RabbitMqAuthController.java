package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.RabbitMqAuthResponse;
import com.nitramite.porssiohjain.services.models.RabbitMqUserAuthRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/rabbitmq/authenticate")
@RequiredArgsConstructor
@Slf4j
public class RabbitMqAuthController {

    private final DeviceRepository deviceRepository;

    @PostMapping("/user")
    public ResponseEntity<RabbitMqAuthResponse> authenticateUser(
            @RequestBody RabbitMqUserAuthRequest request
    ) {
        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(request.getUsername());
        if (deviceOpt.isEmpty()) {
            log.warn("MQTT auth denied for username '{}' - device not found", request.getUsername());
            return ResponseEntity.ok(new RabbitMqAuthResponse("deny"));
        }
        DeviceEntity device = deviceOpt.get();
        if (!Objects.equals(device.getMqttPassword(), request.getPassword())) {
            log.warn("MQTT auth denied for username '{}' - invalid password", request.getUsername());
            return ResponseEntity.ok(new RabbitMqAuthResponse("deny"));
        }
        log.info("MQTT auth allowed for username '{}' (device uuid: {})", request.getUsername(), device.getUuid());
        return ResponseEntity.ok(new RabbitMqAuthResponse("allow"));
    }

}
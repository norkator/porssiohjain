package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.RabbitMqAuthResponse;
import com.nitramite.porssiohjain.services.models.RabbitMqUserAuthRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String client_id,
            @RequestParam(required = false) String vhost
    ) {
        log.info("RabbitMQ HTTP auth request: username='{}', password='{}', client_id='{}', vhost='{}'",
                username, password, client_id, vhost);
        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(username);
        if (deviceOpt.isEmpty()) {
            log.warn("MQTT auth denied for username '{}' - device not found", username);
            return ResponseEntity.ok(new RabbitMqAuthResponse(false));
        }
        DeviceEntity device = deviceOpt.get();
        if (!Objects.equals(device.getMqttPassword(), password)) {
            log.warn("MQTT auth denied for username '{}' - invalid password", username);
            return ResponseEntity.ok(new RabbitMqAuthResponse(false));
        }
        log.info("MQTT auth allowed for username '{}' (device uuid: {})", username, device.getUuid());
        return ResponseEntity.ok(new RabbitMqAuthResponse(true));
    }

}
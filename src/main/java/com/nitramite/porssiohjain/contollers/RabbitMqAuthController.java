package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.RabbitMqAuthResponse;
import com.nitramite.porssiohjain.services.models.RabbitMqUserAuthRequest;
import lombok.RequiredArgsConstructor;
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
public class RabbitMqAuthController {

    private final DeviceRepository deviceRepository;

    @PostMapping("/user")
    public ResponseEntity<RabbitMqAuthResponse> authenticateUser(
            @RequestBody RabbitMqUserAuthRequest request
    ) {
        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(request.getUsername());
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.ok(new RabbitMqAuthResponse("deny"));
        }
        DeviceEntity device = deviceOpt.get();
        if (!Objects.equals(device.getMqttPassword(), request.getPassword())) {
            return ResponseEntity.ok(new RabbitMqAuthResponse("deny"));
        }
        return ResponseEntity.ok(new RabbitMqAuthResponse("allow"));
    }

}
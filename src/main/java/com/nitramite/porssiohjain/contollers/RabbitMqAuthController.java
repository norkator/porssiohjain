package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
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

    private static final String ALLOW = "allow";
    private static final String DENY = "deny";
    private static final String DEFAULT_VHOST = "/";
    private static final String MQTT_EXCHANGE = "amq.topic";
    private static final String MQTT_SUBSCRIPTION_QUEUE_PREFIX = "mqtt-subscription-";

    @PostMapping("/user")
    ResponseEntity<String> authenticateUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String client_id,
            @RequestParam(required = false) String vhost
    ) {
        log.info("RabbitMQ HTTP auth request: username='{}', client_id='{}', vhost='{}'",
                username, client_id, vhost);
        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(username);
        if (deviceOpt.isEmpty()) {
            log.warn("MQTT auth denied for username '{}' - device not found", username);
            return ResponseEntity.ok(DENY);
        }
        DeviceEntity device = deviceOpt.get();
        if (!Objects.equals(device.getMqttPassword(), password)) {
            log.warn("MQTT auth denied for username '{}' - invalid password", username);
            return ResponseEntity.ok(DENY);
        }
        log.info("MQTT auth allowed for username '{}' (device uuid: {})", username, device.getUuid());
        return ResponseEntity.ok(ALLOW);
    }

    @PostMapping("/vhost")
    public ResponseEntity<String> authorizeVhost(
            @RequestParam String username,
            @RequestParam String vhost,
            @RequestParam(required = false) String ip
    ) {
        log.info("VHOST auth: username='{}', vhost='{}', ip='{}'", username, vhost, ip);
        return deviceRepository.findByMqttUsername(username)
                .map(device -> ResponseEntity.ok(DEFAULT_VHOST.equals(vhost) ? ALLOW : DENY))
                .orElseGet(() -> ResponseEntity.ok(ALLOW));
    }

    @PostMapping("/resource")
    public ResponseEntity<String> authorizeResource(
            @RequestParam String username,
            @RequestParam String vhost,
            @RequestParam String resource,
            @RequestParam String name,
            @RequestParam String permission
    ) {
        log.debug("RESOURCE auth: username='{}', vhost='{}', resource='{}', name='{}', permission='{}'",
                username, vhost, resource, name, permission);
        return deviceRepository.findByMqttUsername(username)
                .map(device -> ResponseEntity.ok(authorizeDeviceResource(vhost, resource, name, permission)))
                .orElseGet(() -> ResponseEntity.ok(ALLOW));
    }

    @PostMapping("/topic")
    public ResponseEntity<String> authorizeTopic(
            @RequestParam String username,
            @RequestParam String vhost,
            @RequestParam String resource,
            @RequestParam String name,
            @RequestParam String permission,
            @RequestParam String routing_key
    ) {
        log.debug("TOPIC auth: username='{}', vhost='{}', resource='{}', name='{}', permission='{}', routing_key='{}'",
                username, vhost, resource, name, permission, routing_key);
        return deviceRepository.findByMqttUsername(username)
                .map(device -> ResponseEntity.ok(authorizeDeviceTopic(device, vhost, resource, name, permission, routing_key)))
                .orElseGet(() -> ResponseEntity.ok(ALLOW));
    }

    private String authorizeDeviceResource(
            String vhost,
            String resource,
            String name,
            String permission
    ) {
        if (!DEFAULT_VHOST.equals(vhost)) {
            return DENY;
        }
        if ("exchange".equals(resource) && MQTT_EXCHANGE.equals(name)) {
            return "read".equals(permission) ? ALLOW : DENY;
        }
        if ("queue".equals(resource) && name.startsWith(MQTT_SUBSCRIPTION_QUEUE_PREFIX)) {
            return isQueueSubscriptionPermission(permission) ? ALLOW : DENY;
        }
        return DENY;
    }

    private String authorizeDeviceTopic(
            DeviceEntity device,
            String vhost,
            String resource,
            String name,
            String permission,
            String routingKey
    ) {
        if (!DEFAULT_VHOST.equals(vhost)
                || !"topic".equals(resource)
                || !MQTT_EXCHANGE.equals(name)
                || !"read".equals(permission)) {
            return DENY;
        }
        return isOwnCommandRoutingKey(device, routingKey) ? ALLOW : DENY;
    }

    private boolean isQueueSubscriptionPermission(String permission) {
        return "configure".equals(permission) || "write".equals(permission) || "read".equals(permission);
    }

    private boolean isOwnCommandRoutingKey(DeviceEntity device, String routingKey) {
        String deviceUuid = device.getUuid().toString();
        return routingKey.startsWith(deviceUuid + "/command/")
                || routingKey.equals(deviceUuid + "/command/#")
                || routingKey.startsWith(deviceUuid + ".command.")
                || routingKey.equals(deviceUuid + ".command.#");
    }

}

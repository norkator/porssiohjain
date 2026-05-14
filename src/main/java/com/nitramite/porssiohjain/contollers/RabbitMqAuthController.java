package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.FactoryDeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.FactoryDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/rabbitmq/authenticate")
@RequiredArgsConstructor
@Slf4j
public class RabbitMqAuthController {

    private static final String ALLOW = "allow";
    private static final String DENY = "deny";
    private static final String DEFAULT_VHOST = "/";
    private static final String MQTT_EXCHANGE = "amq.topic";
    private static final String MQTT_SUBSCRIPTION_QUEUE_PREFIX = "mqtt-subscription-";

    private final DeviceRepository deviceRepository;
    private final FactoryDeviceRepository factoryDeviceRepository;

    private ResponseEntity<String> plainTextResponse(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

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
        if (deviceOpt.isPresent()) {
            DeviceEntity device = deviceOpt.get();
            if (!Objects.equals(device.getMqttPassword(), password)) {
                log.warn("MQTT auth denied for username '{}' - invalid password", username);
                return plainTextResponse(DENY);
            }
            log.info("MQTT auth allowed for username '{}' (device uuid: {})", username, device.getUuid());
            return plainTextResponse(ALLOW);
        }

        Optional<FactoryDeviceEntity> factoryDeviceOpt = factoryDeviceRepository.findByMqttUsername(username);
        if (factoryDeviceOpt.isPresent()) {
            FactoryDeviceEntity factoryDevice = factoryDeviceOpt.get();
            if (!Objects.equals(factoryDevice.getMqttPassword(), password)) {
                log.warn("Factory MQTT auth denied for username '{}' - invalid password", username);
                return plainTextResponse(DENY);
            }
            log.info("Factory MQTT auth allowed for username '{}' (serial: {})", username, factoryDevice.getSerialNumber());
            return plainTextResponse(ALLOW);
        }

        log.warn("MQTT auth denied for username '{}' - device not found", username);
        return plainTextResponse(DENY);
    }

    @PostMapping("/vhost")
    public ResponseEntity<String> authorizeVhost(
            @RequestParam String username,
            @RequestParam String vhost,
            @RequestParam(required = false) String ip
    ) {
        log.info("VHOST auth: username='{}', vhost='{}', ip='{}'", username, vhost, ip);
        if (deviceRepository.findByMqttUsername(username).isPresent()
                || factoryDeviceRepository.findByMqttUsername(username).isPresent()) {
            return plainTextResponse(DEFAULT_VHOST.equals(vhost) ? ALLOW : DENY);
        }
        return plainTextResponse(DENY);
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

        if (deviceRepository.findByMqttUsername(username).isPresent()
                || factoryDeviceRepository.findByMqttUsername(username).isPresent()) {
            return plainTextResponse(authorizeClientResource(vhost, resource, name, permission));
        }
        return plainTextResponse(DENY);
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

        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(username);
        if (deviceOpt.isPresent()) {
            return plainTextResponse(authorizeClientTopic(
                    deviceOpt.get().getUuid().toString(),
                    vhost,
                    resource,
                    name,
                    permission,
                    routing_key,
                    true
            ));
        }

        Optional<FactoryDeviceEntity> factoryDeviceOpt = factoryDeviceRepository.findByMqttUsername(username);
        if (factoryDeviceOpt.isPresent()) {
            return plainTextResponse(authorizeClientTopic(
                    factoryDeviceOpt.get().getMqttTopicRoot(),
                    vhost,
                    resource,
                    name,
                    permission,
                    routing_key,
                    false
            ));
        }

        return plainTextResponse(DENY);
    }

    private String authorizeClientResource(
            String vhost,
            String resource,
            String name,
            String permission
    ) {
        if (!DEFAULT_VHOST.equals(vhost)) {
            return DENY;
        }
        if ("exchange".equals(resource) && MQTT_EXCHANGE.equals(name)) {
            return isExchangePermission(permission) ? ALLOW : DENY;
        }
        if ("queue".equals(resource) && name.startsWith(MQTT_SUBSCRIPTION_QUEUE_PREFIX)) {
            return isQueueSubscriptionPermission(permission) ? ALLOW : DENY;
        }
        return DENY;
    }

    private String authorizeClientTopic(
            String topicRoot,
            String vhost,
            String resource,
            String name,
            String permission,
            String routingKey,
            boolean finalDevice
    ) {
        if (!DEFAULT_VHOST.equals(vhost)
                || !"topic".equals(resource)
                || !MQTT_EXCHANGE.equals(name)) {
            return DENY;
        }

        if ("read".equals(permission)) {
            return isReadableCommandRoutingKey(topicRoot, routingKey) ? ALLOW : DENY;
        }

        if ("write".equals(permission)) {
            return isWritableStatusRoutingKey(topicRoot, routingKey, finalDevice) ? ALLOW : DENY;
        }

        return DENY;
    }

    private boolean isQueueSubscriptionPermission(String permission) {
        return "configure".equals(permission) || "write".equals(permission) || "read".equals(permission);
    }

    private boolean isExchangePermission(String permission) {
        return "read".equals(permission) || "write".equals(permission);
    }

    private boolean isReadableCommandRoutingKey(String topicRoot, String routingKey) {
        return routingKey.equals(topicRoot + "/command")
                || routingKey.startsWith(topicRoot + "/command/")
                || routingKey.equals(topicRoot + "/command/#")
                || routingKey.startsWith(topicRoot + ".command.")
                || routingKey.equals(topicRoot + ".command.#");
    }

    private boolean isWritableStatusRoutingKey(String topicRoot, String routingKey, boolean finalDevice) {
        if (finalDevice) {
            return routingKey.equals(topicRoot + "/online")
                    || routingKey.startsWith(topicRoot + "/state/")
                    || routingKey.startsWith(topicRoot + "/telemetry/")
                    || routingKey.equals(topicRoot + ".online")
                    || routingKey.startsWith(topicRoot + ".state.")
                    || routingKey.startsWith(topicRoot + ".telemetry.");
        }
        return routingKey.equals(topicRoot + "/state")
                || routingKey.startsWith(topicRoot + "/telemetry/")
                || routingKey.startsWith(topicRoot + "/status/")
                || routingKey.equals(topicRoot + ".state")
                || routingKey.startsWith(topicRoot + ".telemetry.")
                || routingKey.startsWith(topicRoot + ".status.");
    }

}

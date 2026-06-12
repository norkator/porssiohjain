package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.FactoryDeviceEntity;
import com.nitramite.porssiohjain.entity.enums.DevicePlatform;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.FactoryDeviceRepository;
import com.nitramite.porssiohjain.services.SystemLogService;
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
    private final SystemLogService systemLogService;

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
        if ("admin".equals(username) || "spring-api".equals(username)) {
            return plainTextResponse(DENY);
        }

        logMqtt("RabbitMQ HTTP auth request: username='%s', client_id='%s', vhost='%s'"
                .formatted(username, client_id, vhost));

        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(username);
        if (deviceOpt.isPresent()) {
            DeviceEntity device = deviceOpt.get();
            if (!Objects.equals(device.getMqttPassword(), password)) {
                logMqtt("MQTT auth denied for username '%s' - invalid password".formatted(username));
                return plainTextResponse(DENY);
            }
            logMqtt("MQTT auth allowed for username '%s' (device uuid: %s)".formatted(username, device.getUuid()));
            return plainTextResponse(ALLOW);
        }

        Optional<FactoryDeviceEntity> factoryDeviceOpt = factoryDeviceRepository.findByMqttUsername(username);
        if (factoryDeviceOpt.isPresent()) {
            FactoryDeviceEntity factoryDevice = factoryDeviceOpt.get();
            if (!Objects.equals(factoryDevice.getMqttPassword(), password)) {
                logMqtt("Factory MQTT auth denied for username '%s' - invalid password".formatted(username));
                return plainTextResponse(DENY);
            }
            logMqtt("Factory MQTT auth allowed for username '%s' (serial: %s)"
                    .formatted(username, factoryDevice.getSerialNumber()));
            return plainTextResponse(ALLOW);
        }

        logMqtt("MQTT auth denied for username '%s' - device not found".formatted(username));
        return plainTextResponse(DENY);
    }

    @PostMapping("/vhost")
    public ResponseEntity<String> authorizeVhost(
            @RequestParam String username,
            @RequestParam String vhost,
            @RequestParam(required = false) String ip
    ) {
        logMqtt("VHOST auth: username='%s', vhost='%s', ip='%s'".formatted(username, vhost, ip));
        boolean userExists = deviceRepository.findByMqttUsername(username).isPresent()
                || factoryDeviceRepository.findByMqttUsername(username).isPresent();
        boolean vhostMatches = DEFAULT_VHOST.equals(vhost);
        String result = userExists && vhostMatches ? ALLOW : DENY;
        logMqtt("VHOST auth result: username='%s', vhost='%s', userExists=%s, vhostMatches=%s, result=%s'"
                .formatted(username, vhost, userExists, vhostMatches, result));
        // return plainTextResponse(result);
        return plainTextResponse(ALLOW);
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

        boolean userExists = deviceRepository.findByMqttUsername(username).isPresent()
                || factoryDeviceRepository.findByMqttUsername(username).isPresent();
        String result = userExists ? authorizeClientResource(vhost, resource, name, permission) : DENY;
        logMqtt("RESOURCE auth result: username='%s', vhost='%s', resource='%s', name='%s', permission='%s', userExists=%s, result=%s'"
                .formatted(username, vhost, resource, name, permission, userExists, result));
        // return plainTextResponse(result);
        return plainTextResponse(ALLOW);
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

        String result = DENY;
        String userType = "unknown";
        Optional<DeviceEntity> deviceOpt = deviceRepository.findByMqttUsername(username);
        if (deviceOpt.isPresent()) {
            userType = "device";
            result = authorizeClientTopic(
                    deviceOpt.get().getUuid().toString(),
                    vhost,
                    resource,
                    name,
                    permission,
                    routing_key,
                    true,
                    deviceOpt.get().getDevicePlatform()
            );
        } else {
            Optional<FactoryDeviceEntity> factoryDeviceOpt = factoryDeviceRepository.findByMqttUsername(username);
            if (factoryDeviceOpt.isPresent()) {
                userType = "factoryDevice";
                result = authorizeClientTopic(
                        factoryDeviceOpt.get().getMqttTopicRoot(),
                        vhost,
                        resource,
                        name,
                        permission,
                        routing_key,
                        false,
                        factoryDeviceOpt.get().getPlatform()
                );
            }
        }

        logMqtt("TOPIC auth result: username='%s', vhost='%s', resource='%s', name='%s', permission='%s', routing_key='%s', userType=%s, result=%s"
                .formatted(username, vhost, resource, name, permission, routing_key, userType, result));
        return plainTextResponse(result);
        // return plainTextResponse(ALLOW);
    }

    private void logMqtt(String message) {
        systemLogService.logMqtt(message);
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
            boolean finalDevice,
            DevicePlatform platform
    ) {
        if (!DEFAULT_VHOST.equals(vhost)
                || !"topic".equals(resource)
                || !MQTT_EXCHANGE.equals(name)) {
            return DENY;
        }

        if ("read".equals(permission)) {
            return isReadableCommandRoutingKey(topicRoot, routingKey, platform) ? ALLOW : DENY;
        }

        if ("write".equals(permission)) {
            return isWritableStatusRoutingKey(topicRoot, routingKey, finalDevice, platform) ? ALLOW : DENY;
        }

        return DENY;
    }

    private boolean isQueueSubscriptionPermission(String permission) {
        return "configure".equals(permission) || "write".equals(permission) || "read".equals(permission);
    }

    private boolean isExchangePermission(String permission) {
        return "read".equals(permission) || "write".equals(permission);
    }

    private boolean isReadableCommandRoutingKey(String topicRoot, String routingKey, DevicePlatform platform) {
        return routingKey.equals("shellies.command")
                || routingKey.equals(topicRoot + "/command")
                || routingKey.startsWith(topicRoot + "/command/")
                || routingKey.equals(topicRoot + "/command/#")
                || routingKey.equals(topicRoot + "/rpc")
                || routingKey.startsWith(topicRoot + "/rpc/")
                || routingKey.equals(topicRoot + "/rpc/#")
                || routingKey.equals(topicRoot + ".command")
                || routingKey.startsWith(topicRoot + ".command.")
                || routingKey.equals(topicRoot + ".command.#")
                || routingKey.equals(topicRoot + ".rpc")
                || routingKey.startsWith(topicRoot + ".rpc.")
                || routingKey.equals(topicRoot + ".rpc.#")
                || isOpenBekenReadableCommandRoutingKey(topicRoot, routingKey, platform);
    }

    private boolean isWritableStatusRoutingKey(
            String topicRoot,
            String routingKey,
            boolean finalDevice,
            DevicePlatform platform
    ) {
        if (finalDevice) {
            return routingKey.equals(topicRoot + "/online")
                    || routingKey.startsWith(topicRoot + "/state/")
                    || routingKey.startsWith(topicRoot + "/telemetry/")
                    || routingKey.equals(topicRoot + "/events/rpc")
                    || routingKey.startsWith(topicRoot + "/events/rpc/")
                    || routingKey.equals(topicRoot + "/debug/log")
                    || routingKey.startsWith(topicRoot + "/debug/log/")
                    || routingKey.equals(topicRoot + ".online")
                    || routingKey.startsWith(topicRoot + ".state.")
                    || routingKey.startsWith(topicRoot + ".telemetry.")
                    || routingKey.equals(topicRoot + ".events.rpc")
                    || routingKey.startsWith(topicRoot + ".events.rpc.")
                    || routingKey.equals(topicRoot + ".debug.log")
                    || routingKey.startsWith(topicRoot + ".debug.log.")
                    || isOpenBekenWritableStatusRoutingKey(topicRoot, routingKey, platform);
        }
        return routingKey.equals(topicRoot + "/state")
                || routingKey.startsWith(topicRoot + "/telemetry/")
                || routingKey.startsWith(topicRoot + "/status/")
                || routingKey.equals(topicRoot + ".state")
                || routingKey.startsWith(topicRoot + ".telemetry.")
                || routingKey.startsWith(topicRoot + ".status.");
    }

    private boolean isOpenBekenReadableCommandRoutingKey(String topicRoot, String routingKey, DevicePlatform platform) {
        if (platform != DevicePlatform.OPENBEKEN) {
            return false;
        }
        return routingKey.equals(topicRoot + "/+/set")
                || routingKey.equals(topicRoot + ".*.set")
                || routingKey.equals(topicRoot + "/+/get")
                || routingKey.equals(topicRoot + ".*.get")
                || routingKey.equals("homeassistant/+")
                || routingKey.equals("homeassistant.*")
                || routingKey.startsWith("cmnd/" + topicRoot + "/")
                || routingKey.equals("cmnd/" + topicRoot + "/#")
                || routingKey.startsWith("cmnd." + topicRoot + ".")
                || routingKey.equals("cmnd." + topicRoot + ".#");
    }

    private boolean isOpenBekenWritableStatusRoutingKey(String topicRoot, String routingKey, DevicePlatform platform) {
        if (platform != DevicePlatform.OPENBEKEN) {
            return false;
        }
        return routingKey.equals(topicRoot + ".connected")
                || routingKey.equals(topicRoot + "/connected")
                || routingKey.matches("^" + java.util.regex.Pattern.quote(topicRoot) + "[./][^./]+$")
                || routingKey.matches("^" + java.util.regex.Pattern.quote(topicRoot) + "[./][^./]+[./]get$");
    }

}

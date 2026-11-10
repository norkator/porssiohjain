/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.DemoAccountGuard;
import com.nitramite.porssiohjain.services.DeviceService;
import com.nitramite.porssiohjain.services.DeviceOfflineNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TuyaService {
    private final TuyaApiClient api;
    private final ObjectMapper mapper;
    private final TuyaConnectionRepository connections;
    private final TuyaDeviceRepository devices;
    private final AccountRepository accounts;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final DemoAccountGuard demoGuard;
    private final DeviceMeasurementRepository measurements;
    private final DeviceOfflineNotificationService notifications;
    // Shared by cloud-snapshot freshness, planner contact checks, and API offline detection.
    public static final Duration POLL_FRESHNESS = Duration.ofHours(1);
    private final Map<Long, PendingLogin> pendingLogins = new ConcurrentHashMap<>();
    private static final Set<String> TEMPERATURE_CODES = Set.of("va_temperature", "temp_current", "temp_current_f",
            "ext_temp", "temp_current_external", "temp_current_external_1", "temp_current_external_2", "temp_current_external_3");
    private static final Set<String> HUMIDITY_CODES = Set.of("va_humidity", "humidity_value");

    @Transactional(readOnly = true)
    public List<ConnectionStatus> listConnections(Long accountId) {
        requireAccountId(accountId);
        return connections.findByAccountIdOrderByIdAsc(accountId).stream().map(this::connectionStatus).toList();
    }

    private ConnectionStatus connectionStatus(TuyaConnectionEntity connection) {
        return new ConnectionStatus(connection.getId(), connection.getSessionData() != null,
                Objects.toString(connection.getAccountLabel(), ""));
    }

    @Transactional
    public void renameConnection(Long accountId, Long connectionId, String label) {
        demoGuard.assertWritable(accountId);
        TuyaConnectionEntity connection = ownedConnection(accountId, connectionId);
        connection.setAccountLabel(requireLabel(label));
        connections.save(connection);
    }

    @Transactional(readOnly = true)
    public ConnectionStatus deviceConnection(Long accountId, Long deviceId) {
        requireAccountId(accountId);
        return devices.findByDeviceIdAndConnectionAccountId(deviceId, accountId)
                .map(link -> connectionStatus(link.getConnection()))
                .orElseThrow(() -> new TuyaException("tuya.error.device"));
    }

    @Transactional
    public QrLogin beginLogin(Long accountId, Long connectionId, String label, String userCode) {
        requireAccountId(accountId);
        demoGuard.assertWritable(accountId);
        if (connectionId != null) ownedConnection(accountId, connectionId);
        String name = requireLabel(label);
        String code = userCode == null ? "" : userCode.trim();
        if (code.isEmpty() || code.length() > 128) throw new TuyaException("tuya.error.userCode");
        pendingLogins.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        String token = api.createQrCode(code);
        PendingLogin pending = new PendingLogin(UUID.randomUUID(), connectionId, name, code, token, Instant.now().plusSeconds(120));
        pendingLogins.put(accountId, pending);
        return new QrLogin(pending.id(), "tuyaSmart--qrLogin?token=" + token, pending.expiresAt());
    }

    @Transactional
    public ConnectionStatus finishLogin(Long accountId, UUID attemptId) {
        requireAccountId(accountId);
        demoGuard.assertWritable(accountId);
        // Lock the account too: the first connection does not yet have a row to lock.
        AccountEntity account = accounts.findWithLockById(accountId).orElseThrow(() -> new TuyaException("tuya.error.account"));
        PendingLogin pending = pendingLogins.get(accountId);
        if (pending == null || !pending.id().equals(attemptId) || !pending.expiresAt().isAfter(Instant.now()))
            throw new TuyaException("tuya.error.qrExpired");
        JsonNode result = api.loginResult(pending.userCode(), pending.token());
        if (!result.path("success").asBoolean(false))
            throw new TuyaException("tuya.error.loginPending", result.path("code").asText(""));
        TuyaSession session = api.sessionFromLogin(pending.userCode(), result);
        TuyaConnectionEntity connection = pending.connectionId() == null
                ? connections.findByAccountIdAndProviderUserId(accountId, session.userId()).orElseGet(TuyaConnectionEntity::new)
                : ownedConnection(accountId, pending.connectionId());
        if (connection.getProviderUserId() != null && !connection.getProviderUserId().equals(session.userId())) throw new TuyaException("tuya.error.accountChanged");
        connection.setAccount(account);
        connection.setProviderUserId(session.userId());
        connection.setAccountLabel(pending.label());
        connection.setSessionData(writeSession(session));
        connections.save(connection);
        pendingLogins.remove(accountId, pending);
        return connectionStatus(connection);
    }

    public void cancelLogin(Long accountId, UUID attemptId) {
        if (accountId != null) pendingLogins.computeIfPresent(accountId,
                (key, pending) -> pending.id().equals(attemptId) ? null : pending);
    }

    @Transactional(noRollbackFor = TuyaException.class)
    public void disconnect(Long accountId, Long connectionId) {
        requireAccountId(accountId);
        demoGuard.assertWritable(accountId);
        TuyaConnectionEntity connection = requireConnection(accountId, connectionId);
        // Revoke before clearing local tokens. A failed request leaves a retryable connection.
        api.revoke(refresh(connection));
        connection.setSessionData(null);
        connections.save(connection);
        pendingLogins.computeIfPresent(accountId,
                (key, pending) -> Objects.equals(pending.connectionId(), connectionId) ? null : pending);
        for (TuyaDeviceEntity link : devices.findByConnectionId(connection.getId())) {
            link.getDevice().setApiOnline(false);
            link.getDevice().setMqttOnline(false);
            deviceRepository.save(link.getDevice());
        }
    }

    @Transactional(noRollbackFor = TuyaException.class)
    public List<Home> homes(Long accountId, Long connectionId) {
        TuyaSession session = refresh(requireConnection(accountId, connectionId));
        JsonNode result = api.get(session, "/v1.0/m/life/users/homes", Map.of());
        requireArray(result);
        List<Home> homes = new ArrayList<>();
        for (JsonNode home : result) homes.add(new Home(TuyaApiClient.required(home, "ownerId"), home.path("name").asText("")));
        return List.copyOf(homes);
    }

    @Transactional(noRollbackFor = TuyaException.class)
    public List<CloudDevice> devices(Long accountId, Long connectionId, String homeId) {
        checkId(homeId);
        TuyaSession session = refresh(requireConnection(accountId, connectionId));
        JsonNode result = api.get(session, "/v1.0/m/life/ha/home/devices", Map.of("homeId", homeId));
        requireArray(result);
        List<CloudDevice> found = new ArrayList<>();
        for (JsonNode device : result) found.add(cloudDevice(device));
        return List.copyOf(found);
    }

    @Transactional(noRollbackFor = TuyaException.class)
    public void importDevice(Long accountId, Long connectionId, String externalId, String name, String timezone, boolean enabled) {
        requireAccountId(accountId);
        demoGuard.assertWritable(accountId);
        checkId(externalId);
        if (name == null || name.isBlank() || name.length() > 255) throw new TuyaException("tuya.error.name");
        try { ZoneId.of(timezone); } catch (Exception e) { throw new TuyaException("tuya.error.timezone"); }
        // Serialize imports across this account's connections, including shared devices and quotas.
        accounts.findWithLockById(accountId).orElseThrow(() -> new TuyaException("tuya.error.account"));
        TuyaConnectionEntity connection = requireConnection(accountId, connectionId);
        TuyaSession session = refresh(connection);
        if (devices.existsByConnectionAccountIdAndTuyaDeviceId(accountId, externalId)) throw new TuyaException("tuya.error.duplicate");
        JsonNode detail = detail(session, externalId);
        JsonNode specs = specifications(session, externalId);
        // Relay capability is independent of temperature: a relay may expose both.
        boolean relay = !relayCodes(detail, specs).isEmpty();
        if (!relay && !cloudDevice(detail).temperatureSensor() && !hasTemperature(specs.path("status")))
            throw new TuyaException("tuya.error.unsupported");
        var localDevice = deviceService.createDevice(accountId, accountId, name.trim(), timezone,
                relay ? DeviceType.STANDARD : DeviceType.TEMPERATURE_SENSOR, DevicePlatform.TUYA_SMART, enabled,
                null, AcType.NONE, null, null, null, null);
        TuyaDeviceEntity link = new TuyaDeviceEntity();
        link.setConnection(connection);
        link.setDevice(deviceRepository.findById(localDevice.getId()).orElseThrow());
        link.setTuyaDeviceId(externalId);
        devices.save(link);
    }

    @Transactional(noRollbackFor = TuyaException.class)
    public Snapshot poll(Long accountId, Long deviceId) {
        requireAccountId(accountId);
        TuyaDeviceEntity link = devices.findByDeviceIdAndConnectionAccountId(deviceId, accountId)
                .orElseThrow(() -> new TuyaException("tuya.error.device"));
        if (link.getDevice().getDevicePlatform() != DevicePlatform.TUYA_SMART
                || !Objects.equals(link.getDevice().getAccount().getId(), accountId)) throw new TuyaException("tuya.error.device");
        try {
            TuyaSession session = refresh(requireConnection(accountId, link.getConnection().getId()));
            JsonNode detail = detail(session, link.getTuyaDeviceId());
            JsonNode specs = specifications(session, link.getTuyaDeviceId());
            Instant fetchedAt = Instant.now();
            List<Reading> readings = decodeReadings(detail, specs);
            boolean online = detail.path("online").asBoolean(false);
            recordConnectivity(link.getDevice(), online && link.getDevice().isEnabled(), fetchedAt);
            // Offline devices can still return cached state. Do not turn it into fresh observations.
            if (online && link.getDevice().isEnabled()) saveMeasurements(link.getDevice(), detail, readings, fetchedAt);
            return new Snapshot(online, fetchedAt, readings);
        } catch (TuyaException error) {
            recordConnectivity(link.getDevice(), false, null);
            throw error;
        }
    }

    private void recordConnectivity(DeviceEntity device, boolean online, Instant fetchedAt) {
        boolean wasApiOnline = device.isApiOnline();
        boolean wasMqttOnline = device.isMqttOnline();
        device.setApiOnline(online);
        device.setMqttOnline(false);
        if (fetchedAt != null) device.setLastCommunication(fetchedAt);
        deviceRepository.save(device);
        if (online) notifications.sendIfDeviceCameOnline(device, wasApiOnline, wasMqttOnline, "API", Instant.now());
        else notifications.sendIfDeviceWentOffline(device, wasApiOnline, wasMqttOnline, "API", Instant.now());
    }

    private void saveMeasurements(DeviceEntity device, JsonNode detail, List<Reading> readings, Instant fetchedAt) {
        Map<String, JsonNode> statuses = new HashMap<>();
        for (JsonNode status : detail.path("status")) statuses.put(status.path("code").asText(), status);
        // The device picker selects a sensor, not a thermostat channel. Prefer the standard ambient code.
        Reading temperature = null;
        for (String code : List.of("va_temperature", "temp_current", "temp_current_f")) {
            temperature = readings.stream().filter(r -> r.code().equals(code) && validTemperature(r)).findFirst().orElse(null);
            if (temperature != null) break;
        }
        if (temperature == null) {
            List<Reading> candidates = readings.stream().filter(r -> TEMPERATURE_CODES.contains(r.code()) && validTemperature(r)).toList();
            if (candidates.size() == 1) temperature = candidates.getFirst();
        }
        for (Reading reading : readings) {
            if (reading.value().isBlank()) continue;
            DeviceMeasurementType type;
            String key;
            String unit;
            BigDecimal value;
            try { value = new BigDecimal(reading.value()); } catch (NumberFormatException ignored) { continue; }
            if (TEMPERATURE_CODES.contains(reading.code()) && validTemperature(reading)) {
                type = DeviceMeasurementType.TEMPERATURE;
                key = reading == temperature ? "temperature" : reading.code();
                unit = "°C";
                if (reading.unit().equals("°F")) value = value.subtract(new BigDecimal("32"))
                        .multiply(new BigDecimal("5")).divide(new BigDecimal("9"), 3, RoundingMode.HALF_UP);
                if (value.compareTo(new BigDecimal("-100")) < 0 || value.compareTo(new BigDecimal("150")) > 0) continue;
            } else if (HUMIDITY_CODES.contains(reading.code()) && reading.unit().equals("%")) {
                type = DeviceMeasurementType.HUMIDITY; key = "humidity"; unit = "%";
            } else if (reading.code().equals("battery_percentage") && reading.unit().equals("%")) {
                type = DeviceMeasurementType.BATTERY_PERCENTAGE; key = "batteryPercentage"; unit = "%";
            } else continue;
            if (type != DeviceMeasurementType.TEMPERATURE && (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0)) continue;
            JsonNode status = statuses.get(reading.code());
            Instant measuredAt = reportTime(status, fetchedAt);
            // Invalid/future provider timestamps must not become fresh timestamp-free snapshots.
            if (status != null && status.has("t") && measuredAt == null) continue;
            Instant observedAt = measuredAt == null ? fetchedAt : measuredAt;
            Optional<DeviceMeasurementEntity> previous = measurements
                    .findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(device.getId(), type, key);
            // Repeated reads of the same timestamped report do not create new evidence.
            if (measuredAt != null && previous.isPresent() && previous.get().getMeasuredAt() != null
                    && !measuredAt.isAfter(previous.get().getMeasuredAt())) continue;
            measurements.save(DeviceMeasurementEntity.builder().account(device.getAccount()).device(device)
                    .source(DevicePlatform.TUYA_SMART).sourceKey(reading.code()).measurementType(type).measurementKey(key)
                    .value(value.setScale(3, RoundingMode.HALF_UP)).unit(unit).measuredAt(measuredAt)
                    .observedAt(observedAt).receivedAt(fetchedAt).build());
        }
    }

    private boolean validTemperature(Reading reading) {
        return !reading.value().isBlank() && Set.of("°C", "°F").contains(reading.unit());
    }

    private Instant reportTime(JsonNode status, Instant fetchedAt) {
        // Only use a property report timestamp. Device update_time can describe unrelated changes.
        if (status == null || !status.path("t").isIntegralNumber()) return null;
        long timestamp = status.path("t").asLong();
        try {
            Instant time = timestamp < 100_000_000_000L ? Instant.ofEpochSecond(timestamp) : Instant.ofEpochMilli(timestamp);
            return timestamp > 0 && !time.isAfter(fetchedAt) ? time : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private TuyaConnectionEntity ownedConnection(Long accountId, Long connectionId) {
        requireAccountId(accountId);
        if (connectionId == null) throw new TuyaException("tuya.error.notConnected");
        return connections.lockByIdAndAccountId(connectionId, accountId)
                .orElseThrow(() -> new TuyaException("tuya.error.notConnected"));
    }

    private TuyaConnectionEntity requireConnection(Long accountId, Long connectionId) {
        TuyaConnectionEntity connection = ownedConnection(accountId, connectionId);
        if (connection.getSessionData() == null) throw new TuyaException("tuya.error.notConnected");
        return connection;
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank() || label.trim().length() > 255)
            throw new TuyaException("tuya.error.accountName");
        return label.trim();
    }

    private TuyaSession refresh(TuyaConnectionEntity connection) {
        TuyaSession session = readSession(connection.getSessionData());
        if (!session.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            session = api.refresh(session);
            connection.setSessionData(writeSession(session));
            connections.save(connection);
        }
        return session;
    }

    private JsonNode detail(TuyaSession session, String externalId) {
        JsonNode result = api.get(session, "/v1.0/m/life/ha/devices/detail", Map.of("devIds", externalId));
        requireArray(result);
        for (JsonNode device : result) if (externalId.equals(device.path("id").asText())) return device;
        throw new TuyaException("tuya.error.device");
    }

    private JsonNode specifications(TuyaSession session, String id) {
        checkId(id);
        return api.get(session, "/v1.1/m/life/" + id + "/specifications", Map.of());
    }

    private CloudDevice cloudDevice(JsonNode device) {
        String category = device.path("category").asText("");
        return new CloudDevice(TuyaApiClient.required(device, "id"), device.path("name").asText(""), category,
                device.path("online").asBoolean(false), hasTemperature(device.path("status"))
                        || Set.of("wsdcg", "qxj").contains(category), relayCodes(device, mapper.createObjectNode()));
    }

    private static boolean hasTemperature(JsonNode properties) {
        for (JsonNode property : properties) if (TEMPERATURE_CODES.contains(property.path("code").asText())) return true;
        return false;
    }

    private static boolean relayCode(String code) {
        return code.matches("switch(?:_[1-9][0-9]*)?");
    }

    private static List<String> relayCodes(JsonNode detail, JsonNode specs) {
        Set<String> codes = new TreeSet<>();
        for (JsonNode status : detail.path("status")) {
            String code = status.path("code").asText();
            if (relayCode(code) && status.path("value").isBoolean()) codes.add(code);
        }
        for (String section : List.of("status", "functions")) for (JsonNode definition : specs.path(section)) {
            String code = definition.path("code").asText();
            if (relayCode(code) && "Boolean".equalsIgnoreCase(definition.path("type").asText())) codes.add(code);
        }
        return List.copyOf(codes);
    }

    List<Reading> decodeReadings(JsonNode detail, JsonNode specifications) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (JsonNode status : detail.path("status")) values.put(status.path("code").asText(), status.path("value"));
        Map<String, JsonNode> definitions = new LinkedHashMap<>();
        for (JsonNode definition : specifications.path("status")) definitions.put(definition.path("code").asText(), definition);
        Set<String> codes = new LinkedHashSet<>(definitions.keySet());
        codes.addAll(values.keySet());
        List<String> relays = relayCodes(detail, specifications);
        codes.addAll(relays);
        List<Reading> readings = new ArrayList<>();
        for (String code : codes) {
            if (relays.contains(code)) {
                JsonNode value = values.get(code);
                readings.add(new Reading(code, value != null && value.isBoolean() ? value.asText() : "", ""));
                continue;
            }
            if (!TEMPERATURE_CODES.contains(code) && !HUMIDITY_CODES.contains(code)
                    && !Set.of("battery_percentage", "battery_state").contains(code)) continue;
            JsonNode value = values.get(code);
            if (code.equals("battery_state")) {
                readings.add(new Reading(code, value != null && value.isTextual() ? value.asText() : "", ""));
                continue;
            }
            String display = "";
            String unit = "";
            try {
                JsonNode definition = definitions.get(code);
                if (definition != null && "Integer".equalsIgnoreCase(definition.path("type").asText()) && value != null && value.isNumber()) {
                    JsonNode range = definition.path("values");
                    if (range.isTextual()) range = mapper.readTree(range.asText());
                    if (range != null && range.path("scale").isIntegralNumber() && range.path("scale").canConvertToInt()) {
                        int scale = range.path("scale").intValue();
                        if (scale < 0 || scale > 9) throw new IllegalArgumentException();
                        BigDecimal scaled = value.decimalValue().movePointLeft(scale);
                        display = scaled.stripTrailingZeros().toPlainString();
                        unit = range.path("unit").asText("");
                        if (TEMPERATURE_CODES.contains(code) && unit.isBlank())
                            unit = values.getOrDefault("temp_unit_convert", mapper.nullNode()).asText("");
                        if (Set.of("c", "°c", "℃", "celsius").contains(unit.toLowerCase(Locale.ROOT))) unit = "°C";
                        if (Set.of("f", "°f", "℉", "fahrenheit").contains(unit.toLowerCase(Locale.ROOT))) unit = "°F";
                        if (unit.isBlank() && (HUMIDITY_CODES.contains(code) || code.equals("battery_percentage"))) unit = "%";
                    }
                }
            } catch (Exception ignored) { display = ""; unit = ""; }
            readings.add(new Reading(code, display, unit));
        }
        return List.copyOf(readings);
    }

    private String writeSession(TuyaSession session) {
        ObjectNode json = mapper.createObjectNode();
        json.put("userCode", session.userCode()).put("endpoint", session.endpoint()).put("terminalId", session.terminalId())
                .put("userId", session.userId()).put("accessToken", session.accessToken()).put("refreshToken", session.refreshToken())
                .put("expiresAt", session.expiresAt().toEpochMilli());
        return json.toString();
    }

    private TuyaSession readSession(String json) {
        try {
            JsonNode data = mapper.readTree(json);
            return new TuyaSession(TuyaApiClient.required(data, "userCode"), TuyaApiClient.required(data, "endpoint"),
                    TuyaApiClient.required(data, "terminalId"), TuyaApiClient.required(data, "userId"),
                    TuyaApiClient.required(data, "accessToken"), TuyaApiClient.required(data, "refreshToken"),
                    Instant.ofEpochMilli(data.path("expiresAt").asLong()));
        } catch (Exception e) { throw new TuyaException("tuya.error.notConnected"); }
    }

    private static void requireAccountId(Long accountId) {
        if (accountId == null) throw new TuyaException("tuya.error.account");
    }
    private static void checkId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new TuyaException("tuya.error.device");
    }
    private static void requireArray(JsonNode result) {
        if (!result.isArray()) throw new TuyaException("tuya.error.response");
    }

    private record PendingLogin(UUID id, Long connectionId, String label, String userCode, String token, Instant expiresAt) {
        @Override public String toString() { return "PendingLogin[redacted]"; }
    }
    public record QrLogin(UUID id, String payload, Instant expiresAt) {
        @Override public String toString() { return "QrLogin[redacted]"; }
    }
    public record ConnectionStatus(Long id, boolean connected, String label) { }
    public record Home(String id, String name) { }
    public record CloudDevice(String id, String name, String category, boolean online, boolean temperatureSensor, List<String> relayCodes) {
        public DeviceType deviceType() { return relayCodes.isEmpty() ? DeviceType.TEMPERATURE_SENSOR : DeviceType.STANDARD; }
    }
    public record Reading(String code, String value, String unit) { }
    public record Snapshot(boolean online, Instant fetchedAt, List<Reading> readings) { }
}

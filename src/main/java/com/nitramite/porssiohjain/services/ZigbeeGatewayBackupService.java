package com.nitramite.porssiohjain.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.services.models.ZigbeeGatewayBackup;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ZigbeeGatewayBackupService {
    private static final int MAX_DEVICES = 500;
    private final AccountRepository accountRepository;
    private final ZigbeeGatewayBackupRepository backupRepository;
    private final ObjectMapper objectMapper;

    public ZigbeeGatewayBackup save(Long accountId, String pathIeee, ZigbeeGatewayBackup request) {
        String ieee = normalizeIeee(pathIeee);
        if (request == null || request.getGatewayId() == null
                || !ieee.equals(normalizeIeee(request.getCoordinatorIeee()))) throw badRequest("Coordinator IEEE mismatch");
        validateNetwork(request);
        List<Map<String, Object>> devices = request.getDevices() == null ? List.of() : request.getDevices();
        if (devices.size() > MAX_DEVICES) throw badRequest("Too many Zigbee devices in backup");
        validateDevices(devices);
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        backupRepository.findByGatewayId(request.getGatewayId()).ifPresent(existing -> {
            if (!Objects.equals(existing.getAccount().getId(), accountId)
                    || !existing.getCoordinatorIeee().equals(ieee)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        });
        ZigbeeGatewayBackupEntity entity = backupRepository.findByAccountIdAndCoordinatorIeee(accountId, ieee)
                .orElseGet(() -> ZigbeeGatewayBackupEntity.builder().account(account).coordinatorIeee(ieee)
                        .createdAt(Instant.now()).revision(0).build());
        if (request.getRevision() > 0 && request.getRevision() != entity.getRevision())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Backup revision is stale");
        entity.setGatewayId(request.getGatewayId()); entity.setPanId(request.getPanId());
        entity.setExtendedPanId(normalizeIeee(request.getExtendedPanId())); entity.setChannel(request.getChannel());
        entity.setBackupVersion(1); entity.setRevision(entity.getRevision() + 1); entity.setUpdatedAt(Instant.now());
        try { entity.setDevicesJson(objectMapper.writeValueAsString(devices)); }
        catch (Exception error) { throw badRequest("Invalid device backup"); }
        return response(backupRepository.save(entity));
    }

    public ZigbeeGatewayBackup get(Long accountId, String ieee) {
        return response(backupRepository.findByAccountIdAndCoordinatorIeee(accountId, normalizeIeee(ieee))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public List<ZigbeeGatewayBackup> list(Long accountId) {
        return backupRepository.findByAccountIdOrderByUpdatedAtDesc(accountId).stream().map(this::response).toList();
    }

    private ZigbeeGatewayBackup response(ZigbeeGatewayBackupEntity entity) {
        ZigbeeGatewayBackup value = new ZigbeeGatewayBackup();
        value.setBackupVersion(entity.getBackupVersion()); value.setRevision(entity.getRevision());
        value.setGatewayId(entity.getGatewayId()); value.setCoordinatorIeee(entity.getCoordinatorIeee());
        value.setPanId(entity.getPanId()); value.setExtendedPanId(entity.getExtendedPanId());
        value.setChannel(entity.getChannel()); value.setUpdatedAt(entity.getUpdatedAt());
        try { value.setDevices(objectMapper.readValue(entity.getDevicesJson(), new TypeReference<>() {})); }
        catch (Exception error) { throw new IllegalStateException("Stored Zigbee backup is invalid", error); }
        return value;
    }

    private static void validateNetwork(ZigbeeGatewayBackup value) {
        if (value.getBackupVersion() != 1 || value.getPanId() < 1 || value.getPanId() > 0xfffd
                || value.getChannel() < 11 || value.getChannel() > 26) throw badRequest("Invalid Zigbee network backup");
        normalizeIeee(value.getExtendedPanId());
    }

    private static void validateDevices(List<Map<String, Object>> devices) {
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> device : devices) {
            String ieee = normalizeIeee(Objects.toString(device.get("ieeeAddress"), ""));
            if (!seen.add(ieee)) throw badRequest("Duplicate Zigbee device");
            String name = Objects.toString(device.get("customName"), "");
            if (name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) throw badRequest("Invalid device name");
        }
    }

    private static String normalizeIeee(String value) { return ZigbeeGatewaySyncService.normalizeIeee(value); }
    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}

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

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.LoadSheddingLinkEntity;
import com.nitramite.porssiohjain.entity.LoadSheddingNodeEntity;
import com.nitramite.porssiohjain.entity.enums.ControlAction;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.LoadSheddingTriggerState;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.entity.repository.LoadSheddingLinkRepository;
import com.nitramite.porssiohjain.entity.repository.LoadSheddingNodeRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.models.LoadSheddingLinkResponse;
import com.nitramite.porssiohjain.services.models.LoadSheddingNodeResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoadSheddingService {

    private static final int MIN_CHANNEL = 0;
    private static final int MAX_CHANNEL = 3;

    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final LoadSheddingNodeRepository loadSheddingNodeRepository;
    private final LoadSheddingLinkRepository loadSheddingLinkRepository;

    @Transactional(readOnly = true)
    public List<LoadSheddingNodeResponse> getNodes(Long accountId) {
        validateAccount(accountId);
        return loadSheddingNodeRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(this::mapNode)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoadSheddingLinkResponse> getLinks(Long accountId) {
        validateAccount(accountId);
        return loadSheddingLinkRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(this::mapLink)
                .toList();
    }

    @Transactional
    public LoadSheddingNodeResponse saveNode(Long accountId, Long nodeId, Long deviceId, int channel, int canvasX, int canvasY) {
        validateChannel(channel);
        AccountEntity account = validateAccount(accountId);
        DeviceEntity device = validateStandardDevice(accountId, deviceId);

        LoadSheddingNodeEntity entity = nodeId != null
                ? loadSheddingNodeRepository.findByIdAndAccountId(nodeId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Load shedding node not found: " + nodeId))
                : loadSheddingNodeRepository.findByAccountIdAndDeviceIdAndDeviceChannel(accountId, deviceId, channel)
                .orElseGet(LoadSheddingNodeEntity::new);

        entity.setAccount(account);
        entity.setDevice(device);
        entity.setDeviceChannel(channel);
        entity.setCanvasX(Math.max(0, canvasX));
        entity.setCanvasY(Math.max(0, canvasY));
        return mapNode(loadSheddingNodeRepository.save(entity));
    }

    @Transactional
    public void updateNodePosition(Long accountId, Long nodeId, int canvasX, int canvasY) {
        LoadSheddingNodeEntity entity = loadSheddingNodeRepository.findByIdAndAccountId(nodeId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Load shedding node not found: " + nodeId));
        entity.setCanvasX(Math.max(0, canvasX));
        entity.setCanvasY(Math.max(0, canvasY));
        loadSheddingNodeRepository.save(entity);
    }

    @Transactional
    public void deleteNode(Long accountId, Long nodeId) {
        LoadSheddingNodeEntity node = loadSheddingNodeRepository.findByIdAndAccountId(nodeId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Load shedding node not found: " + nodeId));
        loadSheddingLinkRepository.deleteAll(loadSheddingLinkRepository.findBySourceNode(node));
        loadSheddingLinkRepository.deleteAll(loadSheddingLinkRepository.findByTargetNode(node));
        loadSheddingNodeRepository.delete(node);
    }

    @Transactional
    public LoadSheddingLinkResponse saveLink(
            Long accountId,
            Long linkId,
            Long sourceNodeId,
            Long targetNodeId,
            LoadSheddingTriggerState triggerState,
            ControlAction targetAction,
            boolean reverseOnClear
    ) {
        if (triggerState == null) {
            throw new IllegalArgumentException("Trigger state is required");
        }
        if (targetAction != ControlAction.TURN_ON && targetAction != ControlAction.TURN_OFF) {
            throw new IllegalArgumentException("Load shedding supports only turn on / turn off actions");
        }

        LoadSheddingNodeEntity sourceNode = loadSheddingNodeRepository.findByIdAndAccountId(sourceNodeId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Source node not found: " + sourceNodeId));
        LoadSheddingNodeEntity targetNode = loadSheddingNodeRepository.findByIdAndAccountId(targetNodeId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Target node not found: " + targetNodeId));

        if (sourceNode.getId().equals(targetNode.getId())) {
            throw new IllegalArgumentException("Source and target cannot be the same node");
        }

        LoadSheddingLinkEntity entity = linkId != null
                ? loadSheddingLinkRepository.findByIdAndAccountId(linkId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Load shedding link not found: " + linkId))
                : new LoadSheddingLinkEntity();

        entity.setAccount(validateAccount(accountId));
        entity.setSourceNode(sourceNode);
        entity.setTargetNode(targetNode);
        entity.setTriggerState(triggerState);
        entity.setTargetAction(targetAction);
        entity.setReverseOnClear(reverseOnClear);

        return mapLink(loadSheddingLinkRepository.save(entity));
    }

    @Transactional
    public void deleteLink(Long accountId, Long linkId) {
        LoadSheddingLinkEntity link = loadSheddingLinkRepository.findByIdAndAccountId(linkId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("Load shedding link not found: " + linkId));
        loadSheddingLinkRepository.delete(link);
    }

    private AccountEntity validateAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));
    }

    private DeviceEntity validateStandardDevice(Long accountId, Long deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceId));
        if (!device.getAccount().getId().equals(accountId)) {
            throw new IllegalStateException("Forbidden!");
        }
        if (device.getDeviceType() != DeviceType.STANDARD) {
            throw new IllegalArgumentException("Only standard devices are supported in load shedding");
        }
        return device;
    }

    private void validateChannel(int channel) {
        if (channel < MIN_CHANNEL || channel > MAX_CHANNEL) {
            throw new IllegalArgumentException("Unsupported relay channel: " + channel);
        }
    }

    private LoadSheddingNodeResponse mapNode(LoadSheddingNodeEntity entity) {
        DeviceEntity device = entity.getDevice();
        DeviceResponse deviceResponse = DeviceResponse.builder()
                .id(device.getId())
                .uuid(device.getUuid())
                .deviceType(device.getDeviceType())
                .enabled(device.isEnabled())
                .deviceName(device.getDeviceName())
                .timezone(device.getTimezone())
                .lastCommunication(device.getLastCommunication())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .accountId(device.getAccount().getId())
                .apiOnline(device.isApiOnline())
                .mqttOnline(device.isMqttOnline())
                .mqttUsername(device.getMqttUsername())
                .mqttPassword(device.getMqttPassword())
                .build();

        return LoadSheddingNodeResponse.builder()
                .id(entity.getId())
                .device(deviceResponse)
                .deviceChannel(entity.getDeviceChannel())
                .canvasX(entity.getCanvasX())
                .canvasY(entity.getCanvasY())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LoadSheddingLinkResponse mapLink(LoadSheddingLinkEntity entity) {
        return LoadSheddingLinkResponse.builder()
                .id(entity.getId())
                .sourceNode(mapNode(entity.getSourceNode()))
                .targetNode(mapNode(entity.getTargetNode()))
                .triggerState(entity.getTriggerState())
                .targetAction(entity.getTargetAction())
                .reverseOnClear(entity.isReverseOnClear())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;
import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
import com.nitramite.porssiohjain.entity.enums.MqttDeviceProfile;
import com.nitramite.porssiohjain.entity.enums.OtaDeploymentStatus;
import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.mqtt.MqttService;
import com.nitramite.porssiohjain.services.models.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FactoryProvisioningService {

    private final FactoryTestRunRepository factoryTestRunRepository;
    private final FactoryTestStepResultRepository factoryTestStepResultRepository;
    private final OtaReleaseRepository otaReleaseRepository;
    private final OtaDeploymentRepository otaDeploymentRepository;
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final AccountLimitService accountLimitService;
    private final MqttProfileService mqttProfileService;
    private final ObjectProvider<MqttService> mqttServiceProvider;
    private final DemoAccountGuard demoAccountGuard;

    @Transactional(readOnly = true)
    public List<FactoryDeviceResponse> listFactoryDevices() {
        return deviceRepository.findBySerialNumberIsNotNullOrderByIdDesc().stream()
                .map(this::mapFactoryDevice)
                .toList();
    }

    @Transactional(readOnly = true)
    public FactoryDeviceResponse getFactoryDevice(Long id) {
        return mapFactoryDevice(getFactoryDeviceEntity(id));
    }

    @Transactional
    public FactoryDeviceResponse createFactoryDevice(CreateFactoryDeviceRequest request) {
        String serialNumber = requireText(request.getSerialNumber(), "serialNumber");
        deviceRepository.findBySerialNumber(serialNumber)
                .ifPresent(existing -> {
                    throw new DuplicateEntityException("Factory device already exists with serial number: " + serialNumber);
                });
        DeviceEntity entity = DeviceEntity.builder()
                .serialNumber(serialNumber)
                .chipId(request.getChipId())
                .deviceName(serialNumber)
                .timezone("UTC")
                .deviceType(DeviceType.STANDARD)
                .devicePlatform(request.getPlatform() == null ? com.nitramite.porssiohjain.entity.enums.DevicePlatform.GENERIC_MQTT : request.getPlatform())
                .mqttUsername(blankToNull(request.getMqttUsername()))
                .mqttPassword(blankToNull(request.getMqttPassword()))
                .mqttDeviceProfile(defaultProfile(request.getMqttDeviceProfile()))
                .claimCode(blankToNull(request.getClaimCode()))
                .factoryDeviceStatus(FactoryDeviceStatus.REGISTERED)
                .account(null)
                .build();
        return mapFactoryDevice(deviceRepository.save(entity));
    }

    @Transactional
    public FactoryDeviceResponse updateFactoryDevice(Long id, UpdateFactoryDeviceRequest request) {
        DeviceEntity entity = getFactoryDeviceEntity(id);
        if (request.getChipId() != null) {
            entity.setChipId(request.getChipId());
        }
        if (request.getFirmwareVersion() != null) {
            // Firmware version is intentionally no longer stored on DeviceEntity.
        }
        if (request.getMqttDeviceProfile() != null) {
            entity.setMqttDeviceProfile(request.getMqttDeviceProfile());
        }
        if (request.getClaimCode() != null) {
            entity.setClaimCode(requireText(request.getClaimCode(), "claimCode"));
        }
        if (request.getMetadataJson() != null) {
            // Metadata is intentionally no longer stored on DeviceEntity.
        }
        if (request.getStatus() != null) {
            entity.setFactoryDeviceStatus(request.getStatus());
        }
        return mapFactoryDevice(deviceRepository.save(entity));
    }

    @Transactional
    public FactoryTestRunResponse startTestRun(Long operatorAccountId, Long factoryDeviceId, CreateFactoryTestRunRequest request) {
        DeviceEntity factoryDevice = getFactoryDeviceEntity(factoryDeviceId);
        AccountEntity operator = accountRepository.findById(operatorAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + operatorAccountId));
        factoryDevice.setFactoryDeviceStatus(FactoryDeviceStatus.TESTING);
        deviceRepository.save(factoryDevice);

        FactoryTestRunEntity run = FactoryTestRunEntity.builder()
                .factoryDevice(factoryDevice)
                .operatorAccount(operator)
                .stationName(requireText(request.getStationName(), "stationName"))
                .notes(blankToNull(request.getNotes()))
                .status(FactoryTestStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        return mapTestRun(factoryTestRunRepository.save(run));
    }

    @Transactional
    public FactoryTestRunResponse addTestStep(Long runId, CreateFactoryTestStepRequest request) {
        FactoryTestRunEntity run = factoryTestRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundException("Factory test run not found: " + runId));
        FactoryTestStepResultEntity step = FactoryTestStepResultEntity.builder()
                .factoryTestRun(run)
                .stepKey(requireText(request.getStepKey(), "stepKey"))
                .status(request.getStatus() == null ? FactoryTestStatus.RUNNING : request.getStatus())
                .expectedValue(blankToNull(request.getExpectedValue()))
                .actualValue(blankToNull(request.getActualValue()))
                .details(blankToNull(request.getDetails()))
                .build();
        factoryTestStepResultRepository.save(step);

        if (Boolean.TRUE.equals(request.getFinalizeRun())) {
            finalizeTestRun(run, step.getStatus());
        }

        return mapTestRun(factoryTestRunRepository.save(run));
    }

    @Transactional
    public DeviceResponse claimFactoryDevice(Long factoryDeviceId, ClaimFactoryDeviceRequest request) {
        DeviceEntity factoryDevice = getFactoryDeviceEntity(factoryDeviceId);
        return claimFactoryDeviceInternal(factoryDevice, request.getAccountId(), request.getDeviceName(), request.getTimezone());
    }

    @Transactional(readOnly = true)
    public ProvisionedDeviceLookupResponse lookupProvisionedDevice(String claimCode) {
        DeviceEntity factoryDevice = deviceRepository.findByClaimCode(requireText(claimCode, "claimCode"))
                .orElseThrow(() -> new EntityNotFoundException("Provisioned device not found"));
        return mapProvisionedLookup(factoryDevice);
    }

    @Transactional
    public DeviceResponse claimProvisionedDevice(Long accountId, ClaimProvisionedDeviceRequest request) {
        demoAccountGuard.assertWritable(accountId);
        DeviceEntity factoryDevice = deviceRepository.findByClaimCode(requireText(request.getClaimCode(), "claimCode"))
                .orElseThrow(() -> new EntityNotFoundException("Provisioned device not found"));
        return claimFactoryDeviceInternal(factoryDevice, accountId, request.getDeviceName(), request.getTimezone());
    }

    private DeviceResponse claimFactoryDeviceInternal(
            DeviceEntity factoryDevice,
            Long accountId,
            String deviceName,
            String timezone
    ) {
        if (factoryDevice.getAccount() != null || factoryDevice.getFactoryDeviceStatus() == FactoryDeviceStatus.CLAIMED) {
            throw new DuplicateEntityException("Factory device already claimed");
        }
        if (factoryDevice.getFactoryDeviceStatus() != FactoryDeviceStatus.PASSED) {
            throw new IllegalArgumentException("Factory device must pass testing before claim");
        }
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        accountLimitService.assertCanCreateDevice(accountId);
        factoryDevice.setAccount(account);
        factoryDevice.setDeviceName(requireText(deviceName, "deviceName"));
        factoryDevice.setTimezone(requireText(timezone, "timezone"));
        factoryDevice.setFactoryDeviceStatus(FactoryDeviceStatus.CLAIMED);
        factoryDevice.setClaimedAt(Instant.now());
        return mapDeviceResponse(deviceRepository.save(factoryDevice));
    }

    @Transactional(readOnly = true)
    public List<OtaReleaseResponse> listOtaReleases() {
        return otaReleaseRepository.findAllByOrderByIdDesc().stream()
                .map(this::mapOtaRelease)
                .toList();
    }

    @Transactional
    public OtaReleaseResponse createOtaRelease(CreateOtaReleaseRequest request) {
        otaReleaseRepository.findByPlatformAndProductModelAndVersion(
                        request.getPlatform(),
                        requireText(request.getProductModel(), "productModel"),
                        requireText(request.getVersion(), "version")
                )
                .ifPresent(existing -> {
                    throw new DuplicateEntityException("OTA release already exists for platform/model/version");
                });
        OtaReleaseEntity release = OtaReleaseEntity.builder()
                .platform(request.getPlatform())
                .productModel(requireText(request.getProductModel(), "productModel"))
                .version(requireText(request.getVersion(), "version"))
                .binaryUrl(requireText(request.getBinaryUrl(), "binaryUrl"))
                .checksumSha256(blankToNull(request.getChecksumSha256()))
                .active(request.getActive() == null || request.getActive())
                .notes(blankToNull(request.getNotes()))
                .build();
        return mapOtaRelease(otaReleaseRepository.save(release));
    }

    @Transactional
    public OtaDeploymentResponse createOtaDeployment(Long requestedByAccountId, Long factoryDeviceId, CreateOtaDeploymentRequest request) {
        demoAccountGuard.assertWritable(requestedByAccountId);
        DeviceEntity factoryDevice = getFactoryDeviceEntity(factoryDeviceId);
        OtaReleaseEntity release = otaReleaseRepository.findById(request.getOtaReleaseId())
                .orElseThrow(() -> new EntityNotFoundException("OTA release not found: " + request.getOtaReleaseId()));
        AccountEntity requestedBy = accountRepository.findById(requestedByAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + requestedByAccountId));

        String commandTopic = factoryDevice.getUuid() + "/command";
        String commandPayload = buildOtaCommandPayload(factoryDevice, release, request.getCommandTemplate());

        OtaDeploymentEntity deployment = OtaDeploymentEntity.builder()
                .otaRelease(release)
                .factoryDevice(factoryDevice)
                .requestedByAccount(requestedBy)
                .status(OtaDeploymentStatus.REQUESTED)
                .commandTopic(commandTopic)
                .commandPayload(commandPayload)
                .requestedAt(Instant.now())
                .build();
        deployment = otaDeploymentRepository.save(deployment);

        MqttService mqttService = mqttServiceProvider.getIfAvailable();
        if (mqttService == null) {
            throw new IllegalStateException("MQTT service is not available");
        }
        mqttService.publish(commandTopic, commandPayload);

        return mapOtaDeployment(deployment);
    }

    @Transactional
    public void registerBootstrapMessage(String topic, String payload) {
        String topicRoot = extractTopicRoot(topic);
        parseUuid(topicRoot).flatMap(deviceRepository::findByUuidAndSerialNumberIsNotNull).ifPresent(device -> {
            device.setLastCommunication(Instant.now());
            device.setLastTelemetry(payload);
            deviceRepository.save(device);
        });
    }

    private void finalizeTestRun(FactoryTestRunEntity run, FactoryTestStatus finalStatus) {
        run.setStatus(finalStatus == FactoryTestStatus.FAILED ? FactoryTestStatus.FAILED : FactoryTestStatus.PASSED);
        run.setFinishedAt(Instant.now());
        DeviceEntity factoryDevice = run.getFactoryDevice();
        factoryDevice.setFactoryDeviceStatus(finalStatus == FactoryTestStatus.FAILED
                ? FactoryDeviceStatus.FAILED
                : FactoryDeviceStatus.PASSED);
        deviceRepository.save(factoryDevice);
    }

    private DeviceEntity getFactoryDeviceEntity(Long id) {
        return deviceRepository.findById(id)
                .filter(device -> device.getSerialNumber() != null)
                .orElseThrow(() -> new EntityNotFoundException("Factory device not found: " + id));
    }

    private FactoryDeviceResponse mapFactoryDevice(DeviceEntity entity) {
        List<FactoryTestRunResponse> testRuns = factoryTestRunRepository.findByFactoryDeviceOrderByStartedAtDesc(entity).stream()
                .map(this::mapTestRun)
                .toList();
        List<OtaDeploymentResponse> otaDeployments = otaDeploymentRepository.findByFactoryDeviceOrderByRequestedAtDesc(entity).stream()
                .map(this::mapOtaDeployment)
                .toList();
        return FactoryDeviceResponse.builder()
                .id(entity.getId())
                .serialNumber(entity.getSerialNumber())
                .chipId(entity.getChipId())
                .platform(entity.getDevicePlatform())
                .productModel(null)
                .firmwareVersion(null)
                .mqttTopicRoot(entity.getUuid() != null ? entity.getUuid().toString() : null)
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .mqttDeviceProfile(entity.getMqttDeviceProfile())
                .mqttCapabilities(mqttProfileService.getCapabilities(entity.getMqttDeviceProfile()))
                .claimCode(entity.getClaimCode())
                .status(entity.getFactoryDeviceStatus())
                .lastSeenAt(entity.getLastCommunication())
                .lastBootstrapPayload(entity.getLastTelemetry())
                .metadataJson(null)
                .claimedDeviceId(entity.getAccount() != null ? entity.getId() : null)
                .claimedAt(entity.getClaimedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .testRuns(testRuns)
                .otaDeployments(otaDeployments)
                .build();
    }

    private FactoryTestRunResponse mapTestRun(FactoryTestRunEntity entity) {
        List<FactoryTestStepResultResponse> steps = factoryTestStepResultRepository
                .findByFactoryTestRunOrderByCreatedAtAsc(entity).stream()
                .map(step -> FactoryTestStepResultResponse.builder()
                        .id(step.getId())
                        .stepKey(step.getStepKey())
                        .status(step.getStatus())
                        .expectedValue(step.getExpectedValue())
                        .actualValue(step.getActualValue())
                        .details(step.getDetails())
                        .createdAt(step.getCreatedAt())
                        .build())
                .toList();
        return FactoryTestRunResponse.builder()
                .id(entity.getId())
                .factoryDeviceId(entity.getFactoryDevice().getId())
                .operatorAccountId(entity.getOperatorAccount() != null ? entity.getOperatorAccount().getId() : null)
                .stationName(entity.getStationName())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .steps(steps)
                .build();
    }

    private OtaReleaseResponse mapOtaRelease(OtaReleaseEntity entity) {
        return OtaReleaseResponse.builder()
                .id(entity.getId())
                .platform(entity.getPlatform())
                .productModel(entity.getProductModel())
                .version(entity.getVersion())
                .binaryUrl(entity.getBinaryUrl())
                .checksumSha256(entity.getChecksumSha256())
                .active(entity.isActive())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private OtaDeploymentResponse mapOtaDeployment(OtaDeploymentEntity entity) {
        return OtaDeploymentResponse.builder()
                .id(entity.getId())
                .otaReleaseId(entity.getOtaRelease().getId())
                .factoryDeviceId(entity.getFactoryDevice() != null ? entity.getFactoryDevice().getId() : null)
                .deviceId(entity.getDevice() != null ? entity.getDevice().getId() : null)
                .requestedByAccountId(entity.getRequestedByAccount() != null ? entity.getRequestedByAccount().getId() : null)
                .status(entity.getStatus())
                .commandTopic(entity.getCommandTopic())
                .commandPayload(entity.getCommandPayload())
                .resultDetails(entity.getResultDetails())
                .requestedAt(entity.getRequestedAt())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }

    private String buildOtaCommandPayload(DeviceEntity factoryDevice, OtaReleaseEntity release, String commandTemplate) {
        if (commandTemplate != null && !commandTemplate.isBlank()) {
            return commandTemplate
                    .replace("{url}", release.getBinaryUrl())
                    .replace("{checksum}", release.getChecksumSha256() == null ? "" : release.getChecksumSha256())
                    .replace("{version}", release.getVersion())
                    .replace("{platform}", factoryDevice.getDevicePlatform().name())
                    .replace("{profile}", factoryDevice.getMqttDeviceProfile().name());
        }
        return mqttProfileService.buildDefaultOtaPayload(
                factoryDevice.getMqttDeviceProfile(),
                release.getBinaryUrl(),
                release.getVersion(),
                release.getChecksumSha256()
        );
    }

    private String extractTopicRoot(String topic) {
        int lastSlash = topic.lastIndexOf('/');
        return lastSlash > 0 ? topic.substring(0, lastSlash) : topic;
    }

    private java.util.Optional<java.util.UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(java.util.UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private MqttDeviceProfile defaultProfile(MqttDeviceProfile mqttDeviceProfile) {
        return mqttDeviceProfile == null ? MqttDeviceProfile.GENERIC_RELAY : mqttDeviceProfile;
    }

    private ProvisionedDeviceLookupResponse mapProvisionedLookup(DeviceEntity entity) {
        return ProvisionedDeviceLookupResponse.builder()
                .factoryDeviceId(entity.getId())
                .claimCode(entity.getClaimCode())
                .serialNumber(entity.getSerialNumber())
                .productModel(null)
                .platform(entity.getDevicePlatform())
                .mqttDeviceProfile(entity.getMqttDeviceProfile())
                .mqttCapabilities(mqttProfileService.getCapabilities(entity.getMqttDeviceProfile()))
                .firmwareVersion(null)
                .lastSeenAt(entity.getLastCommunication())
                .claimable(entity.getAccount() == null && entity.getFactoryDeviceStatus() == FactoryDeviceStatus.PASSED)
                .build();
    }

    private DeviceResponse mapDeviceResponse(DeviceEntity entity) {
        DeviceResponse response = DeviceResponse.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .deviceType(entity.getDeviceType())
                .devicePlatform(entity.getDevicePlatform())
                .enabled(entity.isEnabled())
                .deviceName(entity.getDeviceName())
                .timezone(entity.getTimezone())
                .lastCommunication(entity.getLastCommunication())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .accountId(entity.getAccount() != null ? entity.getAccount().getId() : null)
                .shared(false)
                .apiOnline(entity.isApiOnline())
                .mqttOnline(entity.isMqttOnline())
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .mqttDeviceProfile(entity.getMqttDeviceProfile())
                .mqttCapabilities(mqttProfileService.getCapabilities(entity.getMqttDeviceProfile()))
                .build();

        Map<Integer, Integer> channelSnapshot = Map.of();
        boolean hasActiveChannels = channelSnapshot.values().stream()
                .anyMatch(value -> value != null && value == 1);
        List<Boolean> relayChannelStates = new ArrayList<>();
        for (int channel = 0; channel < 4; channel++) {
            relayChannelStates.add(Objects.equals(channelSnapshot.get(channel), 1));
        }
        response.setHasActiveChannels(hasActiveChannels);
        response.setRelayChannelStates(relayChannelStates);
        return response;
    }
}

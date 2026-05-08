package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.DeviceType;
import com.nitramite.porssiohjain.entity.enums.FactoryDeviceStatus;
import com.nitramite.porssiohjain.entity.enums.FactoryTestStatus;
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

@Service
@RequiredArgsConstructor
public class FactoryProvisioningService {

    private final FactoryDeviceRepository factoryDeviceRepository;
    private final FactoryTestRunRepository factoryTestRunRepository;
    private final FactoryTestStepResultRepository factoryTestStepResultRepository;
    private final OtaReleaseRepository otaReleaseRepository;
    private final OtaDeploymentRepository otaDeploymentRepository;
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final ObjectProvider<MqttService> mqttServiceProvider;

    @Transactional(readOnly = true)
    public List<FactoryDeviceResponse> listFactoryDevices() {
        return factoryDeviceRepository.findAllByOrderByIdDesc().stream()
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
        factoryDeviceRepository.findBySerialNumber(serialNumber)
                .ifPresent(existing -> {
                    throw new DuplicateEntityException("Factory device already exists with serial number: " + serialNumber);
                });
        FactoryDeviceEntity entity = FactoryDeviceEntity.builder()
                .serialNumber(serialNumber)
                .hardwareMac(blankToNull(request.getHardwareMac()))
                .chipId(blankToNull(request.getChipId()))
                .platform(request.getPlatform())
                .productModel(requireText(request.getProductModel(), "productModel"))
                .firmwareVersion(blankToNull(request.getFirmwareVersion()))
                .mqttTopicRoot(defaultTopicRoot(serialNumber, request.getMqttTopicRoot()))
                .mqttUsername(blankToNull(request.getMqttUsername()))
                .mqttPassword(blankToNull(request.getMqttPassword()))
                .metadataJson(blankToNull(request.getMetadataJson()))
                .status(FactoryDeviceStatus.REGISTERED)
                .build();
        return mapFactoryDevice(factoryDeviceRepository.save(entity));
    }

    @Transactional
    public FactoryDeviceResponse updateFactoryDevice(Long id, UpdateFactoryDeviceRequest request) {
        FactoryDeviceEntity entity = getFactoryDeviceEntity(id);
        if (request.getHardwareMac() != null) {
            entity.setHardwareMac(blankToNull(request.getHardwareMac()));
        }
        if (request.getChipId() != null) {
            entity.setChipId(blankToNull(request.getChipId()));
        }
        if (request.getFirmwareVersion() != null) {
            entity.setFirmwareVersion(blankToNull(request.getFirmwareVersion()));
        }
        if (request.getMetadataJson() != null) {
            entity.setMetadataJson(blankToNull(request.getMetadataJson()));
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        return mapFactoryDevice(factoryDeviceRepository.save(entity));
    }

    @Transactional
    public FactoryTestRunResponse startTestRun(Long operatorAccountId, Long factoryDeviceId, CreateFactoryTestRunRequest request) {
        FactoryDeviceEntity factoryDevice = getFactoryDeviceEntity(factoryDeviceId);
        AccountEntity operator = accountRepository.findById(operatorAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + operatorAccountId));
        factoryDevice.setStatus(FactoryDeviceStatus.TESTING);
        factoryDeviceRepository.save(factoryDevice);

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
        FactoryDeviceEntity factoryDevice = getFactoryDeviceEntity(factoryDeviceId);
        if (factoryDevice.getClaimedDevice() != null) {
            throw new DuplicateEntityException("Factory device already claimed");
        }
        if (factoryDevice.getStatus() != FactoryDeviceStatus.PASSED) {
            throw new IllegalArgumentException("Factory device must pass testing before claim");
        }

        DeviceResponse created = deviceService.createProvisionedDevice(
                request.getAccountId(),
                requireText(request.getDeviceName(), "deviceName"),
                requireText(request.getTimezone(), "timezone"),
                DeviceType.STANDARD,
                factoryDevice.getMqttUsername(),
                factoryDevice.getMqttPassword()
        );
        DeviceEntity device = deviceRepository.findById(created.getId())
                .orElseThrow(() -> new EntityNotFoundException("Claimed device not found after creation"));
        factoryDevice.setClaimedDevice(device);
        factoryDevice.setStatus(FactoryDeviceStatus.CLAIMED);
        factoryDeviceRepository.save(factoryDevice);
        return created;
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
        FactoryDeviceEntity factoryDevice = getFactoryDeviceEntity(factoryDeviceId);
        OtaReleaseEntity release = otaReleaseRepository.findById(request.getOtaReleaseId())
                .orElseThrow(() -> new EntityNotFoundException("OTA release not found: " + request.getOtaReleaseId()));
        AccountEntity requestedBy = accountRepository.findById(requestedByAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + requestedByAccountId));

        String commandTopic = factoryDevice.getMqttTopicRoot() + "/command";
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
        factoryDeviceRepository.findByMqttTopicRoot(topicRoot).ifPresent(device -> {
            device.setLastSeenAt(Instant.now());
            device.setLastBootstrapPayload(payload);
            if (device.getStatus() == FactoryDeviceStatus.REGISTERED) {
                device.setStatus(FactoryDeviceStatus.REGISTERED);
            }
            factoryDeviceRepository.save(device);
        });
    }

    private void finalizeTestRun(FactoryTestRunEntity run, FactoryTestStatus finalStatus) {
        run.setStatus(finalStatus == FactoryTestStatus.FAILED ? FactoryTestStatus.FAILED : FactoryTestStatus.PASSED);
        run.setFinishedAt(Instant.now());
        FactoryDeviceEntity factoryDevice = run.getFactoryDevice();
        factoryDevice.setStatus(finalStatus == FactoryTestStatus.FAILED
                ? FactoryDeviceStatus.FAILED
                : FactoryDeviceStatus.PASSED);
        factoryDeviceRepository.save(factoryDevice);
    }

    private FactoryDeviceEntity getFactoryDeviceEntity(Long id) {
        return factoryDeviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Factory device not found: " + id));
    }

    private FactoryDeviceResponse mapFactoryDevice(FactoryDeviceEntity entity) {
        List<FactoryTestRunResponse> testRuns = factoryTestRunRepository.findByFactoryDeviceOrderByStartedAtDesc(entity).stream()
                .map(this::mapTestRun)
                .toList();
        List<OtaDeploymentResponse> otaDeployments = otaDeploymentRepository.findByFactoryDeviceOrderByRequestedAtDesc(entity).stream()
                .map(this::mapOtaDeployment)
                .toList();
        return FactoryDeviceResponse.builder()
                .id(entity.getId())
                .serialNumber(entity.getSerialNumber())
                .hardwareMac(entity.getHardwareMac())
                .chipId(entity.getChipId())
                .platform(entity.getPlatform())
                .productModel(entity.getProductModel())
                .firmwareVersion(entity.getFirmwareVersion())
                .mqttTopicRoot(entity.getMqttTopicRoot())
                .mqttUsername(entity.getMqttUsername())
                .mqttPassword(entity.getMqttPassword())
                .status(entity.getStatus())
                .lastSeenAt(entity.getLastSeenAt())
                .lastBootstrapPayload(entity.getLastBootstrapPayload())
                .metadataJson(entity.getMetadataJson())
                .claimedDeviceId(entity.getClaimedDevice() != null ? entity.getClaimedDevice().getId() : null)
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

    private String buildOtaCommandPayload(FactoryDeviceEntity factoryDevice, OtaReleaseEntity release, String commandTemplate) {
        if (commandTemplate != null && !commandTemplate.isBlank()) {
            return commandTemplate
                    .replace("{url}", release.getBinaryUrl())
                    .replace("{checksum}", release.getChecksumSha256() == null ? "" : release.getChecksumSha256())
                    .replace("{version}", release.getVersion())
                    .replace("{platform}", factoryDevice.getPlatform().name());
        }
        return """
                {"command":"ota_install","url":"%s","version":"%s","checksumSha256":"%s"}
                """.formatted(
                release.getBinaryUrl(),
                release.getVersion(),
                release.getChecksumSha256() == null ? "" : release.getChecksumSha256()
        );
    }

    private String defaultTopicRoot(String serialNumber, String requestedTopicRoot) {
        if (requestedTopicRoot != null && !requestedTopicRoot.isBlank()) {
            return requestedTopicRoot;
        }
        return "factory/bootstrap/" + serialNumber;
    }

    private String extractTopicRoot(String topic) {
        int lastSlash = topic.lastIndexOf('/');
        return lastSlash > 0 ? topic.substring(0, lastSlash) : topic;
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
}

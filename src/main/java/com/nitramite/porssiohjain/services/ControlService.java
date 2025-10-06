package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.ControlDeviceEntity;
import com.nitramite.porssiohjain.entity.ControlEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.ControlDeviceRepository;
import com.nitramite.porssiohjain.entity.repository.ControlRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.ControlDeviceResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ControlService {

    private final ControlRepository controlRepository;
    private final ControlDeviceRepository controlDeviceRepository;
    private final AccountRepository accountRepository;
    private final DeviceRepository deviceRepository;

    public ControlEntity createControl(
            Long accountId, String name, BigDecimal maxPriceSnt, Integer dailyOnMinutes
    ) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + accountId));

        ControlEntity control = ControlEntity.builder()
                .account(account)
                .name(name)
                .maxPriceSnt(maxPriceSnt)
                .dailyOnMinutes(dailyOnMinutes)
                .build();

        return controlRepository.save(control);
    }

    public ControlEntity updateControl(
            Long controlId, String name, BigDecimal maxPriceSnt, Integer dailyOnMinutes
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

        control.setName(name);
        control.setMaxPriceSnt(maxPriceSnt);
        control.setDailyOnMinutes(dailyOnMinutes);
        return controlRepository.save(control);
    }

    public void deleteControl(
            Long controlId
    ) {
        if (!controlRepository.existsById(controlId)) {
            throw new EntityNotFoundException("Control not found with id: " + controlId);
        }
        controlRepository.deleteById(controlId);
    }

    public List<ControlEntity> getAllControls() {
        return controlRepository.findAll();
    }


    public ControlDeviceResponse addDeviceToControl(
            Long controlId, Long deviceId, Integer deviceChannel
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found with id: " + deviceId));

        if (controlDeviceRepository.existsByControlIdAndDeviceIdAndDeviceChannel(controlId, deviceId, deviceChannel)) {
            throw new DuplicateEntityException(
                    String.format("Device %d with channel %d is already linked to control %d",
                            deviceId, deviceChannel, controlId)
            );
        }

        ControlDeviceEntity controlDevice = ControlDeviceEntity.builder()
                .control(control)
                .device(device)
                .deviceChannel(deviceChannel)
                .build();

        ControlDeviceEntity saved = controlDeviceRepository.save(controlDevice);

        return ControlDeviceResponse.builder()
                .id(saved.getId())
                .controlId(saved.getControl().getId())
                .deviceId(saved.getDevice().getId())
                .deviceChannel(saved.getDeviceChannel())
                .build();
    }

    public ControlDeviceResponse updateControlDevice(
            Long controlDeviceId, Long deviceId, Integer deviceChannel
    ) {
        ControlDeviceEntity controlDevice = controlDeviceRepository.findById(controlDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId));

        if (deviceId != null) {
            DeviceEntity device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new EntityNotFoundException("Device not found with id: " + deviceId));
            controlDevice.setDevice(device);
        }

        if (deviceChannel != null) {
            controlDevice.setDeviceChannel(deviceChannel);
        }

        ControlDeviceEntity updated = controlDeviceRepository.save(controlDevice);

        return ControlDeviceResponse.builder()
                .id(updated.getId())
                .controlId(updated.getControl().getId())
                .deviceId(updated.getDevice().getId())
                .deviceChannel(updated.getDeviceChannel())
                .build();
    }

    public void deleteControlDevice(
            Long controlDeviceId
    ) {
        if (!controlDeviceRepository.existsById(controlDeviceId)) {
            throw new EntityNotFoundException("ControlDevice not found with id: " + controlDeviceId);
        }
        controlDeviceRepository.deleteById(controlDeviceId);
    }

    public List<ControlDeviceEntity> getDevicesByControl(
            Long controlId
    ) {
        ControlEntity control = controlRepository.findById(controlId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found with id: " + controlId));

        return control.getControlDevices().stream().toList();
    }

}

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.*;
import com.nitramite.porssiohjain.entity.enums.*;
import com.nitramite.porssiohjain.entity.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceMeasurementServiceTest {
    private final DeviceMeasurementRepository generic = mock();
    private final ZigbeeDeviceMeasurementRepository zigbee = mock();
    private final DeviceMeasurementService service = new DeviceMeasurementService(generic, zigbee);

    @Test void existingZigbeeHistoryIsReadWithoutCopyingItToGenericStorage() {
        var device = DeviceEntity.builder().id(12L).devicePlatform(DevicePlatform.ANDROID_ZIGBEE).build();
        Instant at = Instant.parse("2026-10-15T10:00:00Z");
        var row = ZigbeeDeviceMeasurementEntity.builder().id(1L).device(device)
                .measurementType(ZigbeeMeasurementType.TEMPERATURE).measurementKey("temperature")
                .value(new BigDecimal("20.9")).measuredAt(at).receivedAt(at.plusSeconds(1)).build();
        when(zigbee.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByMeasuredAtDescIdDesc(
                12L, ZigbeeMeasurementType.TEMPERATURE, "temperature")).thenReturn(Optional.of(row));
        var page = PageRequest.of(0, 1000);
        when(zigbee.findByDeviceIdAndMeasurementTypeAndMeasurementKeyAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                12L, ZigbeeMeasurementType.TEMPERATURE, "temperature", at.minusSeconds(1), at.plusSeconds(1), page))
                .thenReturn(new SliceImpl<>(List.of(row)));
        var latest = service.latest(device, DeviceMeasurementType.TEMPERATURE, "temperature").orElseThrow();
        assertEquals(at, latest.getObservedAt());
        assertEquals(at, latest.getMeasuredAt());
        assertEquals("°C", latest.getUnit());
        assertEquals(new BigDecimal("20.9"), latest.getValue());
        assertEquals(1, service.history(device, DeviceMeasurementType.TEMPERATURE, "temperature",
                at.minusSeconds(1), at.plusSeconds(1), page).getNumberOfElements());
        verifyNoInteractions(generic);
        verify(zigbee, never()).save(any());
    }

    @Test void tuyaReadUsesGenericStorageAndLeavesZigbeeAlone() {
        var device = DeviceEntity.builder().id(13L).devicePlatform(DevicePlatform.TUYA_SMART).build();
        var row = DeviceMeasurementEntity.builder().device(device).value(BigDecimal.TEN).build();
        when(generic.findFirstByDeviceIdAndMeasurementTypeAndMeasurementKeyOrderByObservedAtDescIdDesc(
                13L, DeviceMeasurementType.TEMPERATURE, "temperature")).thenReturn(Optional.of(row));
        assertSame(row, service.latest(device, DeviceMeasurementType.TEMPERATURE, "temperature").orElseThrow());
        verifyNoInteractions(zigbee);
    }
}

package com.nitramite.porssiohjain.services.tuya;

import com.nitramite.porssiohjain.entity.repository.*;
import com.nitramite.porssiohjain.scheduled.TuyaScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TuyaSchedulerTest {
    private final TuyaDeviceRepository devices = mock();
    private final TuyaService tuya = mock();
    private final DeviceMeasurementRepository measurements = mock();
    private final TuyaScheduler scheduler = new TuyaScheduler(devices, tuya, measurements);

    @Test void failedAccountDoesNotStopOtherSensorsFromPolling() {
        var first = target(7L, 21L);
        var second = target(8L, 22L);
        when(devices.findPollTargets()).thenReturn(List.of(first, second));
        when(tuya.poll(7L, 21L)).thenThrow(new TuyaException("tuya.error.notConnected"));
        scheduler.pollSensors();
        verify(tuya).poll(7L, 21L);
        verify(tuya).poll(8L, 22L);
    }

    @Test void cleansOneMonthOfHistoryUsingSameCalendarRetentionAsZigbee() {
        Instant before = ZonedDateTime.now(ZoneId.of("Europe/Helsinki")).minusMonths(1).toInstant();
        scheduler.deleteOldMeasurements();
        Instant after = ZonedDateTime.now(ZoneId.of("Europe/Helsinki")).minusMonths(1).toInstant();
        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(measurements).deleteOlderThan(cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
    }

    @Test void cloudWorkRunsOffSchedulerThreadAndOverlappingPassIsSkipped() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var selected = target(7L, 21L);
        when(devices.findPollTargets()).thenReturn(List.of(selected));
        when(tuya.poll(7L, 21L)).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            finished.countDown();
            return null;
        });
        try {
            scheduler.schedulePolling();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            scheduler.schedulePolling();
            verify(devices, times(1)).findPollTargets();
        } finally {
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            scheduler.stop();
        }
    }

    private TuyaDeviceRepository.PollTarget target(Long accountId, Long deviceId) {
        var target = mock(TuyaDeviceRepository.PollTarget.class);
        when(target.getAccountId()).thenReturn(accountId);
        when(target.getDeviceId()).thenReturn(deviceId);
        return target;
    }
}

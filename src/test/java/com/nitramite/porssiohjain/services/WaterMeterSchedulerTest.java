package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.scheduled.WaterMeterScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class WaterMeterSchedulerTest {
    private static final String PORTAL = "https://wmd.wrm-systems.fi/water-company";
    @Test
    void oneRemovedOrFailedMeterDoesNotPreventOtherAccountsFromBeingPolled() {
        WaterMeterService service = mock();
        when(service.enabledForPolling()).thenReturn(List.of(new WaterMeterService.MeterReference(1L, 10L),
                new WaterMeterService.MeterReference(2L, 20L)));
        when(service.pollDaily(1L, 10L)).thenThrow(new IllegalStateException("not logged"));
        when(service.pollDaily(2L, 20L)).thenReturn(new WaterMeterService.Meter(20L, "Home",
                PORTAL, true, null, null, null, "waterMeters.error.login",
                com.nitramite.porssiohjain.entity.enums.WaterMeterProvider.WRM_SYSTEMS_WMD, WaterMeterService.NotificationSettings.defaults()));
        new WaterMeterScheduler(service).pollDaily();
        verify(service).pollDaily(1L, 10L);
        verify(service).pollDaily(2L, 20L);
    }
}

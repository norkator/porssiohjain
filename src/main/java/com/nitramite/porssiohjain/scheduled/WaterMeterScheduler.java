/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.scheduled;

import com.nitramite.porssiohjain.services.WaterMeterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class WaterMeterScheduler {
    private final WaterMeterService meters;

    @Scheduled(cron = "${water-meters.poll-cron:0 0 12 * * *}", zone = "Europe/Helsinki")
    public void pollDaily() {
        for (var meter : meters.enabledForPolling()) {
            try {
                var result = meters.pollDaily(meter.accountId(), meter.id());
                if (result.lastErrorKey() != null) log.warn("Water meter poll failed: meterId={}, reason={}",
                        meter.id(), result.lastErrorKey());
            } catch (RuntimeException exception) {
                // An individual failure/deletion must not stop other accounts; no provider payloads in logs.
                log.warn("Water meter poll could not complete: meterId={}", meter.id());
            }
        }
    }
}

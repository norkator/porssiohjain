/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.watermeters;

import com.nitramite.porssiohjain.entity.enums.WaterMeterProvider;

import java.math.BigDecimal;
import java.time.Instant;

/** Provider adapters own portal validation, authentication and conversion to cumulative cubic metres. */
public interface WaterMeterClient {
    WaterMeterProvider provider();
    String normalizePortalUrl(String portalUrl);
    Reading readLatest(String portalUrl, String username, String password);

    /** observedAt is a fetch time, never an invented meter measurement time. */
    record Reading(BigDecimal totalCubicMeters, Instant observedAt) { }
}

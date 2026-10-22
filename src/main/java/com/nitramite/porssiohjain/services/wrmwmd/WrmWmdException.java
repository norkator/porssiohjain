/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026 Martin Kankaanranta / Nitramite Tmi
 * See LICENSE for details.
 */
package com.nitramite.porssiohjain.services.wrmwmd;

import com.nitramite.porssiohjain.services.watermeters.WaterMeterException;

/** Safe translation keys only: provider responses and credentials must not reach logs or the UI. */
public class WrmWmdException extends WaterMeterException {
    public WrmWmdException(String messageKey) {
        super(messageKey);
    }
}

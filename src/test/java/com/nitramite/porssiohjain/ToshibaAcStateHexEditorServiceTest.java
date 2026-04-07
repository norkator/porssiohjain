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

package com.nitramite.porssiohjain;

import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexEditorService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToshibaAcStateHexEditorServiceTest {

    private final ToshibaAcStateHexEditorService service = new ToshibaAcStateHexEditorService();

    @Test
    void appliesPowerModeAndTemperatureToExistingState() {
        String updated = service.applyEditableSettings(
                "30431641313200101605fe0b00001002010000",
                false,
                ToshibaAcStateHexEditorService.EditableMode.COOL,
                24
        );

        assertEquals("31421841313200101605FE0B00001002010000", updated);
    }

    @Test
    void rejectsOutOfRangeTemperature() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.applyEditableSettings(
                        "30431641313200101605fe0b00001002010000",
                        true,
                        ToshibaAcStateHexEditorService.EditableMode.HEAT,
                        31
                )
        );
    }
}

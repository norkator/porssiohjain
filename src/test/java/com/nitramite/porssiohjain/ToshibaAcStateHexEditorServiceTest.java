/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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

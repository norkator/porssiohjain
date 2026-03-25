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

import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateDecodedResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToshibaAcStateHexDecoderServiceTest {

    private final ToshibaAcStateHexDecoderService service = new ToshibaAcStateHexDecoderService();

    @Test
    void decodesProvidedSampleState() {
        ToshibaAcStateDecodedResponse response = service.decode("30431641313200101605fe0b00001002010000");

        assertTrue(response.isValid());
        assertEquals(19, response.getByteLength());
        assertEquals("On", response.getPower().getLabel());
        assertEquals("HEAT", response.getMode().getCode());
        assertEquals(22, response.getTargetTemperature().getTemperatureCelsius());
        assertEquals("Auto", response.getFanMode().getLabel());
        assertEquals("Off", response.getSwingMode().getLabel());
        assertEquals("50% power", response.getPowerSelection().getLabel());
        assertEquals("Off", response.getMeritA().getLabel());
        assertEquals("Off", response.getMeritB().getLabel());
        assertEquals("Off", response.getAirPureIon().getLabel());
        assertEquals(22, response.getIndoorTemperature().getTemperatureCelsius());
        assertEquals(5, response.getOutdoorTemperature().getTemperatureCelsius());
        assertEquals("Off", response.getSelfCleaning().getLabel());
        assertEquals(8, response.getUnknownFields().size());
    }

    @Test
    void warnsWhenStateContainsExtraBytes() {
        ToshibaAcStateDecodedResponse response = service.decode("30431641313200101605fe0b00001002010000FFFF");

        assertTrue(response.isValid());
        assertFalse(response.getWarnings().isEmpty());
    }

}

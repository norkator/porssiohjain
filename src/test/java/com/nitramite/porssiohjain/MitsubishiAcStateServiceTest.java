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

import com.nitramite.porssiohjain.services.mitsubishi.MitsubishiAcStateService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitsubishiAcStateServiceTest {

    @Test
    void parsesLocalDateTimeLastCommunication() {
        Instant parsed = MitsubishiAcStateService.parseLastCommunication("2026-01-18T09:08:38.266");

        Instant expected = LocalDateTime.parse("2026-01-18T09:08:38.266")
                .atZone(ZoneId.systemDefault())
                .toInstant();
        assertEquals(expected, parsed);
    }

    @Test
    void parsesInstantFormattedLastCommunication() {
        Instant parsed = MitsubishiAcStateService.parseLastCommunication("2026-01-18T09:08:38.266Z");

        assertEquals(Instant.parse("2026-01-18T09:08:38.266Z"), parsed);
    }

    @Test
    void recentLastCommunicationMarksDeviceOnline() {
        assertTrue(MitsubishiAcStateService.isApiOnlineFromLastCommunication(Instant.now().minusSeconds(60)));
    }

    @Test
    void staleLastCommunicationMarksDeviceOffline() {
        assertFalse(MitsubishiAcStateService.isApiOnlineFromLastCommunication(Instant.now().minusSeconds(4L * 60L * 60L + 1L)));
    }

    @Test
    void missingLastCommunicationMarksDeviceOffline() {
        assertFalse(MitsubishiAcStateService.isApiOnlineFromLastCommunication(null));
    }
}

/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 */

package com.nitramite.porssiohjain.contollers;

import com.nitramite.porssiohjain.services.ServiceNoticeService;
import com.nitramite.porssiohjain.services.models.ServiceNoticeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/service-notice")
@RequiredArgsConstructor
public class ServiceNoticeController {

    private final ServiceNoticeService serviceNoticeService;

    @GetMapping
    public ServiceNoticeResponse getNotice(@RequestParam(defaultValue = "en") String locale) {
        return serviceNoticeService.getNotice(Locale.forLanguageTag(locale));
    }
}

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

package com.nitramite.porssiohjain.services;

import com.vaadin.flow.component.UI;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class I18nService {

    private final MessageSource messageSource;

    public I18nService(
            MessageSource messageSource
    ) {
        this.messageSource = messageSource;
    }

    public String t(String key, Object... args) {
        Locale locale = UI.getCurrent() != null
                ? UI.getCurrent().getLocale()
                : Locale.getDefault();
        return messageSource.getMessage(key, args, locale);
    }

}
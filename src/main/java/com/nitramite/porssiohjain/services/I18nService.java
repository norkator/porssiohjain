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
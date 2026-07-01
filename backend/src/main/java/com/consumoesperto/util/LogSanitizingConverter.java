package com.consumoesperto.util;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Converter Logback — aplica {@link LogSanitizer} em toda mensagem de log. */
public class LogSanitizingConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        if (event == null || event.getFormattedMessage() == null) {
            return "";
        }
        return LogSanitizer.sanitize(event.getFormattedMessage());
    }
}

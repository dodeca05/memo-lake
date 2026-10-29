package com.memolake.interfaces.web;

import com.memolake.application.NameRules;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * View helpers for templates ({@code ${@webFormat.time(...)}}). Times are rendered in UTC;
 * {@code app.js} converts them to the browser's local time.
 */
@Component
public class WebFormat {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public String time(Instant instant) {
        return instant == null ? "-" : TIME.format(instant);
    }

    public String iso(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    public String nameRule() {
        return NameRules.RULE_DESCRIPTION_KO;
    }
}

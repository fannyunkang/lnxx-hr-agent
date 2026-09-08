package com.lnxx.hr.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SensitiveDataRedactor {
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}([0-9Xx]{4})(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    public String redact(String value) {
        if (value == null) return null;
        String result = ID_CARD.matcher(value).replaceAll("$1********$2");
        result = PHONE.matcher(result).replaceAll("$1****$2");
        return EMAIL.matcher(result).replaceAll("$1***$2");
    }
}

package com.tianji.aigc.handoff;

import java.util.regex.Pattern;

/**
 * Redacts private identifiers before a handoff summary is stored or displayed.
 * Kept intentionally conservative: anything that looks like a credential or a
 * personal identifier is masked instead of being passed downstream.
 */
public final class HandoffRedaction {

    private HandoffRedaction() {
    }

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(1[3-9]\\d{9})(?![0-9])");
    private static final Pattern ID_CARD = Pattern.compile(
            "(?<![0-9])([1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?![0-9])");
    private static final Pattern CARD_NUMBER = Pattern.compile("(?<![0-9])(\\d{16,19})(?![0-9])");
    private static final Pattern AUTHORIZATION = Pattern.compile("(?i)(authorization\\s*[:=])\\s*\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._-]+");
    private static final Pattern SECRET_KEY = Pattern.compile("(?i)(sk-[a-z0-9_-]{10,}|api[_-]?key\\s*[:=]\\s*\\S+|token\\s*[:=]\\s*\\S+)");
    private static final Pattern PASSWORD_VALUE = Pattern.compile("(?i)((?:password|passwd|pwd)\\s*[:=])\\s*\\S+");

    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = text;
        masked = EMAIL.matcher(masked).replaceAll("[email redacted]");
        masked = PHONE.matcher(masked).replaceAll("[phone redacted]");
        masked = ID_CARD.matcher(masked).replaceAll("[id redacted]");
        masked = CARD_NUMBER.matcher(masked).replaceAll("[card redacted]");
        masked = AUTHORIZATION.matcher(masked).replaceAll("$1[redacted]");
        masked = BEARER.matcher(masked).replaceAll("Bearer [redacted]");
        masked = SECRET_KEY.matcher(masked).replaceAll("[secret redacted]");
        masked = PASSWORD_VALUE.matcher(masked).replaceAll("$1[redacted]");
        return masked;
    }
}

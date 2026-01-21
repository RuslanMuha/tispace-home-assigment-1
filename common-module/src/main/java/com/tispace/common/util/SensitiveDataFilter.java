package com.tispace.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Utility class for filtering sensitive data from logs and error messages.
 */
public final class SensitiveDataFilter {

    private SensitiveDataFilter() {
        // utility
    }

    private static final String MASKED_VALUE = "***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "pwd", "passwd",
            "secret",
            "token",
            "apikey", "apiKey", "api-key", "api_key",
            "authorization", "auth",
            "credential",
            "privatekey", "privateKey", "private-key", "private_key",
            "accesstoken", "accessToken", "access-token", "access_token",
            "refreshtoken", "refreshToken", "refresh-token", "refresh_token"
    );

    private static final String KEYS = SENSITIVE_KEYS.stream()
            .map(Pattern::quote)
            .reduce((a, b) -> a + "|" + b)
            .orElseThrow();

    /**
     * Groups:
     *  1 - key (may be quoted)
     *  2 - separator (":" or "=")
     *  3 - optional opening quote
     *  4 - value (may contain spaces)
     *  5 - optional closing quote
     *
     * Value stops at:
     *  - comma / semicolon / } / " / end
     *  - OR before " <nextSensitiveKey>[:=]"
     */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)" +
                    "(\"?(?:" + KEYS + ")\"?)" +          // (1) key
                    "\\s*([:=])\\s*" +                    // (2) separator
                    "(\"?)" +                             // (3) optional quote
                    "(.*?)" +                             // (4) value (lazy, may contain spaces)
                    "(\"?)" +                             // (5) optional quote
                    "(?=" +                               // stop condition (lookahead)
                    "\\s+(?:\"?(?:" + KEYS + ")\"?)\\s*[:=]" + // next sensitive key
                    "|[,;}\"]" +                           // delimiters
                    "|$" +                                 // end
                    ")"
    );

    public static String maskSensitiveData(String message) {
        if (StringUtils.isBlank(message)) {
            return message;
        }

        var matcher = SENSITIVE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return message; // fast-path
        }

        return matcher.replaceAll(SensitiveDataFilter::replacement);
    }

    public static boolean containsSensitiveData(String message) {
        if (StringUtils.isBlank(message)) {
            return false;
        }
        return SENSITIVE_PATTERN.matcher(message).find();
    }

    private static String replacement(MatchResult m) {
        String key = m.group(1);
        String sep = m.group(2);

        boolean keyQuoted = key.startsWith("\"") && key.endsWith("\"");
        return key + sep + (keyQuoted ? "\"" + MASKED_VALUE + "\"" : MASKED_VALUE);
    }
}



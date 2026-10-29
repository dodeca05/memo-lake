package com.memolake.interfaces.rest;

import com.memolake.common.exception.MemoLakeException;

/**
 * The memo version is exposed as a strong ETag ({@code "3"}) and accepted back in {@code If-Match}.
 */
final class VersionETags {

    private VersionETags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * @return the version required by {@code If-Match}, or null when the header is absent or {@code *}
     */
    static Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.equals("*")) {
            return null;
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException e) {
            throw MemoLakeException.invalidRequest(
                    "If-Match must be a single memo version ETag, for example \"3\" (as returned in the ETag header)");
        }
    }
}

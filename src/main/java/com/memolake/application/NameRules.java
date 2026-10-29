package com.memolake.application;

import com.memolake.common.exception.MemoLakeException;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Naming rules for topics and memos.
 *
 * <ul>
 *   <li>1 to {@value #MAX_LENGTH} characters</li>
 *   <li>Allowed: Hangul, ASCII letters, digits, space, hyphen ({@code -}), underscore ({@code _})</li>
 *   <li>No leading or trailing space</li>
 * </ul>
 * Everything else, including path separators ({@code /}, {@code \}), dots and control characters, is rejected.
 * Names are normalized to Unicode NFC so that composed and decomposed Hangul refer to the same name.
 */
public final class NameRules {

    public static final int MAX_LENGTH = 100;

    public static final String RULE_DESCRIPTION = "1-" + MAX_LENGTH
            + " characters of Hangul, English letters, digits, space, hyphen (-) or underscore (_),"
            + " not starting or ending with a space";

    /** Same rule in Korean, for the web UI. */
    public static final String RULE_DESCRIPTION_KO = "이름은 1~" + MAX_LENGTH
            + "자의 한글, 영문, 숫자, 공백, 하이픈(-), 밑줄(_)만 쓸 수 있고 공백으로 시작하거나 끝날 수 없습니다.";

    private static final Pattern ALLOWED = Pattern.compile("[\\p{IsHangul}A-Za-z0-9 _-]+");

    private NameRules() {
    }

    public static String normalize(String name) {
        return name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFC);
    }

    public static boolean isValid(String name) {
        String normalized = normalize(name);
        return normalized != null
                && !normalized.isEmpty()
                && normalized.length() <= MAX_LENGTH
                && normalized.charAt(0) != ' '
                && normalized.charAt(normalized.length() - 1) != ' '
                && ALLOWED.matcher(normalized).matches();
    }

    public static String requireTopicName(String name) {
        return require("topic name", name);
    }

    public static String requireMemoName(String name) {
        return require("memo name", name);
    }

    private static String require(String label, String name) {
        if (!isValid(name)) {
            throw MemoLakeException.invalidRequest("Invalid %s. Use %s.".formatted(label, RULE_DESCRIPTION));
        }
        return normalize(name);
    }
}

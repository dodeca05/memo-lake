package com.memolake.interfaces.web;

import java.util.Locale;
import java.util.Optional;

/**
 * How the memo page shows a memo's content. Chosen with {@code ?view=raw|markdown|html};
 * without the parameter {@link #detect(String)} picks one from the content.
 */
public enum MemoView {

    /** The stored text as-is in a {@code <pre>}. */
    RAW("raw", "원문"),
    /** Rendered as (GitHub flavoured) Markdown; the HTML is sanitized by {@link MarkdownRenderer}. */
    MARKDOWN("markdown", "Markdown"),
    /** Shown as an HTML document in a sandboxed frame (no scripts, no cookies). */
    HTML("html", "HTML");

    private final String key;
    private final String label;

    MemoView(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Value used in the {@code view} query parameter. */
    public String getKey() {
        return key;
    }

    /** Button label. */
    public String getLabel() {
        return label;
    }

    public static Optional<MemoView> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.strip().toLowerCase(Locale.ROOT);
        for (MemoView view : values()) {
            if (view.key.equals(wanted)) {
                return Optional.of(view);
            }
        }
        return Optional.empty();
    }

    /**
     * Default view for a memo: JSON-looking content stays raw (Markdown would mangle it), full HTML documents open
     * as HTML, everything else is treated as Markdown (plain text renders fine as Markdown too).
     */
    public static MemoView detect(String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            return RAW;
        }
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '{' && last == '}') || (first == '[' && last == ']')) {
            return RAW;
        }
        String head = text.substring(0, Math.min(text.length(), 15)).toLowerCase(Locale.ROOT);
        if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
            return HTML;
        }
        return MARKDOWN;
    }
}

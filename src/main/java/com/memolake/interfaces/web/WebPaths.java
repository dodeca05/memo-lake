package com.memolake.interfaces.web;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds web UI paths with topic / memo names percent-encoded as path segments.
 */
final class WebPaths {

    private WebPaths() {
    }

    static String topic(String topicName) {
        return expand("/web/topic/{topicName}", topicName);
    }

    static String memo(String topicName, String memoName) {
        return expand("/web/topic/{topicName}/{memoName}", topicName, memoName);
    }

    static String editor(String topicName, String memoName) {
        return expand("/web/topic/{topicName}/{memoName}/edit", topicName, memoName);
    }

    private static String expand(String template, Object... values) {
        return UriComponentsBuilder.fromPath(template).encode().buildAndExpand(values).toUriString();
    }
}

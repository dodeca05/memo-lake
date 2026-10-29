package com.memolake.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "memolake")
public record MemoLakeProperties(@DefaultValue Memo memo, @DefaultValue Security security) {

    /**
     * @param maxContentSize maximum memo body size, measured in UTF-8 bytes
     */
    public record Memo(@DefaultValue("1MB") DataSize maxContentSize) {
    }

    /**
     * @param allowedHosts host names accepted in the {@code Host} header; {@code *} accepts any host
     * @param accessToken  optional shared token; when set, every request must present it
     */
    public record Security(@DefaultValue({"localhost", "127.0.0.1", "::1"}) List<String> allowedHosts,
                           @DefaultValue("") String accessToken) {

        public boolean anyHostAllowed() {
            return allowedHosts.contains("*");
        }

        public boolean accessTokenEnabled() {
            return accessToken != null && !accessToken.isBlank();
        }
    }
}

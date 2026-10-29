package com.memolake.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemoLakeProperties.class)
public class AppConfig {

    /** Source of all createdAt / updatedAt values (UTC). Replaceable in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

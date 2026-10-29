package com.memolake.application.dto;

import com.memolake.domain.topic.Topic;
import java.time.Instant;

public record TopicInfo(String name, String description, Instant createdAt, Instant updatedAt) {

    public static TopicInfo from(Topic topic) {
        return new TopicInfo(topic.getName(), topic.getDescription(), topic.getCreatedAt(), topic.getUpdatedAt());
    }
}

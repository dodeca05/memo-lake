package com.memolake.interfaces.rest;

import com.memolake.application.TopicService;
import com.memolake.application.ValidName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/topic}. Only {@code name} and {@code description} are used.
 */
public record CreateTopicRequest(
        @NotBlank(message = "is required") @ValidName String name,
        @Size(max = TopicService.DESCRIPTION_MAX_LENGTH) String description) {
}

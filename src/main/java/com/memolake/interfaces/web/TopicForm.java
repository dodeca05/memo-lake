package com.memolake.interfaces.web;

import com.memolake.application.NameRules;
import com.memolake.application.TopicService;
import com.memolake.application.ValidName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "New topic" form on the topic list page. */
public class TopicForm {

    @NotBlank(message = "토픽 이름을 입력하세요.")
    @ValidName(message = NameRules.RULE_DESCRIPTION_KO)
    private String name;

    @Size(max = TopicService.DESCRIPTION_MAX_LENGTH, message = "설명은 1000자 이하로 입력하세요.")
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

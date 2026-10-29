package com.memolake.interfaces.rest;

import com.memolake.application.TopicService;
import com.memolake.application.dto.PageResult;
import com.memolake.application.dto.TopicDetail;
import com.memolake.application.dto.TopicInfo;
import com.memolake.domain.topic.TopicSummary;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/topic")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public PageResult<TopicSummary> listTopics(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return topicService.listTopics(page, size);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TopicInfo> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        TopicInfo topic = topicService.createTopic(request.name(), request.description());
        var location = UriComponentsBuilder.fromPath("/api/topic/{topicName}").encode()
                .buildAndExpand(topic.name()).toUri();
        return ResponseEntity.created(location).body(topic);
    }

    @GetMapping("/{topicName}")
    public TopicDetail getTopic(@PathVariable String topicName,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size) {
        return topicService.getTopic(topicName, page, size);
    }
}

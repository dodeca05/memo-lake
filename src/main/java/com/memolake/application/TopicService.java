package com.memolake.application;

import com.memolake.application.dto.PageResult;
import com.memolake.application.dto.TopicDetail;
import com.memolake.application.dto.TopicInfo;
import com.memolake.common.exception.MemoLakeException;
import com.memolake.domain.memo.MemoRepository;
import com.memolake.domain.topic.Topic;
import com.memolake.domain.topic.TopicRepository;
import com.memolake.domain.topic.TopicSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Topic use cases. Called by the REST controllers, the MCP tools and the web controller alike.
 */
@Service
@Transactional(readOnly = true)
public class TopicService {

    public static final int DESCRIPTION_MAX_LENGTH = 1000;

    private static final Logger log = LoggerFactory.getLogger(TopicService.class);

    private final TopicRepository topicRepository;
    private final MemoRepository memoRepository;
    private final Clock clock;

    public TopicService(TopicRepository topicRepository, MemoRepository memoRepository, Clock clock) {
        this.topicRepository = topicRepository;
        this.memoRepository = memoRepository;
        this.clock = clock;
    }

    public PageResult<TopicSummary> listTopics(int page, int size) {
        return PageResult.from(topicRepository.findSummaries(Paging.of(page, size)));
    }

    public TopicInfo getTopicInfo(String topicName) {
        return TopicInfo.from(topicRepository.getByName(NameRules.requireTopicName(topicName)));
    }

    public TopicDetail getTopic(String topicName, int page, int size) {
        PageRequest pageRequest = Paging.of(page, size);
        Topic topic = topicRepository.getByName(NameRules.requireTopicName(topicName));
        return new TopicDetail(topic.getName(), topic.getDescription(),
                PageResult.from(memoRepository.findSummaries(topic, pageRequest)));
    }

    @Transactional
    public TopicInfo createTopic(String topicName, String description) {
        String name = NameRules.requireTopicName(topicName);
        String desc = description == null ? "" : description;
        if (desc.length() > DESCRIPTION_MAX_LENGTH) {
            throw MemoLakeException.invalidRequest(
                    "description must be at most %d characters".formatted(DESCRIPTION_MAX_LENGTH));
        }
        if (topicRepository.existsByName(name)) {
            throw MemoLakeException.topicAlreadyExists(name);
        }
        Topic topic;
        try {
            topic = topicRepository.saveAndFlush(new Topic(name, desc, now()));
        }
        catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent create of the same name (unique constraint).
            throw MemoLakeException.topicAlreadyExists(name);
        }
        log.atInfo().addKeyValue("topic", name).log("Topic created: topic='{}'", name);
        return TopicInfo.from(topic);
    }

    private Instant now() {
        // Database timestamps keep microseconds; truncate so the in-memory value equals the stored one.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

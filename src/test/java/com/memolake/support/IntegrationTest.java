package com.memolake.support;

import com.memolake.domain.memo.MemoRepository;
import com.memolake.domain.topic.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that run against the full application and the (in-memory H2) database.
 * The database is emptied and the clock reset before every test.
 */
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected TopicRepository topicRepository;

    @Autowired
    protected MemoRepository memoRepository;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        memoRepository.deleteAllInBatch();
        topicRepository.deleteAllInBatch();
        clock.set(MutableClock.START);
    }
}

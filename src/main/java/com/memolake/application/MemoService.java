package com.memolake.application;

import com.memolake.application.dto.MemoDetail;
import com.memolake.application.dto.MemoWriteResult;
import com.memolake.common.exception.MemoLakeException;
import com.memolake.config.MemoLakeProperties;
import com.memolake.domain.memo.Memo;
import com.memolake.domain.memo.MemoRepository;
import com.memolake.domain.topic.Topic;
import com.memolake.domain.topic.TopicRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memo use cases. Called by the REST controllers, the MCP tools and the web controller alike.
 *
 * <p>Concurrency: every content change (replace and append) first takes a row lock on the memo
 * ({@code SELECT ... FOR UPDATE}) inside the transaction, so concurrent writers to the same memo run
 * one after another and no append is lost. Replace and append can additionally be made conditional on
 * the version the caller last saw ({@code expectedVersion}); the check runs under the same lock, so the
 * compare-and-write is atomic. Without a condition, the last replace wins.
 */
@Service
@Transactional(readOnly = true)
public class MemoService {

    private static final Logger log = LoggerFactory.getLogger(MemoService.class);

    private final TopicRepository topicRepository;
    private final MemoRepository memoRepository;
    private final Clock clock;
    private final long maxContentBytes;

    public MemoService(TopicRepository topicRepository, MemoRepository memoRepository, Clock clock,
                       MemoLakeProperties properties) {
        this.topicRepository = topicRepository;
        this.memoRepository = memoRepository;
        this.clock = clock;
        this.maxContentBytes = properties.memo().maxContentSize().toBytes();
    }

    public long maxContentBytes() {
        return maxContentBytes;
    }

    public MemoDetail getMemo(String topicName, String memoName) {
        Topic topic = topicRepository.getByName(NameRules.requireTopicName(topicName));
        String name = NameRules.requireMemoName(memoName);
        return memoRepository.findByTopicAndName(topic, name)
                .map(MemoDetail::from)
                .orElseThrow(() -> MemoLakeException.memoNotFound(topic.getName(), name));
    }

    /** Like {@link #getMemo} but returns empty when the memo does not exist (the topic must exist). */
    public Optional<MemoDetail> findMemo(String topicName, String memoName) {
        Topic topic = topicRepository.getByName(NameRules.requireTopicName(topicName));
        return memoRepository.findByTopicAndName(topic, NameRules.requireMemoName(memoName)).map(MemoDetail::from);
    }

    @Transactional
    public MemoWriteResult createMemo(String topicName, String memoName, String content) {
        checkSize(utf8Length(requireContent(content)));
        Topic topic = topicRepository.getByName(NameRules.requireTopicName(topicName));
        String name = NameRules.requireMemoName(memoName);
        if (memoRepository.existsByTopicAndName(topic, name)) {
            throw MemoLakeException.memoAlreadyExists(topic.getName(), name);
        }
        Memo memo;
        try {
            memo = memoRepository.saveAndFlush(new Memo(topic, name, content, now()));
        }
        catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent create of the same name (unique constraint).
            throw MemoLakeException.memoAlreadyExists(topic.getName(), name);
        }
        logWrite("Memo created", topic, memo, content);
        return MemoWriteResult.from(memo);
    }

    /**
     * Replaces the whole content.
     *
     * @param expectedVersion when not null, the replace only happens if the memo is still at this version
     */
    @Transactional
    public MemoWriteResult replaceMemo(String topicName, String memoName, String content, Long expectedVersion) {
        checkSize(utf8Length(requireContent(content)));
        Topic topic = topicRepository.getByName(NameRules.requireTopicName(topicName));
        Memo memo = lockMemo(topic, memoName);
        checkVersion(memo, expectedVersion);
        memo.replaceContent(content, now());
        memoRepository.flush(); // increments the version
        logWrite("Memo replaced", topic, memo, content);
        return MemoWriteResult.from(memo);
    }

    /**
     * Appends {@code content} to the end of the memo exactly as given (no separator is inserted).
     *
     * @param expectedVersion when not null, the append only happens if the memo is still at this version
     */
    @Transactional
    public MemoWriteResult appendMemo(String topicName, String memoName, String content, Long expectedVersion) {
        long additionBytes = utf8Length(requireContent(content));
        checkSize(additionBytes);
        Topic topic = topicRepository.getByName(NameRules.requireTopicName(topicName));
        Memo memo = lockMemo(topic, memoName);
        checkVersion(memo, expectedVersion);
        checkSize(utf8Length(memo.getContent()) + additionBytes);
        memo.appendContent(content, now());
        memoRepository.flush(); // increments the version
        logWrite("Memo appended", topic, memo, content);
        return MemoWriteResult.from(memo);
    }

    private Memo lockMemo(Topic topic, String memoName) {
        String name = NameRules.requireMemoName(memoName);
        return memoRepository.findForUpdate(topic, name)
                .orElseThrow(() -> MemoLakeException.memoNotFound(topic.getName(), name));
    }

    private static void checkVersion(Memo memo, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != memo.getVersion()) {
            throw MemoLakeException.versionConflict(expectedVersion, memo.getVersion());
        }
    }

    private static String requireContent(String content) {
        if (content == null) {
            throw MemoLakeException.invalidRequest("content is required");
        }
        // PostgreSQL TEXT cannot hold U+0000; reject it the same way on every database.
        if (content.indexOf('\0') >= 0) {
            throw MemoLakeException.invalidRequest("content must not contain NUL (U+0000) characters");
        }
        return content;
    }

    private void checkSize(long bytes) {
        if (bytes > maxContentBytes) {
            throw MemoLakeException.contentTooLarge(maxContentBytes);
        }
    }

    /** UTF-8 encoded length without allocating the encoded bytes. */
    static long utf8Length(CharSequence text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes += 1;
            }
            else if (c < 0x800) {
                bytes += 2;
            }
            else if (Character.isHighSurrogate(c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            }
            else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private Instant now() {
        // Database timestamps keep microseconds; truncate so the in-memory value equals the stored one.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void logWrite(String message, Topic topic, Memo memo, String content) {
        // The content itself is never logged.
        long bytes = utf8Length(content);
        log.atInfo()
                .addKeyValue("topic", topic.getName())
                .addKeyValue("memo", memo.getName())
                .addKeyValue("version", memo.getVersion())
                .addKeyValue("bytes", bytes)
                .log("{}: topic='{}', memo='{}', version={}, bytes={}", message, topic.getName(), memo.getName(),
                        memo.getVersion(), bytes);
    }
}

package com.memolake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.MemoLakeException;
import com.memolake.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Many writers on the same memo at the same time. Runs against the real service and database (no mocks).
 */
@SpringBootTest
class MemoConcurrencyTest extends IntegrationTest {

    private static final String TOPIC = "concurrency";

    @Autowired
    TopicService topicService;

    @Autowired
    MemoService memoService;

    @BeforeEach
    void createTopic() {
        topicService.createTopic(TOPIC, "");
    }

    @Test
    void concurrentAppendsLoseNothing() throws Exception {
        memoService.createMemo(TOPIC, "log", "");
        int threads = 16;
        int appendsPerThread = 25;

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            tasks.add(() -> {
                for (int i = 0; i < appendsPerThread; i++) {
                    memoService.appendMemo(TOPIC, "log", "[" + thread + "-" + i + "]", null);
                }
                return null;
            });
        }
        runConcurrently(tasks);

        var memo = memoService.getMemo(TOPIC, "log");
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < appendsPerThread; i++) {
                assertThat(occurrences(memo.content(), "[" + t + "-" + i + "]")).isEqualTo(1);
            }
        }
        int expectedLength = 0;
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < appendsPerThread; i++) {
                expectedLength += ("[" + t + "-" + i + "]").length();
            }
        }
        assertThat(memo.content()).hasSize(expectedLength);
        assertThat(memo.version()).isEqualTo(1 + threads * appendsPerThread);
    }

    @RepeatedTest(10)
    void twoAgentsAppendingAtOnceKeepBothTexts() throws Exception {
        memoService.createMemo(TOPIC, "hello", "Hello");

        runConcurrently(List.of(
                () -> memoService.appendMemo(TOPIC, "hello", " AAA", null),
                () -> memoService.appendMemo(TOPIC, "hello", " BBB", null)));

        assertThat(memoService.getMemo(TOPIC, "hello").content()).isIn("Hello AAA BBB", "Hello BBB AAA");
    }

    @RepeatedTest(10)
    void concurrentConditionalReplacesWithTheSameVersionLetExactlyOneWin() throws Exception {
        memoService.createMemo(TOPIC, "doc", "v1");
        int writers = 8;

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            String content = "written by " + w;
            tasks.add(() -> memoService.replaceMemo(TOPIC, "doc", content, 1L));
        }
        List<Throwable> failures = runConcurrently(tasks);

        assertThat(failures).hasSize(writers - 1)
                .allSatisfy(e -> assertThat(e).isInstanceOfSatisfying(MemoLakeException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT)));
        var memo = memoService.getMemo(TOPIC, "doc");
        assertThat(memo.version()).isEqualTo(2);
        assertThat(memo.content()).startsWith("written by ");
    }

    @Test
    void replaceWithStaleVersionIsRejected() {
        memoService.createMemo(TOPIC, "doc", "v1");
        memoService.appendMemo(TOPIC, "doc", "+", null);

        var thrown = catchThrowableOfType(MemoLakeException.class,
                () -> memoService.replaceMemo(TOPIC, "doc", "overwrite", 1L));

        assertThat(thrown.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
        assertThat(memoService.getMemo(TOPIC, "doc").content()).isEqualTo("v1+");
    }

    @RepeatedTest(10)
    void replaceAndAppendAtTheSameTimeAreSerialized() throws Exception {
        memoService.createMemo(TOPIC, "mixed", "Hello");

        List<Throwable> failures = runConcurrently(List.of(
                () -> memoService.appendMemo(TOPIC, "mixed", " AAA", null),
                () -> memoService.replaceMemo(TOPIC, "mixed", "World", null)));

        assertThat(failures).isEmpty();
        var memo = memoService.getMemo(TOPIC, "mixed");
        // Either the append ran first and was then replaced, or the replace ran first and was appended to.
        assertThat(memo.content()).isIn("World", "World AAA");
        assertThat(memo.version()).isEqualTo(3);
    }

    @RepeatedTest(5)
    void concurrentCreatesOfTheSameMemoLetExactlyOneWin() throws Exception {
        int writers = 8;
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            String content = "created by " + w;
            tasks.add(() -> memoService.createMemo(TOPIC, "race", content));
        }
        List<Throwable> failures = runConcurrently(tasks);

        assertThat(failures).hasSize(writers - 1)
                .allSatisfy(e -> assertThat(e).isInstanceOfSatisfying(MemoLakeException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.MEMO_ALREADY_EXISTS)));
        assertThat(memoService.getMemo(TOPIC, "race").version()).isEqualTo(1);
    }

    /** Starts all tasks at the same moment and returns the exceptions thrown by the ones that failed. */
    private static List<Throwable> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                }
                catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1)) {
            count++;
        }
        return count;
    }
}

package com.memolake.interfaces.rest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memolake.application.MemoService;
import com.memolake.application.TopicService;
import com.memolake.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TopicApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TopicService topicService;

    @Autowired
    MemoService memoService;

    @Test
    void createTopic() throws Exception {
        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "backend-project", "description": "백엔드 개발 협업"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/topic/backend-project"))
                .andExpect(jsonPath("$.name").value("backend-project"))
                .andExpect(jsonPath("$.description").value("백엔드 개발 협업"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-27T00:00:00Z"));
    }

    @Test
    void createTopicWithKoreanNameAndSpaces() throws Exception {
        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "연구 노트_2026-10"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/topic/%EC%97%B0%EA%B5%AC%20%EB%85%B8%ED%8A%B8_2026-10"))
                .andExpect(jsonPath("$.description").value(""));

        mvc.perform(get("/api/topic/{topicName}", "연구 노트_2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("연구 노트_2026-10"));
    }

    @Test
    void duplicateTopicIsConflict() throws Exception {
        topicService.createTopic("backend-project", "first");

        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "backend-project", "description": "second"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Topic already exists: 'backend-project'"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a/b", "a\\\\b", "a.b", " leading", "trailing ", "tab\\there", "new\\nline",
            "emoji🎉", "quote'"})
    void invalidTopicNameIsBadRequest(String name) throws Exception {
        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void topicNameLongerThan100CharactersIsBadRequest() throws Exception {
        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + "가".repeat(100) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/topic").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createTopicRequiresJson() throws Exception {
        mvc.perform(post("/api/topic").contentType(MediaType.TEXT_PLAIN).content("backend-project"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void listIsOrderedByLastMemoModificationWithEmptyTopicsLast() throws Exception {
        topicService.createTopic("empty-b", "no memos");
        topicService.createTopic("old", "");
        topicService.createTopic("new", "");
        topicService.createTopic("empty-a", "no memos");
        memoService.createMemo("old", "m1", "x");
        memoService.createMemo("new", "m1", "x");
        // Touching a memo of "old" makes it the most recently modified topic.
        memoService.appendMemo("old", "m1", "y", null);

        mvc.perform(get("/api/topic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("old", "new", "empty-a", "empty-b")))
                .andExpect(jsonPath("$.content[0].lastMemoModifiedAt", notNullValue()))
                .andExpect(jsonPath("$.content[2].lastMemoModifiedAt", nullValue()))
                .andExpect(jsonPath("$.content[3].lastMemoModifiedAt", nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void topicsWithTheSameModificationTimeAreOrderedByName() throws Exception {
        for (String name : new String[] {"c", "a", "b"}) {
            topicService.createTopic(name, "");
            clock.set(Instant.parse("2026-10-27T12:30:00Z"));
            memoService.createMemo(name, "memo", "x");
        }

        mvc.perform(get("/api/topic"))
                .andExpect(jsonPath("$.content[*].name").value(contains("a", "b", "c")))
                .andExpect(jsonPath("$.content[0].lastMemoModifiedAt").value("2026-10-27T12:30:00Z"));
    }

    @Test
    void listIsPaged() throws Exception {
        for (int i = 1; i <= 5; i++) {
            topicService.createTopic("topic-" + i, "");
            memoService.createMemo("topic-" + i, "memo", "x");
        }

        mvc.perform(get("/api/topic").param("page", "0").param("size", "2"))
                .andExpect(jsonPath("$.content[*].name").value(contains("topic-5", "topic-4")))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/topic").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.content[*].name").value(contains("topic-1")))
                .andExpect(jsonPath("$.page").value(2));
        mvc.perform(get("/api/topic").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void invalidPagingIsBadRequest() throws Exception {
        mvc.perform(get("/api/topic").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/topic").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/topic").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/topic").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/topic").param("size", "100")).andExpect(status().isOk());
    }

    @Test
    void getTopicListsMemosNewestFirstWithoutContent() throws Exception {
        topicService.createTopic("backend-project", "백엔드 개발 협업");
        memoService.createMemo("backend-project", "architecture", "# Architecture");
        memoService.createMemo("backend-project", "agent-a-progress", "started");
        memoService.createMemo("backend-project", "api-design", "draft");
        memoService.appendMemo("backend-project", "architecture", "\nupdated", null);

        mvc.perform(get("/api/topic/backend-project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("backend-project"))
                .andExpect(jsonPath("$.description").value("백엔드 개발 협업"))
                .andExpect(jsonPath("$.memos.content[*].name")
                        .value(contains("architecture", "api-design", "agent-a-progress")))
                .andExpect(jsonPath("$.memos.content[0].updatedAt", notNullValue()))
                .andExpect(jsonPath("$.memos.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.memos.totalElements").value(3))
                .andExpect(jsonPath("$.memos.totalPages").value(1));

        mvc.perform(get("/api/topic/backend-project").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.memos.content[*].name").value(contains("agent-a-progress")))
                .andExpect(jsonPath("$.memos.totalPages").value(2));
    }

    @Test
    void memosWithTheSameModificationTimeAreOrderedByName() throws Exception {
        topicService.createTopic("t", "");
        for (String name : new String[] {"b", "c", "a"}) {
            clock.set(Instant.parse("2026-10-27T12:30:00Z"));
            memoService.createMemo("t", name, "x");
        }

        mvc.perform(get("/api/topic/t"))
                .andExpect(jsonPath("$.memos.content[*].name").value(contains("a", "b", "c")));
    }

    @Test
    void unknownTopicIsNotFound() throws Exception {
        mvc.perform(get("/api/topic/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Topic not found: 'nothing'"));
    }
}

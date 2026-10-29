package com.memolake.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memolake.application.MemoService;
import com.memolake.application.TopicService;
import com.memolake.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MemoApiTest extends IntegrationTest {

    private static final String MEMO = "/api/topic/{topicName}/{memoName}";
    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    TopicService topicService;

    @Autowired
    MemoService memoService;

    @BeforeEach
    void createTopic() {
        topicService.createTopic("backend-project", "백엔드 개발 협업");
    }

    @Test
    void createAndGetMemo() throws Exception {
        mvc.perform(text(post(MEMO, "backend-project", "architecture"), "# Architecture\nSpring Boot 기반 아키텍처"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/topic/backend-project/architecture"))
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.name").value("architecture"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.content").doesNotExist());

        mvc.perform(get(MEMO, "backend-project", "architecture"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.name").value("architecture"))
                .andExpect(jsonPath("$.content").value("# Architecture\nSpring Boot 기반 아키텍처"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void contentWithoutCharsetIsReadAsUtf8() throws Exception {
        mvc.perform(post(MEMO, "backend-project", "memo").contentType(MediaType.TEXT_PLAIN)
                        .content("한글 내용".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());

        assertThat(memoService.getMemo("backend-project", "memo").content()).isEqualTo("한글 내용");
    }

    @Test
    void newlinesWhitespaceAndSpecialCharactersArePreserved() throws Exception {
        String content = "\nfirst line\r\nsecond\tline   \n\n  indented\u001b[0m ✓ 🎉 한글 <>&\"'\\ %20 ${x}\n\n";
        mvc.perform(text(post(MEMO, "backend-project", "raw"), content)).andExpect(status().isCreated());

        assertThat(readContent("raw")).isEqualTo(content);
    }

    @Test
    void nulCharacterIsRejected() throws Exception {
        mvc.perform(text(post(MEMO, "backend-project", "nul"), "a\u0000b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void jsonAndMarkdownAreStoredAsRawText() throws Exception {
        String json = "{\n  \"agent\": \"a\",\n  \"steps\": [1, 2, 3],\n  \"nested\": {\"ok\": true}\n}";
        String markdown = "# Title\n\n- item **bold**\n\n```java\nSystem.out.println(\"hi\");\n```\n";
        mvc.perform(text(post(MEMO, "backend-project", "json"), json)).andExpect(status().isCreated());
        mvc.perform(text(post(MEMO, "backend-project", "markdown"), markdown)).andExpect(status().isCreated());

        // The JSON memo comes back as a string with its original formatting, not as a JSON object.
        mvc.perform(get(MEMO, "backend-project", "json"))
                .andExpect(jsonPath("$.content").isString())
                .andExpect(jsonPath("$.content").value(json));
        assertThat(readContent("markdown")).isEqualTo(markdown);
    }

    @Test
    void emptyBodyCreatesEmptyMemo() throws Exception {
        mvc.perform(text(post(MEMO, "backend-project", "empty"), "")).andExpect(status().isCreated());

        assertThat(readContent("empty")).isEmpty();
    }

    @Test
    void duplicateMemoIsConflict() throws Exception {
        memoService.createMemo("backend-project", "architecture", "v1");

        mvc.perform(text(post(MEMO, "backend-project", "architecture"), "v2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMO_ALREADY_EXISTS"));
        assertThat(readContent("architecture")).isEqualTo("v1");
    }

    @Test
    void sameMemoNameInDifferentTopicsIsAllowed() throws Exception {
        topicService.createTopic("frontend-project", "");
        mvc.perform(text(post(MEMO, "backend-project", "notes"), "back")).andExpect(status().isCreated());
        mvc.perform(text(post(MEMO, "frontend-project", "notes"), "front")).andExpect(status().isCreated());

        assertThat(memoService.getMemo("frontend-project", "notes").content()).isEqualTo("front");
        assertThat(readContent("notes")).isEqualTo("back");
    }

    @Test
    void createInUnknownTopicIsNotFound() throws Exception {
        mvc.perform(text(post(MEMO, "nothing", "memo"), "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
    }

    @Test
    void unknownMemoIsNotFound() throws Exception {
        mvc.perform(get(MEMO, "backend-project", "nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMO_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(text(patch(MEMO, "backend-project", "nothing"), "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMO_NOT_FOUND"));
        mvc.perform(text(put(MEMO, "backend-project", "nothing"), "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMO_NOT_FOUND"));
    }

    @Test
    void invalidMemoNameIsBadRequest() throws Exception {
        mvc.perform(text(post(MEMO, "backend-project", "a.txt"), "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/topic/backend-project/a%5Cb")).andExpect(status().isBadRequest());
    }

    @Test
    void patchReplacesWholeContent() throws Exception {
        memoService.createMemo("backend-project", "memo", "Hello");

        mvc.perform(text(patch(MEMO, "backend-project", "memo"), "World"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get(MEMO, "backend-project", "memo"))
                .andExpect(jsonPath("$.content").value("World"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void putAppendsWithoutAddingNewline() throws Exception {
        memoService.createMemo("backend-project", "memo", "Hello");

        mvc.perform(text(put(MEMO, "backend-project", "memo"), "World"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(text(put(MEMO, "backend-project", "memo"), "\nNext line"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));

        assertThat(readContent("memo")).isEqualTo("HelloWorld\nNext line");
    }

    @Test
    void writesUpdateTimestampAndVersion() throws Exception {
        memoService.createMemo("backend-project", "memo", "a");
        var created = memoService.getMemo("backend-project", "memo");

        memoService.appendMemo("backend-project", "memo", "b", null);
        var appended = memoService.getMemo("backend-project", "memo");
        assertThat(appended.createdAt()).isEqualTo(created.createdAt());
        assertThat(appended.updatedAt()).isAfter(created.updatedAt());
        assertThat(appended.version()).isEqualTo(created.version() + 1);

        memoService.replaceMemo("backend-project", "memo", "c", null);
        var replaced = memoService.getMemo("backend-project", "memo");
        assertThat(replaced.updatedAt()).isAfter(appended.updatedAt());
        assertThat(replaced.version()).isEqualTo(appended.version() + 1);
    }

    @Test
    void patchWithMatchingIfMatchSucceeds() throws Exception {
        memoService.createMemo("backend-project", "memo", "v1");

        mvc.perform(text(patch(MEMO, "backend-project", "memo"), "v2").header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        // Unquoted versions are accepted as well.
        mvc.perform(text(patch(MEMO, "backend-project", "memo"), "v3").header("If-Match", "2"))
                .andExpect(status().isOk());
        assertThat(readContent("memo")).isEqualTo("v3");
    }

    @Test
    void patchWithStaleIfMatchIsPreconditionFailed() throws Exception {
        memoService.createMemo("backend-project", "memo", "v1");
        memoService.replaceMemo("backend-project", "memo", "v2 by another agent", null);

        mvc.perform(text(patch(MEMO, "backend-project", "memo"), "v2 by me").header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(readContent("memo")).isEqualTo("v2 by another agent");
    }

    @Test
    void putWithStaleIfMatchIsPreconditionFailed() throws Exception {
        memoService.createMemo("backend-project", "memo", "Hello");
        mvc.perform(text(put(MEMO, "backend-project", "memo"), " World").header("If-Match", "\"1\""))
                .andExpect(status().isOk());

        // A blind retry of the same request does not append twice.
        mvc.perform(text(put(MEMO, "backend-project", "memo"), " World").header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed());
        assertThat(readContent("memo")).isEqualTo("Hello World");
    }

    @Test
    void malformedIfMatchIsBadRequest() throws Exception {
        memoService.createMemo("backend-project", "memo", "v1");

        mvc.perform(text(patch(MEMO, "backend-project", "memo"), "v2").header("If-Match", "\"abc\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void contentLargerThanOneMegabyteIsRejected() throws Exception {
        String max = "a".repeat(1024 * 1024);
        mvc.perform(text(post(MEMO, "backend-project", "max"), max)).andExpect(status().isCreated());

        mvc.perform(text(post(MEMO, "backend-project", "too-big"), max + "a"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        mvc.perform(text(patch(MEMO, "backend-project", "max"), max + "a"))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void sizeLimitCountsUtf8Bytes() throws Exception {
        // 349,526 Hangul syllables = 1,048,578 bytes in UTF-8 (just over 1 MB) but only ~350k characters.
        mvc.perform(text(post(MEMO, "backend-project", "korean"), "가".repeat(349_526)))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void appendBeyondMaximumSizeIsRejected() throws Exception {
        memoService.createMemo("backend-project", "memo", "a".repeat(1024 * 1024 - 1));

        mvc.perform(text(put(MEMO, "backend-project", "memo"), "b")).andExpect(status().isOk());
        mvc.perform(text(put(MEMO, "backend-project", "memo"), "c"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        assertThat(memoService.getMemo("backend-project", "memo").version()).isEqualTo(2);
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        // Rejected during request mapping; the JSON error body is checked in HttpErrorResponseTest.
        mvc.perform(post(MEMO, "backend-project", "memo").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(put(MEMO, "backend-project", "memo").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("a=b"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(MEMO, "backend-project", "memo").contentType("text/plain; charset=no-such-charset")
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void invalidUtf8IsBadRequest() throws Exception {
        mvc.perform(post(MEMO, "backend-project", "memo").contentType(TEXT_UTF8)
                        .content(new byte[] {'o', 'k', (byte) 0xC3, (byte) 0x28}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete(MEMO, "backend-project", "memo")).andExpect(status().isMethodNotAllowed());
    }

    private static MockHttpServletRequestBuilder text(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(TEXT_UTF8).content(body.getBytes(StandardCharsets.UTF_8));
    }

    private String readContent(String memoName) throws Exception {
        String json = mvc.perform(get(MEMO, "backend-project", memoName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return jsonMapper.readTree(json).get("content").asString();
    }
}

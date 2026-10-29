package com.memolake.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memolake.application.MemoService;
import com.memolake.application.TopicService;
import com.memolake.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WebPageTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TopicService topicService;

    @Autowired
    MemoService memoService;

    @Test
    void rootRedirectsToMain() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/web/main"));
    }

    @Test
    void mainPageShowsThreeMostRecentlyModifiedTopics() throws Exception {
        for (String name : new String[] {"oldest", "second", "third", "newest"}) {
            topicService.createTopic(name, name + " 설명");
            memoService.createMemo(name, "memo", "x");
        }

        String html = html(mvc.perform(get("/web/main")).andExpect(status().isOk()).andReturn());

        assertThat(html).contains("Memo Lake", "최근 수정된 토픽", "모든 토픽 보기", "href=\"/web/topic\"");
        assertThat(html).contains("newest 설명", "href=\"/web/topic/newest\"").doesNotContain("oldest");
        assertThat(html.indexOf(">newest<")).isLessThan(html.indexOf(">third<"));
        assertThat(html.indexOf(">third<")).isLessThan(html.indexOf(">second<"));
        assertThat(html).contains("datetime=\"2026-10-27T");
    }

    @Test
    void topicListIsPagedByTen() throws Exception {
        for (int i = 1; i <= 12; i++) {
            String name = "topic-%02d".formatted(i);
            topicService.createTopic(name, "");
            memoService.createMemo(name, "memo", "x");
        }

        String first = html(mvc.perform(get("/web/topic")).andExpect(status().isOk()).andReturn());
        assertThat(first).contains("topic-12", "topic-03", "1 / 2", "href=\"/web/topic?page=1\"", "새 토픽 만들기")
                .doesNotContain(">topic-02<", ">topic-01<");

        String second = html(mvc.perform(get("/web/topic").param("page", "1")).andExpect(status().isOk()).andReturn());
        assertThat(second).contains(">topic-02<", ">topic-01<", "2 / 2").doesNotContain(">topic-03<");
    }

    @Test
    void topicPageListsMemosAndEncodesLinks() throws Exception {
        topicService.createTopic("백엔드 프로젝트", "설명");
        memoService.createMemo("백엔드 프로젝트", "진행 상황", "x");

        mvc.perform(get("/web/topic/{name}", "백엔드 프로젝트"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("메모 발행"),
                        containsString(">진행 상황<"),
                        containsString("href=\"/web/topic/%EB%B0%B1%EC%97%94%EB%93%9C%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8/"
                                + "%EC%A7%84%ED%96%89%20%EC%83%81%ED%99%A9\""))));
    }

    @Test
    void memoPageShowsRawContentWithHtmlEscaped() throws Exception {
        topicService.createTopic("t", "");
        String content = "\n<script>alert('xss')</script>\n<img src=x onerror=alert(1)>\n  {\"json\": true}\n# Markdown";
        memoService.createMemo("t", "m", content);

        String html = html(mvc.perform(get("/web/topic/t/m")).andExpect(status().isOk()).andReturn());

        assertThat(html).doesNotContain("<script>alert", "<img src=x");
        assertThat(html).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;",
                "&lt;img src=x onerror=alert(1)&gt;", "  {&quot;json&quot;: true}\n# Markdown");
        // The browser drops one newline right after <pre>, so the leading newline of the memo survives.
        assertThat(html).contains("<pre class=\"memo-content\" id=\"memo-content\">\n\n&lt;script&gt;");
        assertThat(html).contains("메모 수정", "토픽으로 돌아가기", "href=\"/web/topic/t/m/edit\"");
    }

    @Test
    void pagesSendSecurityHeaders() throws Exception {
        mvc.perform(get("/web/main"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void createTopicThroughForm() throws Exception {
        mvc.perform(post("/web/topic").param("name", "새 토픽").param("description", "웹에서 생성"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/topic/%EC%83%88%20%ED%86%A0%ED%94%BD"))
                .andExpect(flash().attribute("notice", "토픽을 만들었습니다."));

        assertThat(topicService.getTopicInfo("새 토픽").description()).isEqualTo("웹에서 생성");
    }

    @Test
    void createTopicFormShowsValidationErrors() throws Exception {
        mvc.perform(post("/web/topic").param("name", "bad/name"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("이름은 1~100자의"),
                        containsString("<details class=\"panel\" open=\"open\">"))));

        topicService.createTopic("taken", "");
        mvc.perform(post("/web/topic").param("name", "taken"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("같은 이름의 토픽이 이미 있습니다.")));
    }

    @Test
    void newMemoFlowOpensEditorAndCreatesMemo() throws Exception {
        topicService.createTopic("t", "");

        mvc.perform(post("/web/topic/t").param("memoName", "new memo"))
                .andExpect(redirectedUrl("/web/topic/t/new%20memo/edit"));

        String editor = html(mvc.perform(get("/web/topic/t/new memo/edit")).andExpect(status().isOk()).andReturn());
        assertThat(editor).contains("새 메모 작성", "<textarea").doesNotContain("name=\"version\"");

        // Browsers send textarea line breaks as CRLF; they are stored as LF.
        mvc.perform(post("/web/topic/t/new memo/edit").param("content", "line 1\r\nline 2\r\n"))
                .andExpect(redirectedUrl("/web/topic/t/new%20memo"))
                .andExpect(flash().attribute("notice", "메모를 저장했습니다."));

        assertThat(memoService.getMemo("t", "new memo").content()).isEqualTo("line 1\nline 2\n");
    }

    @Test
    void newMemoFormRejectsExistingName() throws Exception {
        topicService.createTopic("t", "");
        memoService.createMemo("t", "m", "x");

        mvc.perform(post("/web/topic/t").param("memoName", "m"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("같은 이름의 메모가 이미 있습니다.")));
    }

    @Test
    void editorLoadsExistingMemoAndReplacesIt() throws Exception {
        topicService.createTopic("t", "");
        memoService.createMemo("t", "m", "\nold <b>content</b>");

        String editor = html(mvc.perform(get("/web/topic/t/m/edit")).andExpect(status().isOk()).andReturn());
        assertThat(editor).contains("name=\"version\" value=\"1\"",
                "spellcheck=\"false\">\n\nold &lt;b&gt;content&lt;/b&gt;</textarea>");

        mvc.perform(post("/web/topic/t/m/edit").param("content", "new content").param("version", "1"))
                .andExpect(redirectedUrl("/web/topic/t/m"));

        var memo = memoService.getMemo("t", "m");
        assertThat(memo.content()).isEqualTo("new content");
        assertThat(memo.version()).isEqualTo(2);
    }

    @Test
    void editorReportsConflictAndKeepsTheUsersText() throws Exception {
        topicService.createTopic("t", "");
        memoService.createMemo("t", "m", "v1");
        memoService.appendMemo("t", "m", " + agent change", null);

        String html = html(mvc.perform(post("/web/topic/t/m/edit").param("content", "my edit").param("version", "1"))
                .andExpect(status().isOk()).andReturn());

        assertThat(html).contains("먼저 수정했습니다", "my edit</textarea>", "name=\"version\" value=\"2\"");
        assertThat(memoService.getMemo("t", "m").content()).isEqualTo("v1 + agent change");
    }

    @Test
    void tooLargeSaveKeepsTheVersionTheUserStartedFrom() throws Exception {
        topicService.createTopic("t", "");
        memoService.createMemo("t", "m", "v1");
        memoService.appendMemo("t", "m", "+", null);

        String html = html(mvc.perform(post("/web/topic/t/m/edit")
                        .param("content", "a".repeat(1024 * 1024 + 1)).param("version", "1"))
                .andExpect(status().isOk()).andReturn());

        assertThat(html).contains("최대 크기", "name=\"version\" value=\"1\"");
    }

    @Test
    void missingTopicOrMemoShowsErrorPage() throws Exception {
        mvc.perform(get("/web/topic/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("토픽을 찾을 수 없습니다.")));

        topicService.createTopic("t", "");
        mvc.perform(get("/web/topic/t/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("메모를 찾을 수 없습니다.")));
        mvc.perform(get("/web/topic/nothing/m/edit")).andExpect(status().isNotFound());
        mvc.perform(get("/web/topic/t/bad.name/edit")).andExpect(status().isBadRequest());
    }

    @Test
    void crossSiteFormPostIsRejected() throws Exception {
        mvc.perform(post("/web/topic").param("name", "evil").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/web/topic").param("name", "fine").header("Origin", "http://localhost"))
                .andExpect(status().is3xxRedirection());

        assertThat(topicRepository.findByName("evil")).isEmpty();
    }

    @Test
    void staticAssetsAreServed() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("innerHTML"))));
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}

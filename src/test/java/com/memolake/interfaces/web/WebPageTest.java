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

        String html = html(mvc.perform(get("/web/topic/t/m").param("view", "raw")).andExpect(status().isOk())
                .andReturn());

        assertThat(html).doesNotContain("<script>alert", "<img src=x");
        assertThat(markdownPane(html)).doesNotContain("script", "onerror");
        assertThat(html).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;",
                "&lt;img src=x onerror=alert(1)&gt;", "  {&quot;json&quot;: true}\n# Markdown");
        // The browser drops one newline right after <pre>, so the leading newline of the memo survives.
        assertThat(html).contains("<pre class=\"memo-content\" id=\"memo-content\">\n\n&lt;script&gt;");
        assertThat(html).contains("메모 수정", "토픽으로 돌아가기", "href=\"/web/topic/t/m/edit\"");
        // The raw view is selected; the other panes are present but hidden.
        assertThat(html).contains("href=\"/web/topic/t/m?view=raw\"", "data-view=\"raw\" aria-current=\"page\"",
                "id=\"memo-markdown\" hidden=\"hidden\"", "id=\"memo-html\"", "hidden=\"hidden\"></iframe>")
                .doesNotContain(" src=\"/web/topic/t/m/html\"");
    }

    @Test
    void memoPageRendersMarkdownByDefaultAndSanitizesIt() throws Exception {
        topicService.createTopic("t", "");
        memoService.createMemo("t", "m", """
                # 제목

                - [x] 완료
                - [ ] 대기

                | a | b |
                |---|---|
                | 1 | 2 |

                ~~지움~~ <b>굵게</b> <script>alert(1)</script> <span onclick="x()">클릭</span>
                [나쁜 링크](javascript:alert(1)) [다른 메모](other-memo) https://example.com/x
                첫 줄
                둘째 줄
                """);

        String html = html(mvc.perform(get("/web/topic/t/m")).andExpect(status().isOk()).andReturn());

        assertThat(html).contains("data-view=\"markdown\" aria-current=\"page\"",
                "<pre class=\"memo-content\" id=\"memo-content\" hidden=\"hidden\">",
                "<div class=\"memo-markdown\" id=\"memo-markdown\">");
        String rendered = markdownPane(html);
        assertThat(rendered).contains(">제목</h1>", "type=\"checkbox\"", "checked", "<th>a</th>", "<td>2</td>",
                "<del>지움</del>", "<b>굵게</b>", "<span>클릭</span>", "href=\"other-memo\"",
                "<a href=\"https://example.com/x\">https://example.com/x</a>", "첫 줄<br>\n둘째 줄");
        assertThat(rendered).doesNotContain("<script", "onclick", "javascript:");
    }

    @Test
    void memoPagePicksTheDefaultViewFromTheContent() throws Exception {
        topicService.createTopic("t", "");
        memoService.createMemo("t", "json", "{\"a\": [1, 2]}");
        memoService.createMemo("t", "page", "<!DOCTYPE html>\n<html><body><h1>Hi</h1></body></html>");

        String json = html(mvc.perform(get("/web/topic/t/json")).andExpect(status().isOk()).andReturn());
        assertThat(json).contains("data-view=\"raw\" aria-current=\"page\"",
                "<pre class=\"memo-content\" id=\"memo-content\">");

        String page = html(mvc.perform(get("/web/topic/t/page")).andExpect(status().isOk()).andReturn());
        assertThat(page).contains("data-view=\"html\" aria-current=\"page\"", "sandbox=\"\"",
                "src=\"/web/topic/t/page/html\"></iframe>")
                .doesNotContain("<h1>Hi</h1></body>");

        // An unknown value falls back to detection as well.
        String unknown = html(mvc.perform(get("/web/topic/t/json").param("view", "pdf")).andExpect(status().isOk())
                .andReturn());
        assertThat(unknown).contains("data-view=\"raw\" aria-current=\"page\"");
    }

    @Test
    void memoHtmlViewIsServedAsSandboxedDocument() throws Exception {
        topicService.createTopic("t", "");
        String content = "<!DOCTYPE html>\n<html><body><h1>안녕</h1><script>alert(1)</script></body></html>";
        memoService.createMemo("t", "page", content);

        MvcResult result = mvc.perform(get("/web/topic/t/page/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Content-Security-Policy", allOf(
                        containsString("sandbox;"), containsString("frame-ancestors 'self'"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        // The document is sent unchanged; the headers above (not escaping) keep it harmless.
        assertThat(html(result)).isEqualTo(content);
        // The security filter's headers are replaced, not duplicated: two policies would both be enforced.
        assertThat(result.getResponse().getHeaders("Content-Security-Policy")).hasSize(1);
        assertThat(result.getResponse().getHeaders("X-Frame-Options")).hasSize(1);

        mvc.perform(get("/web/topic/t/nothing/html")).andExpect(status().isNotFound());
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

    /** The server-rendered Markdown pane of a memo page. */
    private static String markdownPane(String html) {
        int start = html.indexOf("<div class=\"memo-markdown\"");
        int end = html.indexOf("<!-- The HTML document", start);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        return html.substring(start, end);
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}

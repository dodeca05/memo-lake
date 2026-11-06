package com.memolake.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rendersGitHubFlavouredMarkdown() {
        String html = renderer.render("""
                ## 상태

                | 항목 | 값 |
                |:----|---:|
                | a | 1 |

                - [x] done
                - [ ] todo

                ~~old~~ **new** `code`

                ```java
                int x = 1;
                ```
                """);

        assertThat(html).contains("<h2", ">상태</h2>", "<table>", "<th align=\"left\">항목</th>", "<td align=\"right\">1</td>",
                "<input type=\"checkbox\" disabled checked>", "<del>old</del>", "<strong>new</strong>",
                "<code>code</code>", "<pre><code class=\"language-java\">int x = 1;\n</code></pre>");
    }

    @Test
    void singleLineBreaksBecomeBr() {
        assertThat(renderer.render("a\nb")).isEqualTo("<p>a<br>\nb</p>");
    }

    @Test
    void keepsWhitespaceInsideCodeBlocks() {
        assertThat(renderer.render("    x  =  1\n      y\n")).isEqualTo("<pre><code>x  =  1\n  y\n</code></pre>");
    }

    @Test
    void removesScriptsEventHandlersAndDangerousUrls() {
        String html = renderer.render("""
                <script>alert(1)</script>
                <img src="x" onerror="alert(1)">
                <a href="javascript:alert(1)">bad</a>
                <a href="https://example.com" target="_blank" style="color:red">ok</a>
                <iframe src="https://evil.example"></iframe>
                <form action="/api/topic"><input type="text" name="n"><button>go</button></form>
                <div class="button" id="memo-content">styled</div>

                [bad](vbscript:x) ![img](javascript:alert(1)) [rel](sub/page.md) [anchor](#top)
                """);

        assertThat(html).doesNotContain("<script", "onerror", "javascript:", "vbscript:", "<iframe", "<form",
                "type=\"text\"", "<button", "style=", "target=", "class=\"button\"", "id=\"memo-content\"");
        assertThat(html).contains("<img src=\"x\">", "<a href=\"https://example.com\">ok</a>", ">bad</a>",
                "<a href=\"sub/page.md\">rel</a>", "<a href=\"#top\">anchor</a>", "<div>styled</div>");
    }

    @Test
    void onlyLanguageClassesSurviveOnCode() {
        assertThat(renderer.render("<code class=\"button language-sql\">x</code>")).contains("class=\"language-sql\"");
        assertThat(renderer.render("<code class=\"button\">x</code>")).isEqualTo("<p><code>x</code></p>");
    }

    @Test
    void allowsDataImagesAndHeadingIds() {
        String html = renderer.render("# Intro\n\n![dot](data:image/png;base64,iVBORw0KGgo=)");
        assertThat(html).contains("<h1 id=\"intro\">Intro</h1>", "<img src=\"data:image/png;base64,iVBORw0KGgo=\" alt=\"dot\">");
    }

    @Test
    void emptyAndNullContent() {
        assertThat(renderer.render("")).isEmpty();
        assertThat(renderer.render(null)).isEmpty();
    }
}

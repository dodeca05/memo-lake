package com.memolake.interfaces.web;

import java.util.List;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Renders memo content as GitHub flavoured Markdown (tables, strikethrough, task lists, autolinks, heading ids)
 * and sanitizes the result so that memos written by agents or other people cannot run scripts in the browser.
 *
 * <p>Raw HTML inside the Markdown is kept, but only elements and attributes on the allow list survive
 * ({@code <script>}, event handlers, {@code javascript:} links, {@code style} attributes, ... are removed).
 * Single line breaks are rendered as {@code <br>} like GitHub comments, since memos are usually notes and logs.
 */
@Component
public class MarkdownRenderer {

    /** Only used to decide whether a relative link is allowed; links are written back unchanged. */
    private static final String BASE_URI = "http://memolake.invalid/";

    private static final Set<String> ALLOWED_INPUT_TYPES = Set.of("checkbox");

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Cleaner cleaner;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create(), StrikethroughExtension.create(),
                AutolinkExtension.create(), TaskListItemsExtension.create(), HeadingAnchorExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .softbreak("<br />\n")
                .sanitizeUrls(true)
                .build();
        this.cleaner = new Cleaner(safelist());
    }

    private static Safelist safelist() {
        return Safelist.relaxed()
                .addTags("hr", "del", "s", "ins", "kbd", "mark", "samp", "var", "abbr", "details", "summary",
                        "input", "section", "figure", "figcaption")
                .addAttributes("input", "type", "checked", "disabled")
                .addAttributes("code", "class")
                .addAttributes("th", "align")
                .addAttributes("td", "align")
                .addAttributes("abbr", "title")
                .addAttributes("details", "open")
                .addAttributes("h1", "id")
                .addAttributes("h2", "id")
                .addAttributes("h3", "id")
                .addAttributes("h4", "id")
                .addAttributes("h5", "id")
                .addAttributes("h6", "id")
                .addProtocols("img", "src", "data")
                .preserveRelativeLinks(true);
    }

    /** @return sanitized HTML fragment (no surrounding {@code <html>}/{@code <body>}) */
    public String render(String markdown) {
        String html = renderer.render(parser.parse(markdown == null ? "" : markdown));
        Document dirty = Jsoup.parse(html, BASE_URI);
        Document clean = cleaner.clean(dirty);
        clean.outputSettings().prettyPrint(false);
        tidy(clean.body());
        return clean.body().html().strip();
    }

    /** Last pass over what the allow list cannot express: attribute values. */
    private static void tidy(Element body) {
        for (Element input : body.select("input")) {
            if (!ALLOWED_INPUT_TYPES.contains(input.attr("type").toLowerCase(java.util.Locale.ROOT))) {
                input.remove();
                continue;
            }
            input.attr("disabled", "");
        }
        for (Element code : body.select("code[class]")) {
            String language = null;
            for (String cls : code.classNames()) {
                if (cls.startsWith("language-") && cls.length() > "language-".length()) {
                    language = cls;
                    break;
                }
            }
            if (language == null) {
                code.removeAttr("class");
            }
            else {
                code.attr("class", language);
            }
        }
    }
}

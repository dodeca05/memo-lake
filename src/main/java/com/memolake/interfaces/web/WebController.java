package com.memolake.interfaces.web;

import com.memolake.application.MemoService;
import com.memolake.application.NameRules;
import com.memolake.application.TopicService;
import com.memolake.application.dto.MemoDetail;
import com.memolake.application.dto.TopicInfo;
import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.MemoLakeException;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Server-rendered pages for people. Uses the same services as the REST API and the MCP tools.
 */
@Controller
public class WebController {

    static final int PAGE_SIZE = 10;
    static final int RECENT_TOPIC_COUNT = 3;

    private final TopicService topicService;
    private final MemoService memoService;

    public WebController(TopicService topicService, MemoService memoService) {
        this.topicService = topicService;
        this.memoService = memoService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/web/main";
    }

    @GetMapping("/web/main")
    public String main(Model model) {
        model.addAttribute("recentTopics", topicService.listTopics(0, RECENT_TOPIC_COUNT).content());
        return "main";
    }

    @GetMapping("/web/topic")
    public String topics(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("topicForm", new TopicForm());
        return topicsPage(page, model);
    }

    @PostMapping("/web/topic")
    public String createTopic(@Valid @ModelAttribute("topicForm") TopicForm form, BindingResult binding, Model model,
                              RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                TopicInfo topic = topicService.createTopic(form.getName(), form.getDescription());
                redirect.addFlashAttribute("notice", "토픽을 만들었습니다.");
                return "redirect:" + WebPaths.topic(topic.name());
            }
            catch (MemoLakeException e) {
                binding.reject(e.getCode().name(), WebMessages.of(e));
            }
        }
        model.addAttribute("showTopicForm", true);
        return topicsPage(0, model);
    }

    @GetMapping("/web/topic/{topicName}")
    public String topic(@PathVariable String topicName, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("memoForm", new MemoNameForm());
        return topicPage(topicName, page, model);
    }

    /** "New memo" form: validates the name, then opens the editor in create mode. */
    @PostMapping("/web/topic/{topicName}")
    public String startMemo(@PathVariable String topicName, @Valid @ModelAttribute("memoForm") MemoNameForm form,
                            BindingResult binding, Model model) {
        if (!binding.hasErrors()) {
            if (memoService.findMemo(topicName, form.getMemoName()).isEmpty()) {
                return "redirect:" + WebPaths.editor(topicName, NameRules.normalize(form.getMemoName()));
            }
            binding.rejectValue("memoName", ErrorCode.MEMO_ALREADY_EXISTS.name(),
                    "같은 이름의 메모가 이미 있습니다. 목록에서 메모를 열어 수정하세요.");
        }
        model.addAttribute("showMemoForm", true);
        return topicPage(topicName, 0, model);
    }

    @GetMapping("/web/topic/{topicName}/{memoName}")
    public String memo(@PathVariable String topicName, @PathVariable String memoName, Model model) {
        model.addAttribute("topic", topicService.getTopicInfo(topicName));
        model.addAttribute("memo", memoService.getMemo(topicName, memoName));
        return "memo";
    }

    /** Editor: edits the memo if it exists, otherwise creates it. */
    @GetMapping("/web/topic/{topicName}/{memoName}/edit")
    public String editor(@PathVariable String topicName, @PathVariable String memoName, Model model) {
        Optional<MemoDetail> memo = memoService.findMemo(topicName, memoName);
        return editorPage(topicName, memoName, memo, memo.map(MemoDetail::content).orElse(""),
                memo.map(MemoDetail::version).orElse(null), model);
    }

    @PostMapping("/web/topic/{topicName}/{memoName}/edit")
    public String saveMemo(@PathVariable String topicName, @PathVariable String memoName,
                           @RequestParam(required = false) String content,
                           @RequestParam(required = false) Long version,
                           Model model, RedirectAttributes redirect) {
        if (content == null) {
            // Tomcat drops form parameters when the post exceeds server.tomcat.max-http-form-post-size.
            Optional<MemoDetail> memo = memoService.findMemo(topicName, memoName);
            model.addAttribute("error", "메모 내용을 받지 못했습니다. 내용이 너무 크면 저장할 수 없습니다.");
            return editorPage(topicName, memoName, memo, memo.map(MemoDetail::content).orElse(""), version, model);
        }
        // Browsers submit textarea line breaks as CRLF; store them as LF like agents do.
        String text = content.replace("\r\n", "\n");
        try {
            if (version == null) {
                memoService.createMemo(topicName, memoName, text);
            }
            else {
                memoService.replaceMemo(topicName, memoName, text, version);
            }
        }
        catch (MemoLakeException e) {
            switch (e.getCode()) {
                case VERSION_CONFLICT, MEMO_ALREADY_EXISTS, MEMO_NOT_FOUND, CONTENT_TOO_LARGE -> {
                    // Keep the user's text and show what happened.
                    Optional<MemoDetail> latest = memoService.findMemo(topicName, memoName);
                    model.addAttribute("error", WebMessages.of(e));
                    Long formVersion = version;
                    if (e.getCode() == ErrorCode.VERSION_CONFLICT || e.getCode() == ErrorCode.MEMO_ALREADY_EXISTS) {
                        // The form now carries the latest version: saving again knowingly overwrites the other change.
                        model.addAttribute("conflict", true);
                        formVersion = latest.map(MemoDetail::version).orElse(null);
                    }
                    return editorPage(topicName, memoName, latest, text, formVersion, model);
                }
                default -> throw e;
            }
        }
        redirect.addFlashAttribute("notice", "메모를 저장했습니다.");
        return "redirect:" + WebPaths.memo(topicName, NameRules.normalize(memoName));
    }

    private String topicsPage(int page, Model model) {
        model.addAttribute("topics", topicService.listTopics(page, PAGE_SIZE));
        return "topics";
    }

    private String topicPage(String topicName, int page, Model model) {
        model.addAttribute("topic", topicService.getTopic(topicName, page, PAGE_SIZE));
        return "topic";
    }

    /**
     * @param version version sent back on save: null creates the memo, otherwise it replaces the memo only if it is
     *                still at this version
     */
    private String editorPage(String topicName, String memoName, Optional<MemoDetail> memo, String content,
                              Long version, Model model) {
        model.addAttribute("topic", topicService.getTopicInfo(topicName));
        model.addAttribute("memoName", NameRules.requireMemoName(memoName));
        model.addAttribute("memo", memo.orElse(null));
        model.addAttribute("content", content);
        model.addAttribute("version", version);
        return "editor";
    }
}

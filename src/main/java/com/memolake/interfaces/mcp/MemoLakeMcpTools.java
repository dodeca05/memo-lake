package com.memolake.interfaces.mcp;

import com.memolake.application.MemoService;
import com.memolake.application.NameRules;
import com.memolake.application.Paging;
import com.memolake.application.TopicService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools exposed at {@code /mcp} (Streamable HTTP). Each tool calls the same application service as the
 * matching REST endpoint; nothing here goes through HTTP.
 */
@Component
public class MemoLakeMcpTools {

    private static final String TOPIC_NAME = "Topic name. " + NameRules.RULE_DESCRIPTION + ".";
    private static final String MEMO_NAME = "Memo name, unique within its topic. " + NameRules.RULE_DESCRIPTION + ".";
    private static final String PAGE = "Zero-based page number. Default 0.";
    private static final String SIZE = "Page size, 1-" + Paging.MAX_SIZE + ". Default " + Paging.DEFAULT_SIZE + ".";

    private final TopicService topicService;
    private final MemoService memoService;
    private final McpToolResults results;

    public MemoLakeMcpTools(TopicService topicService, MemoService memoService, McpToolResults results) {
        this.topicService = topicService;
        this.memoService = memoService;
        this.results = results;
    }

    @McpTool(name = "list_topics", title = "List topics",
            description = """
                    List all topics, most recently active first (by the newest memo modification time).
                    Returns {content: [{name, description, lastMemoModifiedAt}], page, size, totalElements, totalPages}.
                    lastMemoModifiedAt is null for topics without memos; those come last.""",
            annotations = @McpAnnotations(title = "List topics", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public CallToolResult listTopics(
            @McpToolParam(description = PAGE, required = false) Integer page,
            @McpToolParam(description = SIZE, required = false) Integer size) {
        return results.run(() -> topicService.listTopics(orDefault(page, Paging.DEFAULT_PAGE),
                orDefault(size, Paging.DEFAULT_SIZE)));
    }

    @McpTool(name = "create_topic", title = "Create topic",
            description = """
                    Create a new topic (a named space that groups memos). Fails with TOPIC_ALREADY_EXISTS if the name is taken.
                    Returns {name, description, createdAt, updatedAt}.""",
            annotations = @McpAnnotations(title = "Create topic", readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public CallToolResult createTopic(
            @McpToolParam(description = TOPIC_NAME) String name,
            @McpToolParam(description = "What the topic is about, up to 1000 characters. Optional.", required = false)
            String description) {
        return results.run(() -> topicService.createTopic(name, description));
    }

    @McpTool(name = "get_topic", title = "Get topic",
            description = """
                    Get a topic's description and one page of its memos, most recently modified first.
                    Memo content is not included; use get_memo to read a memo.
                    Returns {name, description, memos: {content: [{name, updatedAt}], page, size, totalElements, totalPages}}.""",
            annotations = @McpAnnotations(title = "Get topic", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public CallToolResult getTopic(
            @McpToolParam(description = TOPIC_NAME) String topicName,
            @McpToolParam(description = PAGE, required = false) Integer page,
            @McpToolParam(description = SIZE, required = false) Integer size) {
        return results.run(() -> topicService.getTopic(topicName, orDefault(page, Paging.DEFAULT_PAGE),
                orDefault(size, Paging.DEFAULT_SIZE)));
    }

    @McpTool(name = "get_memo", title = "Get memo",
            description = """
                    Read a memo. Returns {name, content, createdAt, updatedAt, version}.
                    content is the raw text exactly as stored. Pass version as expectedVersion to replace_memo or
                    append_memo to make the write fail if someone else changed the memo in between.""",
            annotations = @McpAnnotations(title = "Get memo", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public CallToolResult getMemo(
            @McpToolParam(description = TOPIC_NAME) String topicName,
            @McpToolParam(description = MEMO_NAME) String memoName) {
        return results.run(() -> memoService.getMemo(topicName, memoName));
    }

    @McpTool(name = "create_memo", title = "Create memo",
            description = """
                    Create a new memo in an existing topic. content is stored verbatim (plain text, Markdown, JSON, code...).
                    Fails with TOPIC_NOT_FOUND or MEMO_ALREADY_EXISTS. Returns {name, createdAt, updatedAt, version}.""",
            annotations = @McpAnnotations(title = "Create memo", readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public CallToolResult createMemo(
            @McpToolParam(description = TOPIC_NAME) String topicName,
            @McpToolParam(description = MEMO_NAME) String memoName,
            @McpToolParam(description = "Memo text, stored exactly as given. Max 1 MB (UTF-8) by default.")
            String content) {
        return results.run(() -> memoService.createMemo(topicName, memoName, content));
    }

    @McpTool(name = "replace_memo", title = "Replace memo",
            description = """
                    Replace the entire content of an existing memo with the given text.
                    Without expectedVersion the last write wins and overwrites concurrent changes.
                    With expectedVersion (from get_memo) the replace fails with VERSION_CONFLICT if the memo has changed.
                    Returns {name, createdAt, updatedAt, version}.""",
            annotations = @McpAnnotations(title = "Replace memo", readOnlyHint = false, destructiveHint = true,
                    idempotentHint = true, openWorldHint = false))
    public CallToolResult replaceMemo(
            @McpToolParam(description = TOPIC_NAME) String topicName,
            @McpToolParam(description = MEMO_NAME) String memoName,
            @McpToolParam(description = "New memo text; replaces the old content completely.") String content,
            @McpToolParam(description = "Only replace if the memo is still at this version. Optional.",
                    required = false) Long expectedVersion) {
        return results.run(() -> memoService.replaceMemo(topicName, memoName, content, expectedVersion));
    }

    @McpTool(name = "append_memo", title = "Append to memo",
            description = """
                    Append text to the end of an existing memo. No separator or newline is added:
                    include a leading "\\n" in content if you want the text on a new line.
                    Concurrent appends from several agents are serialized, none is lost.
                    Not idempotent: retrying a successful call appends the text again. Pass expectedVersion
                    to make a retry fail with VERSION_CONFLICT instead. Returns {name, createdAt, updatedAt, version}.""",
            annotations = @McpAnnotations(title = "Append to memo", readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public CallToolResult appendMemo(
            @McpToolParam(description = TOPIC_NAME) String topicName,
            @McpToolParam(description = MEMO_NAME) String memoName,
            @McpToolParam(description = "Text to append, exactly as given.") String content,
            @McpToolParam(description = "Only append if the memo is still at this version. Optional.",
                    required = false) Long expectedVersion) {
        return results.run(() -> memoService.appendMemo(topicName, memoName, content, expectedVersion));
    }

    private static int orDefault(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
}

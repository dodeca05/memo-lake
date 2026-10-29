package com.memolake.interfaces.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.memolake.support.IntegrationTest;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Connects a real MCP client (official Java SDK, Streamable HTTP) to {@code /mcp} on a running server and
 * checks the tools against the REST API served by the same process.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    McpSyncClient client;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void connect() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void serverInfoAndInstructions() {
        var init = client.getServerInfo();
        assertThat(init.name()).isEqualTo("memo-lake");
        assertThat(client.getServerCapabilities().tools()).isNotNull();
    }

    @Test
    void listsSevenToolsWithReadOnlyAndWriteHints() {
        Map<String, Tool> tools = client.listTools().tools().stream()
                .collect(Collectors.toMap(Tool::name, Function.identity()));

        assertThat(tools).containsOnlyKeys("list_topics", "create_topic", "get_topic", "get_memo", "create_memo",
                "replace_memo", "append_memo");
        for (String readOnly : List.of("list_topics", "get_topic", "get_memo")) {
            assertThat(tools.get(readOnly).annotations().readOnlyHint()).as(readOnly).isTrue();
        }
        for (String write : List.of("create_topic", "create_memo", "replace_memo", "append_memo")) {
            assertThat(tools.get(write).annotations().readOnlyHint()).as(write).isFalse();
        }
        assertThat(tools.get("replace_memo").annotations().destructiveHint()).isTrue();
        assertThat(tools.get("append_memo").annotations().idempotentHint()).isFalse();
        assertThat(tools.values()).allSatisfy(tool -> assertThat(tool.description()).isNotBlank());
        assertThat(tools.get("append_memo").inputSchema().get("required"))
                .isEqualTo(List.of("topicName", "memoName", "content"));
        assertThat(tools.get("replace_memo").inputSchema().get("properties"))
                .isInstanceOfSatisfying(Map.class, properties -> assertThat(properties).containsKey("expectedVersion"));
    }

    @Test
    void allToolsWorkEndToEnd() {
        JsonNode topic = ok(call("create_topic", Map.of("name", "backend-project", "description", "백엔드 개발 협업")));
        assertThat(topic.get("name").asString()).isEqualTo("backend-project");

        JsonNode created = ok(call("create_memo",
                Map.of("topicName", "backend-project", "memoName", "architecture", "content", "Hello")));
        assertThat(created.get("version").asLong()).isEqualTo(1);

        ok(call("append_memo", Map.of("topicName", "backend-project", "memoName", "architecture",
                "content", "World")));
        JsonNode memo = ok(call("get_memo", Map.of("topicName", "backend-project", "memoName", "architecture")));
        assertThat(memo.get("content").asString()).isEqualTo("HelloWorld");
        assertThat(memo.get("version").asLong()).isEqualTo(2);
        assertThat(memo.get("createdAt").asString()).isNotBlank();
        assertThat(memo.get("updatedAt").asString()).isNotBlank();

        ok(call("replace_memo", Map.of("topicName", "backend-project", "memoName", "architecture",
                "content", "# Architecture\n", "expectedVersion", 2)));
        memo = ok(call("get_memo", Map.of("topicName", "backend-project", "memoName", "architecture")));
        assertThat(memo.get("content").asString()).isEqualTo("# Architecture\n");
        assertThat(memo.get("version").asLong()).isEqualTo(3);

        JsonNode detail = ok(call("get_topic", Map.of("topicName", "backend-project")));
        assertThat(detail.get("description").asString()).isEqualTo("백엔드 개발 협업");
        assertThat(detail.at("/memos/content/0/name").asString()).isEqualTo("architecture");
        assertThat(detail.at("/memos/content/0/content").isMissingNode()).isTrue();
        assertThat(detail.at("/memos/totalElements").asLong()).isEqualTo(1);

        JsonNode topics = ok(call("list_topics", Map.of("page", 0, "size", 5)));
        assertThat(topics.at("/content/0/name").asString()).isEqualTo("backend-project");
        assertThat(topics.at("/content/0/lastMemoModifiedAt").asString()).isNotBlank();
        assertThat(topics.get("size").asInt()).isEqualTo(5);
        assertThat(topics.get("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    void optionalArgumentsUseDefaults() {
        ok(call("create_topic", Map.of("name", "no-description")));
        JsonNode topics = ok(call("list_topics", Map.of()));
        assertThat(topics.get("page").asInt()).isZero();
        assertThat(topics.get("size").asInt()).isEqualTo(10);
        assertThat(topics.at("/content/0/description").asString()).isEmpty();
    }

    @Test
    void mcpAndRestShareTheSameData() throws Exception {
        // Written through MCP, read through REST.
        ok(call("create_topic", Map.of("name", "shared")));
        ok(call("create_memo", Map.of("topicName", "shared", "memoName", "from-mcp", "content", "{\"a\": 1}")));
        HttpResponse<String> rest = http.send(HttpRequest.newBuilder(uri("/api/topic/shared/from-mcp")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rest.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(rest.body()).get("content").asString()).isEqualTo("{\"a\": 1}");

        // Written through REST, read through MCP.
        HttpResponse<String> created = http.send(HttpRequest.newBuilder(uri("/api/topic/shared/from-rest"))
                        .header("Content-Type", "text/plain; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString("REST 본문\n", StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode memo = ok(call("get_memo", Map.of("topicName", "shared", "memoName", "from-rest")));
        assertThat(memo.get("content").asString()).isEqualTo("REST 본문\n");
    }

    @Test
    void errorsAreStructuredToolErrors() {
        CallToolResult missingTopic = call("get_topic", Map.of("topicName", "nothing"));
        assertError(missingTopic, "TOPIC_NOT_FOUND");

        ok(call("create_topic", Map.of("name", "t")));
        assertError(call("create_topic", Map.of("name", "t")), "TOPIC_ALREADY_EXISTS");
        assertError(call("get_memo", Map.of("topicName", "t", "memoName", "nothing")), "MEMO_NOT_FOUND");
        assertError(call("append_memo", Map.of("topicName", "t", "memoName", "nothing", "content", "x")),
                "MEMO_NOT_FOUND");
        assertError(call("create_memo", Map.of("topicName", "t", "memoName", "../etc", "content", "x")),
                "INVALID_REQUEST");
        assertError(call("list_topics", Map.of("size", 1000)), "INVALID_REQUEST");

        ok(call("create_memo", Map.of("topicName", "t", "memoName", "m", "content", "v1")));
        assertError(call("create_memo", Map.of("topicName", "t", "memoName", "m", "content", "v1")),
                "MEMO_ALREADY_EXISTS");
        assertError(call("replace_memo", Map.of("topicName", "t", "memoName", "m", "content", "v2",
                "expectedVersion", 7)), "VERSION_CONFLICT");
        assertError(call("append_memo", Map.of("topicName", "t", "memoName", "m", "content", "x".repeat(1024 * 1024))),
                "CONTENT_TOO_LARGE");
        JsonNode memo = ok(call("get_memo", Map.of("topicName", "t", "memoName", "m")));
        assertThat(memo.get("content").asString()).isEqualTo("v1");
    }

    @Test
    void concurrentAppendsThroughMcpAndRestLoseNothing() throws Exception {
        ok(call("create_topic", Map.of("name", "parallel")));
        ok(call("create_memo", Map.of("topicName", "parallel", "memoName", "log", "content", "")));

        int perInterface = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < perInterface; i++) {
                String mcpLine = "mcp-" + i + "\n";
                String restLine = "rest-" + i + "\n";
                futures.add(executor.submit(() -> ok(call("append_memo",
                        Map.of("topicName", "parallel", "memoName", "log", "content", mcpLine)))));
                futures.add(executor.submit(() -> {
                    var response = http.send(HttpRequest.newBuilder(uri("/api/topic/parallel/log"))
                                    .header("Content-Type", "text/plain; charset=UTF-8")
                                    .PUT(HttpRequest.BodyPublishers.ofString(restLine)).build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        JsonNode memo = ok(call("get_memo", Map.of("topicName", "parallel", "memoName", "log")));
        List<String> lines = List.of(memo.get("content").asString().split("\n"));
        assertThat(lines).hasSize(2 * perInterface).doesNotHaveDuplicates();
        assertThat(memo.get("version").asLong()).isEqualTo(1 + 2 * perInterface);
    }

    private CallToolResult call(String tool, Map<String, Object> arguments) {
        return client.callTool(new CallToolRequest(tool, new HashMap<>(arguments)));
    }

    private JsonNode ok(CallToolResult result) {
        assertThat(result.isError()).as(text(result)).isNotEqualTo(Boolean.TRUE);
        return jsonMapper.readTree(text(result));
    }

    private void assertError(CallToolResult result, String code) {
        assertThat(result.isError()).isTrue();
        JsonNode error = jsonMapper.readTree(text(result));
        assertThat(error.get("code").asString()).isEqualTo(code);
        assertThat(error.get("message").asString()).isNotBlank();
        assertThat(error.get("timestamp").asString()).isNotBlank();
        assertThat(result.structuredContent()).isInstanceOfSatisfying(Map.class,
                structured -> assertThat(structured.get("code")).isEqualTo(code));
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}

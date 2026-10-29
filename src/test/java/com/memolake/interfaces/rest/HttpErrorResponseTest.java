package com.memolake.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.memolake.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Errors produced before a controller runs (routing, method, content type) still use the common
 * error format over real HTTP; browsers get the HTML error page instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpErrorResponseTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void unsupportedContentType() throws Exception {
        var response = send(request("/api/topic/t/m").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unsupportedMethod() throws Exception {
        var response = send(request("/api/topic/t/m").DELETE());

        assertError(response, 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unknownApiPath() throws Exception {
        assertError(send(request("/api/nothing/here/at/all").GET()), 404, "NOT_FOUND");
    }

    @Test
    void browsersGetAnHtmlErrorPage() throws Exception {
        var response = send(request("/web/nothing").header("Accept", "text/html").GET());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/html"));
        assertThat(response.body()).contains("페이지를 찾을 수 없습니다.");
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        JsonNode body = jsonMapper.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo(code);
        assertThat(body.get("message").asString()).isNotBlank();
        assertThat(body.get("timestamp").asString()).isNotBlank();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

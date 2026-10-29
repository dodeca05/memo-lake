package com.memolake.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memolake.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Optional shared access token and the Host allow-list.
 */
@SpringBootTest(properties = "memolake.security.access-token=s3cret")
@AutoConfigureMockMvc
class AccessTokenTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/topic"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"memolake\""));
        mvc.perform(get("/api/topic").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenIsAccepted() throws Exception {
        mvc.perform(get("/api/topic").header("Authorization", "Bearer s3cret")).andExpect(status().isOk());
    }

    @Test
    void browsersCanUseBasicAuthWithTheTokenAsPassword() throws Exception {
        mvc.perform(get("/web/main"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().stringValues("WWW-Authenticate",
                        "Bearer realm=\"memolake\"", "Basic realm=\"memolake\", charset=\"UTF-8\""));
        String basic = Base64.getEncoder().encodeToString("anyone:s3cret".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/web/main").header("Authorization", "Basic " + basic)).andExpect(status().isOk());
    }

    @Test
    void unknownHostIsRejected() throws Exception {
        mvc.perform(get("/api/topic").header("Authorization", "Bearer s3cret").header("Host", "evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}

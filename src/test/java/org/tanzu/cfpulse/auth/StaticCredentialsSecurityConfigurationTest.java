package org.tanzu.cfpulse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the MCP endpoint is accessible without authentication when
 * static CF credentials are configured and no OAuth2 issuer-uri is set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cf.apiHost=api.test.example.com",
        "cf.username=test-user",
        "cf.password=test-password",
        "cf.organization=test-org",
        "cf.space=test-space"
})
class StaticCredentialsSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsAllowedWithStaticCredentials() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(APPLICATION_JSON)
                        .accept(TEXT_EVENT_STREAM, APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1,\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}"))
                .andExpect(status().isOk());
    }
}

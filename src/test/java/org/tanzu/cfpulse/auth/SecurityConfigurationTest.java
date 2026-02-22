package org.tanzu.cfpulse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that the production {@link SecurityConfiguration} correctly enforces JWT
 * authentication on the MCP endpoint.
 * <p>
 * Setting {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} activates
 * the production {@link SecurityConfiguration} and keeps
 * {@link StaticCredentialsSecurityConfiguration} inactive. A mock
 * {@link JwtDecoder} replaces the auto-configured one so that no live
 * authorization server is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfigurationTest.TestSecurityConfig.class)
@TestPropertySource(properties = {
        "cf.apiHost=api.test.example.com",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://uaa.test.example.com/oauth/token",
        "sso.auth-domain=https://login.test.example.com"
})
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithJwtIsAccepted() throws Exception {
        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(builder -> builder
                                .subject("test-user")
                                .claim("scope", "openid")))
                        .contentType(APPLICATION_JSON)
                        .accept(TEXT_EVENT_STREAM, APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1,\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}

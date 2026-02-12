package org.tanzu.cfpulse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that the security filter chain correctly enforces JWT authentication.
 * <p>
 * The production {@link SecurityConfiguration} is disabled by not setting the
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} property (it's
 * conditional on that property). This test provides its own security filter chain
 * with a mock {@link JwtDecoder} to verify the security behavior without needing
 * a live authorization server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfigurationTest.TestSecurityConfig.class)
@TestPropertySource(properties = {
        "cf.apiHost=api.test.example.com"
        // issuer-uri intentionally NOT set, disabling the production SecurityConfiguration
})
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType("application/json")
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

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                    .csrf(CsrfConfigurer::disable)
                    .build();
        }
    }
}

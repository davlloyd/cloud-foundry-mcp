package org.tanzu.cfpulse.auth;

import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures the MCP server as an OAuth2 Resource Server.
 * <p>
 * Uses {@link McpServerOAuth2Configurer} from the mcp-server-security community
 * module, which handles:
 * <ul>
 *   <li>JWT token validation via the configured issuer-uri</li>
 *   <li>OAuth 2.0 Protected Resource Metadata endpoint (RFC 9728)</li>
 *   <li>WWW-Authenticate header with resource_metadata on 401 responses</li>
 * </ul>
 * <p>
 * The issuer-uri is auto-configured by {@code java-cfenv-boot-pivotal-sso} when
 * running on Cloud Foundry with a bound SSO tile instance. For local development,
 * set the {@code SSO_ISSUER_URI} environment variable.
 * <p>
 * This configuration is only activated when
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set to a
 * non-empty value.
 */
@Configuration
@ConditionalOnExpression("!''.equals('${spring.security.oauth2.resourceserver.jwt.issuer-uri:}')")
public class SecurityConfiguration {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(CsrfConfigurer::disable)
                .with(
                        McpServerOAuth2Configurer.mcpServerOAuth2(),
                        mcpOAuth2 -> mcpOAuth2.authorizationServer(issuerUri)
                )
                .build();
    }
}

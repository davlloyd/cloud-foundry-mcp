package org.tanzu.cfpulse.auth;

import org.springaicommunity.mcp.security.server.oauth2.authentication.BearerResourceMetadataTokenAuthenticationEntryPoint;
import org.springaicommunity.mcp.security.server.oauth2.metadata.OAuth2ProtectedResourceMetadataEndpointFilter;
import org.springaicommunity.mcp.security.server.oauth2.metadata.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures the MCP server as an OAuth2 Resource Server with RFC 9728
 * Protected Resource Metadata.
 * <p>
 * In Cloud Foundry with Tanzu SSO, the login server ({@code login.sys.*}) and
 * UAA ({@code uaa.sys.*}) share the same JWT signing keys but have different
 * hostnames. The {@code java-cfenv-boot-pivotal-sso} library sets the JWT
 * issuer-uri to the UAA endpoint ({@code uaa.sys.*}), while SSO client
 * credentials are registered with the login server ({@code login.sys.*}).
 * <p>
 * This configuration addresses the split by:
 * <ul>
 *   <li>Using the SSO auth domain ({@code login.sys.*}) as the authorization
 *       server in the OAuth 2.0 Protected Resource Metadata, so MCP clients
 *       discover the correct authorize/token endpoints for the SSO client</li>
 *   <li>Using the standard Spring Security JWT resource server with the
 *       issuer-uri for token validation, matching the {@code iss} claim</li>
 * </ul>
 * <p>
 * This configuration is only activated when
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set to a
 * non-empty value.
 */
@Configuration
@ConditionalOnExpression("!''.equals('${spring.security.oauth2.resourceserver.jwt.issuer-uri:}')")
public class SecurityConfiguration {

    private static final String MCP_ENDPOINT = "/mcp";

    @Value("${sso.auth-domain}")
    private String authDomain;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var resourceIdentifier = new ResourceIdentifier(MCP_ENDPOINT);
        var entryPoint = new BearerResourceMetadataTokenAuthenticationEntryPoint(resourceIdentifier);
        var metadataFilter = new OAuth2ProtectedResourceMetadataEndpointFilter(resourceIdentifier);
        metadataFilter.setProtectedResourceMetadataCustomizer(metadata -> metadata
                .authorizationServer(authDomain));

        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(CsrfConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .addFilterBefore(metadataFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}

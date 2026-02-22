package org.tanzu.cfpulse.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits all requests when the server is running with static CF credentials
 * (CF_USERNAME / CF_PASSWORD) and no OAuth2 issuer is configured.
 * <p>
 * This is the counterpart to {@link SecurityConfiguration}, which activates
 * only when an OAuth2 issuer-uri is present. Exactly one of these two
 * configurations will be active at any time.
 */
@Configuration
@ConditionalOnExpression("''.equals('${spring.security.oauth2.resourceserver.jwt.issuer-uri:}')")
public class StaticCredentialsSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}

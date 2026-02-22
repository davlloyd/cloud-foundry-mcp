package org.tanzu.cfpulse.cf;

import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.networking.ReactorNetworkingClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CfConfiguration {

    /**
     * ConnectionContext is always available -- it only needs the CF API host,
     * which is required regardless of the authentication mode.
     */
    @Bean
    DefaultConnectionContext connectionContext(@Value("${cf.apiHost}") String apiHost) {
        return DefaultConnectionContext.builder()
                .apiHost(apiHost)
                .build();
    }

    /**
     * Static credential beans below are only created when {@code cf.username} is configured.
     * In OAuth2 mode (SSO tile), these beans are absent and all CF API calls use the
     * authenticated user's relayed token instead.
     */
    @Bean
    @ConditionalOnProperty("cf.username")
    PasswordGrantTokenProvider tokenProvider(@Value("${cf.username}") String username,
                                             @Value("${cf.password}") String password) {
        return PasswordGrantTokenProvider.builder()
                .password(password)
                .username(username)
                .build();
    }

    @Bean
    @ConditionalOnProperty("cf.username")
    ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
        return ReactorCloudFoundryClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty("cf.username")
    ReactorDopplerClient dopplerClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
        return ReactorDopplerClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty("cf.username")
    ReactorUaaClient uaaClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
        return ReactorUaaClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty("cf.username")
    ReactorNetworkingClient networkingClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
        return ReactorNetworkingClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }
}

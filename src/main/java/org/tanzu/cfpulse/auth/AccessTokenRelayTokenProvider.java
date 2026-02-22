package org.tanzu.cfpulse.auth;

import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import reactor.core.publisher.Mono;

/**
 * A {@link TokenProvider} that relays a pre-existing OAuth2 access token to the
 * Cloud Foundry Java client. This enables CF API calls to execute under the
 * authenticated user's own permission set rather than a static service account.
 */
public record AccessTokenRelayTokenProvider(String accessToken) implements TokenProvider {

    @Override
    public Mono<String> getToken(ConnectionContext connectionContext) {
        return Mono.just("bearer " + accessToken);
    }
}

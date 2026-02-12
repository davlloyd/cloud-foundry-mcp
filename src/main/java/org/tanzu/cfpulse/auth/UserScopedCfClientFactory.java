package org.tanzu.cfpulse.auth;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.networking.NetworkingClient;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.networking.ReactorNetworkingClient;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.cloudfoundry.uaa.UaaClient;
import org.springframework.stereotype.Component;

/**
 * Factory that creates a complete set of Cloud Foundry Java clients scoped to a
 * specific user's OAuth2 access token. Each call produces fresh client instances
 * authenticated with the provided token, so CF API operations execute under that
 * user's permission set.
 */
@Component
public class UserScopedCfClientFactory {

    private final ConnectionContext connectionContext;

    public UserScopedCfClientFactory(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
    }

    /**
     * Creates a {@link CloudFoundryOperations} instance that executes CF API
     * calls under the given user's access token, targeting the specified
     * organization and space.
     */
    public CloudFoundryOperations createOperations(String accessToken, String organization, String space) {
        var tokenProvider = new AccessTokenRelayTokenProvider(accessToken);
        return DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(createCloudFoundryClient(tokenProvider))
                .dopplerClient(createDopplerClient(tokenProvider))
                .uaaClient(createUaaClient(tokenProvider))
                .networkingClient(createNetworkingClient(tokenProvider))
                .organization(organization)
                .space(space)
                .build();
    }

    private CloudFoundryClient createCloudFoundryClient(TokenProvider tokenProvider) {
        return ReactorCloudFoundryClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }

    private DopplerClient createDopplerClient(TokenProvider tokenProvider) {
        return ReactorDopplerClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }

    private UaaClient createUaaClient(TokenProvider tokenProvider) {
        return ReactorUaaClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }

    private NetworkingClient createNetworkingClient(TokenProvider tokenProvider) {
        return ReactorNetworkingClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
    }
}

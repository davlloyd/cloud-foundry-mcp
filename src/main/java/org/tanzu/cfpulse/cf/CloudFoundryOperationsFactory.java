package org.tanzu.cfpulse.cf;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.networking.NetworkingClient;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.uaa.UaaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.tanzu.cfpulse.auth.UserScopedCfClientFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CloudFoundryOperationsFactory {

    private static final Logger logger = LoggerFactory.getLogger(CloudFoundryOperationsFactory.class);

    @Nullable
    private final CloudFoundryClient cloudFoundryClient;
    @Nullable
    private final DopplerClient dopplerClient;
    @Nullable
    private final UaaClient uaaClient;
    @Nullable
    private final NetworkingClient networkingClient;
    private final UserScopedCfClientFactory userScopedCfClientFactory;
    private final String defaultOrganization;
    private final String defaultSpace;
    
    private final ConcurrentHashMap<String, CloudFoundryOperations> operationsCache;
    private volatile CloudFoundryOperations defaultOperations;

    public CloudFoundryOperationsFactory(@Nullable CloudFoundryClient cloudFoundryClient,
                                       @Nullable DopplerClient dopplerClient,
                                       @Nullable UaaClient uaaClient,
                                       @Nullable NetworkingClient networkingClient,
                                       UserScopedCfClientFactory userScopedCfClientFactory,
                                       @Value("${cf.organization:}") String defaultOrganization,
                                       @Value("${cf.space:}") String defaultSpace) {
        this.cloudFoundryClient = cloudFoundryClient;
        this.dopplerClient = dopplerClient;
        this.uaaClient = uaaClient;
        this.networkingClient = networkingClient;
        this.userScopedCfClientFactory = userScopedCfClientFactory;
        this.defaultOrganization = defaultOrganization;
        this.defaultSpace = defaultSpace;
        this.operationsCache = new ConcurrentHashMap<>();
    }

    /**
     * Returns CloudFoundryOperations using the static service-account credentials.
     * Only available when {@code cf.username} and {@code cf.password} are configured.
     *
     * @throws IllegalStateException if static credentials are not configured
     */
    public CloudFoundryOperations getDefaultOperations() {
        requireStaticCredentials();
        if (defaultOperations == null) {
            synchronized (this) {
                if (defaultOperations == null) {
                    logger.debug("Creating default CloudFoundryOperations for org={}, space={}", 
                               defaultOrganization, defaultSpace);
                    defaultOperations = createStaticOperations(defaultOrganization, defaultSpace);
                }
            }
        }
        return defaultOperations;
    }

    /**
     * Returns CloudFoundryOperations using the static service-account credentials,
     * targeting the specified organization and space (falling back to defaults for null values).
     * Only available when {@code cf.username} and {@code cf.password} are configured.
     *
     * @throws IllegalStateException if static credentials are not configured
     */
    public CloudFoundryOperations getOperations(String organization, String space) {
        requireStaticCredentials();
        String resolvedOrg = organization != null ? organization : defaultOrganization;
        String resolvedSpace = space != null ? space : defaultSpace;
        
        if (resolvedOrg.equals(defaultOrganization) && resolvedSpace.equals(defaultSpace)) {
            return getDefaultOperations();
        }
        
        String cacheKey = createCacheKey(resolvedOrg, resolvedSpace);
        return operationsCache.computeIfAbsent(cacheKey, key -> {
            logger.debug("Creating new CloudFoundryOperations for org={}, space={}", 
                       resolvedOrg, resolvedSpace);
            return createStaticOperations(resolvedOrg, resolvedSpace);
        });
    }

    /**
     * Returns CloudFoundryOperations scoped to the given user's access token,
     * targeting the specified organization and space (falling back to defaults for null values).
     * CF API calls made through the returned operations will execute under the user's permissions.
     */
    public CloudFoundryOperations getUserScopedOperations(String accessToken, String organization, String space) {
        String resolvedOrg = organization != null ? organization : defaultOrganization;
        String resolvedSpace = space != null ? space : defaultSpace;
        logger.debug("Creating user-scoped CloudFoundryOperations for org={}, space={}", resolvedOrg, resolvedSpace);
        return userScopedCfClientFactory.createOperations(accessToken, resolvedOrg, resolvedSpace);
    }

    public void clearCache() {
        logger.info("Clearing CloudFoundryOperations cache containing {} entries", operationsCache.size());
        operationsCache.clear();
        synchronized (this) {
            defaultOperations = null;
        }
    }

    public Set<String> getCachedContexts() {
        return Set.copyOf(operationsCache.keySet());
    }

    public int getCacheSize() {
        return operationsCache.size();
    }

    public String getDefaultSpace() {
        return defaultSpace;
    }

    public String getDefaultOrganization() {
        return defaultOrganization;
    }

    private boolean hasStaticCredentials() {
        return cloudFoundryClient != null;
    }

    private void requireStaticCredentials() {
        if (!hasStaticCredentials()) {
            throw new IllegalStateException(
                    "Static CF credentials are not configured. " +
                    "Set cf.username and cf.password, or use OAuth2 authentication.");
        }
    }

    private CloudFoundryOperations createStaticOperations(String organization, String space) {
        return DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(cloudFoundryClient)
                .dopplerClient(dopplerClient)
                .uaaClient(uaaClient)
                .networkingClient(networkingClient)
                .organization(organization)
                .space(space)
                .build();
    }

    private String createCacheKey(String organization, String space) {
        return organization + ":" + space;
    }
}

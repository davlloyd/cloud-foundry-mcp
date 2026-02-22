package org.tanzu.cfpulse.cf;

import org.cloudfoundry.operations.CloudFoundryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public abstract class CfBaseService {

    private static final Logger logger = LoggerFactory.getLogger(CfBaseService.class);

    protected final CloudFoundryOperationsFactory operationsFactory;

    protected static final String ORG_PARAM = "Name of the Cloud Foundry organization. Optional - can be null or omitted to use the configured default organization.";
    protected static final String SPACE_PARAM = "Name of the Cloud Foundry space. Optional - can be null or omitted to use the configured default space.";
    protected static final String NAME_PARAM = "Name of the Cloud Foundry application";

    public CfBaseService(CloudFoundryOperationsFactory operationsFactory) {
        this.operationsFactory = operationsFactory;
    }

    protected CloudFoundryOperations getOperations(String organization, String space) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String accessToken = jwtAuth.getToken().getTokenValue();
            logger.debug("Creating user-scoped operations for authenticated user: {}", jwtAuth.getName());
            return operationsFactory.getUserScopedOperations(accessToken, organization, space);
        }
        // Fallback to static service-account credentials (e.g., local dev, STDIO transport)
        if (organization == null && space == null) {
            return operationsFactory.getDefaultOperations();
        }
        return operationsFactory.getOperations(organization, space);
    }
}

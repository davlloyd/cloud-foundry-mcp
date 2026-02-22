package org.tanzu.cfpulse.cf;

import org.cloudfoundry.operations.CloudFoundryOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CfBaseServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOperationsUsesUserTokenWhenJwtAuthenticated() {
        var factory = mock(CloudFoundryOperationsFactory.class);
        var userOps = mock(CloudFoundryOperations.class);
        var tokenValue = "user-jwt-token-value";

        when(factory.getUserScopedOperations(tokenValue, "org1", "space1")).thenReturn(userOps);

        var jwt = buildJwt(tokenValue);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        var service = new TestCfService(factory);
        var result = service.getOperations("org1", "space1");

        assertSame(userOps, result);
        verify(factory).getUserScopedOperations(tokenValue, "org1", "space1");
        verify(factory, never()).getDefaultOperations();
        verify(factory, never()).getOperations(anyString(), anyString());
    }

    @Test
    void getOperationsUsesUserTokenWithNullOrgAndSpace() {
        var factory = mock(CloudFoundryOperationsFactory.class);
        var userOps = mock(CloudFoundryOperations.class);
        var tokenValue = "user-jwt-token-value";

        when(factory.getUserScopedOperations(tokenValue, null, null)).thenReturn(userOps);

        var jwt = buildJwt(tokenValue);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        var service = new TestCfService(factory);
        var result = service.getOperations(null, null);

        assertSame(userOps, result);
        verify(factory).getUserScopedOperations(tokenValue, null, null);
    }

    @Test
    void getOperationsFallsBackToDefaultWhenNoAuthentication() {
        var factory = mock(CloudFoundryOperationsFactory.class);
        var defaultOps = mock(CloudFoundryOperations.class);

        when(factory.getDefaultOperations()).thenReturn(defaultOps);

        var service = new TestCfService(factory);
        var result = service.getOperations(null, null);

        assertSame(defaultOps, result);
        verify(factory).getDefaultOperations();
    }

    @Test
    void getOperationsFallsBackToStaticWithOrgAndSpaceWhenNoAuthentication() {
        var factory = mock(CloudFoundryOperationsFactory.class);
        var staticOps = mock(CloudFoundryOperations.class);

        when(factory.getOperations("org1", "space1")).thenReturn(staticOps);

        var service = new TestCfService(factory);
        var result = service.getOperations("org1", "space1");

        assertSame(staticOps, result);
        verify(factory).getOperations("org1", "space1");
    }

    private Jwt buildJwt(String tokenValue) {
        return new Jwt(
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "test-user", "iss", "https://uaa.example.com/oauth/token")
        );
    }

    /**
     * Concrete subclass of CfBaseService for testing the protected getOperations method.
     */
    static class TestCfService extends CfBaseService {
        TestCfService(CloudFoundryOperationsFactory operationsFactory) {
            super(operationsFactory);
        }

        @Override
        public CloudFoundryOperations getOperations(String organization, String space) {
            return super.getOperations(organization, space);
        }
    }
}

package org.tanzu.cfpulse.auth;

import org.cloudfoundry.reactor.ConnectionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AccessTokenRelayTokenProviderTest {

    @Test
    void getTokenReturnsBearerPrefixedAccessToken() {
        var token = "eyJhbGciOiJSUzI1NiJ9.test-token-value";
        var provider = new AccessTokenRelayTokenProvider(token);
        var connectionContext = mock(ConnectionContext.class);

        var result = provider.getToken(connectionContext).block();

        assertEquals("bearer " + token, result);
    }

    @Test
    void getTokenReturnsSameValueOnMultipleCalls() {
        var token = "my-access-token";
        var provider = new AccessTokenRelayTokenProvider(token);
        var connectionContext = mock(ConnectionContext.class);

        assertEquals("bearer " + token, provider.getToken(connectionContext).block());
        assertEquals("bearer " + token, provider.getToken(connectionContext).block());
    }

    @Test
    void invalidateDoesNotThrow() {
        var provider = new AccessTokenRelayTokenProvider("token");
        var connectionContext = mock(ConnectionContext.class);

        // invalidate has a default no-op implementation; verify it doesn't throw
        assertDoesNotThrow(() -> provider.invalidate(connectionContext));
    }

    @Test
    void accessTokenAccessorReturnsValue() {
        var token = "test-token";
        var provider = new AccessTokenRelayTokenProvider(token);

        assertEquals(token, provider.accessToken());
    }
}

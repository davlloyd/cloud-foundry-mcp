# OAuth Authorization Flow: goose-agent-chat to Cloud Foundry MCP Server

This document describes the end-to-end OAuth 2.0 authorization flow when a user
connects to the Cloud Foundry MCP server (`cf-auth-mcp`) from
`goose-agent-chat`, and the subsequent request flow when authenticated chat
messages invoke MCP tools.

## Participants

| Participant | Role | SSO Binding |
|---|---|---|
| **goose-agent-chat** | OAuth Client — initiates the authorization code flow on behalf of the user | Bound to `p-identity` (SSO) instance. Receives its own `client_id` and `client_secret`. Uses these credentials to request authorization codes and exchange them for tokens. |
| **cf-auth-mcp** | OAuth Resource Server — protects the `/mcp` endpoint and validates bearer tokens | Bound to the same `p-identity` (SSO) instance. `java-cfenv-boot-pivotal-sso` auto-configures the JWT `issuer-uri` (pointing to `uaa.sys.*`) for token validation, and sets `ssoServiceUrl` (pointing to `login.sys.*`) for the RFC 9728 metadata. |
| **Tanzu SSO / UAA** | Authorization Server — authenticates users and issues tokens | The SSO tile runs two cooperating servers: the **login server** (`login.sys.*`) handles user-facing authorize/token endpoints, and **UAA** (`uaa.sys.*`) manages token signing keys and the `iss` claim. Both share the same JWT signing keys. |
| **Cloud Foundry API** | The CF API at `api.sys.*` — enforces role-based access control on every request | Not directly involved in the OAuth flow, but receives the user's access token relayed by cf-auth-mcp for every CF operation. |
| **User's Browser** | Runs the Angular frontend, opens the OAuth popup, and sends chat messages | — |

---

## Part 1: OAuth Authentication Flow

This flow occurs when the user clicks the "OAuth Connect" button for the
Cloud Foundry MCP server in the goose-agent-chat config panel.

### Step 1 — User Initiates OAuth

```
Browser                          goose-agent-chat (backend)
  │                                       │
  │  GET /api/mcp/cloud-foundry/auth/     │
  │      initiate?sessionId=chat-abc123   │
  │ ─────────────────────────────────────>│
```

The Angular frontend (`McpOAuthService.initiateAuth()`) calls the backend to
start the OAuth flow for the named MCP server.

**What happens in goose-agent-chat:**

`McpOAuthController.initiateAuth()` performs the following:

1. Looks up the MCP server configuration from `.goose-config.yml` and finds
   `cloud-foundry` with `requiresAuth: true`.
2. Reads the pre-registered `clientId` and `clientSecret` from the
   configuration. These are the SSO credentials from goose-agent-chat's own
   binding to the `p-identity` service instance.
3. Creates an `McpOAuthManagerImpl` for this server (or retrieves the cached
   one).

### Step 2 — OAuth Discovery (RFC 9728)

```
goose-agent-chat                cf-auth-mcp
  │                                  │
  │  GET /mcp                        │
  │  (no Authorization header)       │
  │ ────────────────────────────────>│
  │                                  │
  │  401 Unauthorized                │
  │  WWW-Authenticate: Bearer        │
  │    resource_metadata="/.well-    │
  │    known/oauth-protected-        │
  │    resource/mcp"                 │
  │ <────────────────────────────────│
  │                                  │
  │  GET /.well-known/oauth-         │
  │      protected-resource/mcp      │
  │ ────────────────────────────────>│
  │                                  │
  │  200 OK                          │
  │  {                               │
  │    "resource": "/mcp",           │
  │    "authorization_servers":      │
  │      ["https://login.sys..."]    │
  │  }                               │
  │ <────────────────────────────────│
```

`McpOAuthManagerImpl.discoverOAuthConfig()` discovers the authorization server
by following the MCP Authorization specification:

1. Makes an unauthenticated request to the MCP server's `/mcp` endpoint.
2. cf-auth-mcp's `SecurityConfiguration` rejects the request with a `401`. The
   `BearerResourceMetadataTokenAuthenticationEntryPoint` includes a
   `WWW-Authenticate` header with a `resource_metadata` link pointing to the
   RFC 9728 Protected Resource Metadata endpoint.
3. goose-agent-chat fetches the metadata document from
   `/.well-known/oauth-protected-resource/mcp`. This document is served by
   `OAuth2ProtectedResourceMetadataEndpointFilter` and includes the
   `authorization_servers` field set to the SSO auth domain
   (`https://login.sys.*`).

**How cf-auth-mcp uses its SSO binding here:**

The `java-cfenv-boot-pivotal-sso` library reads the SSO binding's
`auth_domain` from `VCAP_SERVICES` and sets `ssoServiceUrl`. The
`SecurityConfiguration` injects this value via `@Value("${sso.auth-domain}")`
and passes it to the metadata filter:

```java
metadataFilter.setProtectedResourceMetadataCustomizer(metadata -> metadata
        .authorizationServer(authDomain));
```

This is what tells goose-agent-chat *where* to send the user for login.

### Step 3 — Authorization Server Metadata Discovery (RFC 8414)

```
goose-agent-chat                Tanzu SSO (login.sys.*)
  │                                  │
  │  GET /.well-known/oauth-         │
  │      authorization-server        │
  │ ────────────────────────────────>│
  │                                  │
  │  200 OK                          │
  │  {                               │
  │    "authorization_endpoint":     │
  │      "https://login.sys.../      │
  │       oauth/authorize",          │
  │    "token_endpoint":             │
  │      "https://login.sys.../      │
  │       oauth/token",              │
  │    ...                           │
  │  }                               │
  │ <────────────────────────────────│
```

goose-agent-chat fetches the SSO login server's authorization server metadata
to discover the `authorization_endpoint` and `token_endpoint` URLs.

### Step 4 — Build Authorization URL and Return to Browser

```
goose-agent-chat                Browser
  │                                │
  │  200 OK                        │
  │  {                             │
  │    "authUrl": "https://        │
  │      login.sys.../oauth/       │
  │      authorize?client_id=...   │
  │      &redirect_uri=...         │
  │      &code_challenge=...       │
  │      &state=...",              │
  │    "state": "abc..."           │
  │  }                             │
  │ ──────────────────────────────>│
```

goose-agent-chat generates:

- A **PKCE code verifier and challenge** (OAuth 2.1 requirement)
- A **random state parameter** for CSRF protection
- The **authorization URL** using the discovered `authorization_endpoint`,
  goose-agent-chat's `client_id` (from its SSO binding), the `redirect_uri`
  (`https://goose-agent-chat.apps.*/oauth/callback`), and the requested scopes

This is returned to the Angular frontend as an `InitiateAuthResponse`.

### Step 5 — User Authenticates at SSO Login

```
Browser                          Tanzu SSO (login.sys.*)
  │                                       │
  │  [Opens popup window]                 │
  │  GET https://login.sys.../oauth/      │
  │      authorize?                       │
  │      client_id=c7b38889-...           │
  │      &redirect_uri=https://           │
  │        goose-agent-chat.apps.*/       │
  │        oauth/callback                 │
  │      &response_type=code              │
  │      &code_challenge=...              │
  │      &code_challenge_method=S256      │
  │      &scope=openid+cloud_controller   │
  │        .read+cloud_controller.write   │
  │      &state=abc...                    │
  │ ─────────────────────────────────────>│
  │                                       │
  │  [SSO login page is displayed]        │
  │  [User enters credentials]            │
  │  [User grants consent]                │
  │                                       │
  │  302 Redirect                         │
  │  Location: https://goose-agent-       │
  │    chat.apps.*/oauth/callback?        │
  │    code=AUTH_CODE&state=abc...        │
  │ <─────────────────────────────────────│
```

The Angular frontend opens a popup window pointing to the authorization URL.
The user sees the Tanzu SSO login page (backed by the Internal User Store or
another identity provider configured in the SSO plan). After authentication,
the SSO server validates that:

- The `client_id` matches a registered client (goose-agent-chat's SSO binding)
- The `redirect_uri` is in the client's allowed redirect URI list
- The requested scopes are authorized for this client

The SSO server then redirects the popup back to goose-agent-chat with an
authorization code.

### Step 6 — Authorization Code Exchange

```
Browser                          goose-agent-chat (backend)
  │                                       │
  │  GET /oauth/callback?                 │
  │      code=AUTH_CODE&state=abc...      │
  │ ─────────────────────────────────────>│
  │                                       │

goose-agent-chat                Tanzu SSO (login.sys.*)
  │                                       │
  │  POST https://login.sys.../oauth/     │
  │       token                           │
  │  grant_type=authorization_code        │
  │  &code=AUTH_CODE                      │
  │  &redirect_uri=https://goose-agent-   │
  │    chat.apps.*/oauth/callback         │
  │  &client_id=c7b38889-...             │
  │  &client_secret=e77f5534-...          │
  │  &code_verifier=PKCE_VERIFIER        │
  │ ─────────────────────────────────────>│
  │                                       │
  │  200 OK                               │
  │  {                                    │
  │    "access_token": "eyJhbGci...",     │
  │    "token_type": "bearer",            │
  │    "refresh_token": "...",            │
  │    "expires_in": 43199,               │
  │    "scope": "openid cloud_controller  │
  │      .read cloud_controller.write"    │
  │  }                                    │
  │ <─────────────────────────────────────│
```

`McpOAuthController.handleCallback()` receives the callback and delegates to
`McpOAuthManagerImpl.exchangeCodeForTokens()`:

1. Validates the `state` parameter matches the one generated in Step 4.
2. Sends a POST to the SSO `token_endpoint` with the authorization code,
   the PKCE code verifier, and goose-agent-chat's `client_id` and
   `client_secret`.
3. The SSO server validates everything and returns a JWT access token.

**How goose-agent-chat uses its SSO binding here:**

The `client_id` and `client_secret` sent in the token request are
goose-agent-chat's own credentials from its `p-identity` binding. These prove
to the SSO server that the token request is coming from a legitimate registered
client.

### Step 7 — Token Stored, Popup Closes

```
goose-agent-chat                Browser (popup)
  │                                  │
  │  200 OK (HTML page)              │
  │  "Authentication Successful"     │
  │  <script>                        │
  │    window.opener.postMessage(    │
  │      { type: 'oauth-callback',   │
  │        success: true,            │
  │        server: 'cloud-foundry'   │
  │      }, '*');                     │
  │  </script>                       │
  │ ────────────────────────────────>│
  │                                  │
  │              Browser (popup → main window)
  │                  │
  │  [postMessage notifies the main  │
  │   window of successful auth]     │
  │  [Popup closes]                  │
  │  [Config panel updates to show   │
  │   "Connected" status]            │
```

The access token is stored in-memory in `McpOAuthManagerImpl`, keyed by server
name and session ID. The popup window renders an HTML page that notifies the
parent window via `postMessage`, then closes. The Angular `McpOAuthService`
receives the message and updates the authentication status signal.

---

## Part 2: Authenticated Chat Request Flow

This flow occurs when the user sends a chat message and the LLM invokes a
Cloud Foundry MCP tool (e.g., "list my applications").

### Step 1 — User Sends a Chat Message

```
Browser                          goose-agent-chat (backend)
  │                                       │
  │  GET /api/chat/sessions/chat-abc123/  │
  │      stream?message=list+my+apps      │
  │  Accept: text/event-stream            │
  │ ─────────────────────────────────────>│
```

The Angular frontend sends the message via SSE (Server-Sent Events) to the
`GooseChatController`.

### Step 2 — Token Injection into Goose Configuration

```
GooseChatController              GooseConfigInjector
  │                                       │
  │  injectOAuthTokens("chat-abc123")     │
  │ ─────────────────────────────────────>│
  │                                       │
  │  [Reads ~/.config/goose/config.yaml]  │
  │  [Finds cloud-foundry server with     │
  │   requiresAuth: true]                 │
  │  [Retrieves access token from         │
  │   McpOAuthController for this         │
  │   server + session]                   │
  │  [Writes Authorization: Bearer header │
  │   into config.yaml for the            │
  │   cloud-foundry server entry]         │
  │ <─────────────────────────────────────│
```

Before executing the Goose CLI, `GooseConfigInjector.injectOAuthTokens()`
modifies the Goose `config.yaml` to include the bearer token for the
authenticated MCP server. The config ends up looking like:

```yaml
extensions:
  cloud-foundry:
    type: streamable_http
    uri: https://cf-auth-mcp.apps.*/mcp
    headers:
      Authorization: "Bearer eyJhbGci..."
```

### Step 3 — Goose CLI Executes and Calls the MCP Server

```
goose-agent-chat                Goose CLI
  │                                  │
  │  [Spawns goose process with      │
  │   --name chat-abc123 --resume    │
  │   --output-format stream-json]   │
  │ ────────────────────────────────>│
  │                                  │
  │  [Goose sends the message to     │
  │   the LLM]                       │
  │  [LLM decides to call            │
  │   "applicationsList" tool]       │
  │                                  │

Goose CLI                       cf-auth-mcp
  │                                  │
  │  POST /mcp                       │
  │  Authorization: Bearer eyJhbG... │
  │  Content-Type: application/json  │
  │  {                               │
  │    "method": "tools/call",       │
  │    "params": {                   │
  │      "name": "applicationsList"  │
  │    }                             │
  │  }                               │
  │ ────────────────────────────────>│
```

Goose CLI reads the `Authorization` header from its config and includes it in
every HTTP request to the MCP server.

### Step 4 — cf-auth-mcp Validates the Token

```
cf-auth-mcp (Spring Security filter chain)
  │
  │  [BearerTokenAuthenticationFilter extracts the token]
  │  [JWT decoder fetches signing keys from UAA's JWKS endpoint]
  │  [Validates signature, expiry, issuer, audience]
  │  [Creates authenticated SecurityContext with user identity]
  │
```

**How cf-auth-mcp uses its SSO binding here:**

The `java-cfenv-boot-pivotal-sso` library auto-configured the JWT
`issuer-uri` from the SSO binding (pointing to `uaa.sys.*`). Spring Security
uses this to:

1. Fetch the JWKS (JSON Web Key Set) from `uaa.sys.*/.well-known/jwks.json`
2. Validate the JWT signature against UAA's public keys
3. Verify the `iss` claim matches the configured issuer

This works because the Tanzu SSO login server (`login.sys.*`) and UAA
(`uaa.sys.*`) share the same JWT signing keys. Tokens issued by the login
server's token endpoint are signed with UAA's keys, so cf-auth-mcp can
validate them using the UAA issuer-uri.

### Step 5 — Token Relay to Cloud Foundry API

```
cf-auth-mcp                     Cloud Foundry API (api.sys.*)
  │                                       │
  │  [UserScopedCfClientFactory creates   │
  │   CF clients using the user's token]  │
  │                                       │
  │  [AccessTokenRelayTokenProvider wraps  │
  │   the bearer token for the CF Java    │
  │   client library]                     │
  │                                       │
  │  GET /v2/apps                         │
  │  Authorization: bearer eyJhbGci...    │
  │ ─────────────────────────────────────>│
  │                                       │
  │  [CF API checks the token's scopes    │
  │   and the user's org/space roles]     │
  │                                       │
  │  200 OK                               │
  │  { "resources": [...] }               │
  │ <─────────────────────────────────────│
```

This is the critical security design: cf-auth-mcp does **not** use a static
service account. Instead, `UserScopedCfClientFactory` creates fresh CF client
instances for each request using `AccessTokenRelayTokenProvider`, which relays
the authenticated user's own JWT to the CF API. The CF API then enforces
role-based access control:

- A **SpaceDeveloper** can manage apps, services, and routes within their spaces
- A **SpaceAuditor** can only view resources
- An **OrgManager** can manage spaces and view org-level details
- Access across orgs and spaces is governed by the user's actual role assignments

### Step 6 — Response Streams Back to the User

```
cf-auth-mcp                     Goose CLI
  │                                  │
  │  200 OK                          │
  │  { "result": [                   │
  │    { "name": "my-app", ... },    │
  │    { "name": "other-app", ... }  │
  │  ]}                              │
  │ ────────────────────────────────>│

Goose CLI                       goose-agent-chat
  │                                  │
  │  [LLM formats the tool result    │
  │   into a natural language        │
  │   response]                      │
  │                                  │
  │  {"type":"message","message":    │
  │   {"content":[{"type":"text",    │
  │    "text":"You have 2 apps..."}  │
  │   ]}}                            │
  │ ────────────────────────────────>│

goose-agent-chat                Browser
  │                                  │
  │  event: token                    │
  │  data: "You have 2 apps..."     │
  │ ────────────────────────────────>│
```

The MCP tool result flows back through Goose CLI (which passes it to the LLM
for formatting), then through goose-agent-chat's SSE stream to the browser,
where the Angular frontend renders the response in the chat interface.

---

## SSO Binding Summary

Both applications are bound to the **same** `p-identity` service instance,
which means they share the same authorization server and trust domain. However,
each application gets its **own** client registration with a unique
`client_id` and `client_secret`.

```
┌─────────────────────────────────────────────────────────────────┐
│                    p-identity Service Instance                   │
│                     (SSO tile, plan: uaa)                       │
│                                                                 │
│  Authorization Server: https://login.sys.tas-ndc.kuhn-labs.com  │
│  Token Issuer (UAA):   https://uaa.sys.tas-ndc.kuhn-labs.com   │
│                                                                 │
│  ┌─────────────────────────┐  ┌─────────────────────────────┐  │
│  │  goose-agent-chat       │  │  cf-auth-mcp                │  │
│  │  (OAuth Client)         │  │  (Resource Server)          │  │
│  │                         │  │                             │  │
│  │  client_id: c7b38889-.. │  │  client_id: 2b33fbd0-..    │  │
│  │  grant: authorization_  │  │  grant: authorization_code  │  │
│  │         code            │  │                             │  │
│  │                         │  │  Uses binding for:          │  │
│  │  Uses binding for:      │  │  • JWT issuer-uri (UAA)     │  │
│  │  • client_id/secret in  │  │    → token validation       │  │
│  │    token exchange       │  │  • auth_domain (login.sys)  │  │
│  │  • App login (Spring    │  │    → RFC 9728 metadata      │  │
│  │    Security OAuth2      │  │                             │  │
│  │    Client)              │  │                             │  │
│  └─────────────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

The shared trust domain is what makes the flow work: goose-agent-chat obtains
tokens from the SSO login server using its own `client_id`, and cf-auth-mcp
validates those same tokens using the shared JWT signing keys from UAA.

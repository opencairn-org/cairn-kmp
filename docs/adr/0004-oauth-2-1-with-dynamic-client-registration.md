# 4. OAuth 2.1 with dynamic client registration

Date: 2026-10-28

## Status

Accepted

## Context

cairn-core is an OAuth 2.1 authorization server (authorization code with PKCE, refresh-token
rotation, RFC 7591 dynamic client registration, RFC 8414 discovery). It accepts bearer access
tokens and personal access tokens on every RPC and refuses mutations for tokens without a write
scope. The app connects to arbitrary instances, so no client id can be baked in.

## Decision

- The app registers itself once per server with dynamic client registration as a public client
  and caches the `client_id` per server.
- Sign-in is the authorization-code flow with PKCE S256 in the system browser (Custom Tabs,
  `ASWebAuthenticationSession`, loopback redirect on desktop), never a WebView.
- Read-only scopes only, until a write-capable release exists.
- Tokens live in the platform secret store (Keychain; Keystore-encrypted file; 0600 file on
  desktop) and nowhere else. A personal access token is the fallback for development.

## Consequences

Every instance works without administrator setup. Desktop needs core to accept any loopback
port (RFC 8252 §7.3), tracked in the handover.

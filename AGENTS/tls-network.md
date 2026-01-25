# TLS and network notes (API 7)

## TLS limitations on Android 2.1

Android 2.1's OpenSSL only supports up to TLS 1.0 (and SSLv3).
It cannot negotiate TLS 1.2 or TLS 1.3.

Implications:
- HTTPS to modern APIs will fail with SSL handshake errors.
- This is a system library limitation, not fixable in app code.
- TLS 1.2 support only arrives in Android 4.1 (API 16).

Workarounds:
1. HTTP fallback (if the API supports it).
2. Proxy that accepts TLS 1.2+ and speaks TLS 1.0 to the device.
3. Use APIs that still allow TLS 1.0 (rare).

## SpongyCastle TLS 1.2 workaround

This repo includes a manual TLS client using SpongyCastle to reach modern
HTTPS endpoints on API 7.

Key points:
- TLS 1.2 only (no TLS 1.3 in SpongyCastle 1.58).
- Manual HTTP request/response via `TlsClientProtocol`.
- Adds SNI and `extended_master_secret`.
- Explicit cipher suite list (prioritizes ECDHE_RSA + AES).

Shortcuts (intentional for now):
- Trust-all server certs (no CA validation or pinning).
- No hostname verification beyond SNI.
- Limited cipher list.
- No ALPN/HTTP2 (HTTP/1.1 only).

Where implemented:
- `src/com/bpmct/trmnl_nook_simple_touch/BouncyCastleHttpClient.java`

Setup / install (SpongyCastle 1.58):
1. Download JARs into `libs/`:
   - `https://repo1.maven.org/maven2/com/madgag/spongycastle/core/1.58.0.0/core-1.58.0.0.jar`
   - `https://repo1.maven.org/maven2/com/madgag/spongycastle/prov/1.58.0.0/prov-1.58.0.0.jar`
   - `https://repo1.maven.org/maven2/com/madgag/spongycastle/bctls-jdk15on/1.58.0.0/bctls-jdk15on-1.58.0.0.jar`
2. ADT/Ant picks up `libs/*.jar` automatically.
3. Rebuild the APK.

Notes:
- JARs are gitignored (large, local-only).
- SpongyCastle 1.58 provides TLS 1.2, not TLS 1.3.

## Network API patterns for API 7

HTTP/HTTPS requests:
- Use `HttpURLConnection` (available in API 7).
- Always call `connect()` before `getResponseCode()`.
- Set 15-20s timeouts.
- Handle `-1` response codes (connection failures).
- Retry transient failures.

Error handling:
- Log full stack traces to logcat.
- Show user-friendly errors on screen.
- Detect SSL errors and trigger HTTP fallback.

Example pattern (see `FullscreenActivity.java`):
```java
// Try HTTPS first
Object result = fetchUrl(httpsUrl, true);

// If HTTPS fails with SSL error, try HTTP fallback
if (result != null && result.toString().contains("SSL") && httpUrl != null) {
    result = fetchUrl(httpUrl, false);
}
```

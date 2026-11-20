# ADR-0008: TLS identity, pairing and authentication details

**Context.** Phase 3a implements the connection that ADR-0005 described. Several concrete choices needed evidence.

**Decisions.**
- **Certificate generation:** Ktor's `buildKeyStore { certificate { ... } }` (artifact `ktor-network-tls-certificates` 3.6.1, no BouncyCastle), producing an ECDSA P-256 / SHA-256 self-signed certificate valid for 10 years. The older `generateCertificate` helper is documented as "testing only" and defaults to weak parameters; the builder is used with explicit strong parameters, and because clients pin the fingerprint, CA trust is irrelevant. The certificate was verified externally with `openssl` (P-256, `ecdsa-with-SHA256`, fingerprint equal to the one the agent prints).
- **Server engine: Netty.** Ktor's CIO *server* engine throws `UnsupportedOperationException` for `sslConnector`; this was found by the integration tests. Netty supports TLS.
- **Trust managers extend `X509ExtendedTrustManager`.** With a plain `X509TrustManager` the JSSE adds hostname verification, which cannot succeed for a self-signed certificate reached by IP address. With the extended variant the trust manager owns the identity check, which here is the fingerprint pin. `PinnedTrustManager` is used for all normal connections; `CapturingTrustManager` (accepts and records) only for the PIN pairing handshake, protected by the manual code comparison.
- **Authentication:** ECDSA P-256 (`SHA256withECDSA`, DER signatures) with Android Keystore keys; the signed bytes are `"SDHelper-auth-v1" 0x00 || nonce || SHA-256(certFingerprintHex)`, binding the signature to the agent's certificate.
- **Pairing offers:** token 24 random bytes (Base64 URL), PIN 6 digits, 2 minutes, single use, 5 attempts, constant-time comparison; the PIN path additionally needs approval at the PC.
- **State on the agent:** JSON device list written atomically; key store password in a file readable only by the owner. Windows DPAPI protection follows in Phase 3b.
- **Discovery:** JmDNS 3.6.5 (Apache-2.0) announces `_sdhelper._tcp`; failures are logged and never block startup.

**Alternatives.** BouncyCastle for certificates (extra dependency without need); mutual TLS (harder on Android, signature-based device auth is enough); HMAC with the PIN as a PAKE substitute (offline PIN guessing by a man in the middle is trivial; explicit approval with code comparison is stronger).

**Consequences.** The protocol, pairing and authentication are exercised by integration tests over real TLS. Dependencies added to the agent: Ktor server (Netty, WebSockets, certificates) 3.6.1, JmDNS 3.6.5 (Apache-2.0), zxing core 3.5.4 (Apache-2.0, QR rendering), slf4j-simple 2.0.20 (MIT per the SLF4J project; the POMs carry no license field, so this was not verified from Maven metadata).

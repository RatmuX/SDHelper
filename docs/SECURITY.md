# SDHelper Security Model

SDHelper lets a phone press keys and run actions on a computer. A paired device effectively controls the PC, so security is a hard requirement, not a feature.

## 1. Assets
- The ability to trigger actions on the PC (including key injection).
- Pairing keys and certificates.
- Plugin permissions and any plugin secrets (API tokens).

## 2. Threats and mitigations

| Threat | Mitigation |
|---|---|
| Someone on the same LAN sends commands | No command accepted before pairing; per-device keys; challenge-signature authentication on every connection |
| Eavesdropping / man in the middle on the LAN | TLS; certificate pinned by SHA-256 fingerprint obtained out-of-band (QR) or confirmed with the PIN |
| Pairing brute force | PIN is 6 digits, valid for a short window, limited attempts, pairing mode must be enabled by the user on the agent |
| Stolen or lost phone | Device list on the agent with one-click revoke; Android Keystore protects the device key |
| Agent exposed to the internet | Binds to private-network interfaces only by default; documented that port forwarding is unsupported |
| Malformed or flooding messages | Size limit, strict parsing, per-device rate limit, connection closed on repeated violations |
| Malicious plugin | Permissions declared in the manifest and approved by the user at install; honest limit below |
| Secrets in the repository | `.gitignore` excludes keys; CI secret scanning; Windows secrets stored with DPAPI, Android with Keystore |

## 3. Pairing flow
1. The user enables pairing mode in the agent. The agent shows a QR code and a 6-digit PIN.
2. QR content: host, port, certificate SHA-256 fingerprint, one-time pairing token (short-lived).
3. Phone connects, verifies the fingerprint, creates a key pair in Android Keystore and sends its public key with the token (or PIN).
4. The agent stores the public key and device name. Later connections authenticate with a signed challenge.
5. Manual IP path: the user types the IP and the PIN; the PIN proves the user sees the agent screen, and the fingerprint is shown on both sides for confirmation.

## 4. Network
- Agent listens only on private-range interfaces (RFC 1918, link-local) by default. Exact interface selection is finalized in Phase 3.
- mDNS announcements reveal only the service name, port and a non-sensitive agent id.

## 5. Plugin security (honest limits)
- Plugins run as separate processes under the same Windows user as the agent. **There is no real sandbox.** Declared permissions are an informed-consent mechanism and are enforced where the agent mediates access (for example key injection through the host API), not against arbitrary code that a plugin process runs itself.
- Install only plugins you trust. The catalog repository applies review (see `PLUGIN_REPO_PLAN.md`), which reduces but does not remove risk.
- Plugin processes get a minimal environment and do not receive pairing keys.

## 6. Not covered in v1
Internet exposure, multi-user PCs, OS-level sandboxing of plugins, protection against a fully compromised PC or phone.

## 7. Reporting vulnerabilities
Use GitHub private vulnerability reporting on `RatmuX/SDHelper` (to be enabled when the repository is published).

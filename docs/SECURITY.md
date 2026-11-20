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
| Pairing brute force | PIN is 6 digits, valid 2 minutes, 5 attempts per offer; an offer exists only while the agent window (or console) is showing it, and hiding the window withdraws it; the PIN path also needs approval at the PC |
| Stolen or lost phone | Device list on the agent with one-click revoke; Android Keystore protects the device key |
| Agent exposed to the internet | Connections from non-private addresses are closed right after the WebSocket upgrade (the agent binds all interfaces, see section 4); port forwarding is unsupported |
| Malformed or flooding messages | Size limit, strict parsing, per-device rate limit, connection closed on repeated violations |
| Malicious plugin | Permissions declared in the manifest and approved by the user at install; honest limit below |
| Secrets in the repository | `.gitignore` excludes keys; CI secret scanning; Android keys live in the Keystore |
| Agent TLS key readable by other local users | Key store and its password are files readable only by the owner (`rw-------` on POSIX, user-profile ACL on Windows). **Windows DPAPI protection is not implemented yet (planned for Phase 3b); until then any process running as the same user can read the key.** |

## 3. Pairing flow
See `PROTOCOL.md` section 3 for messages. Summary:
1. Pairing mode shows an offer on the agent: QR code (host, port, certificate SHA-256 fingerprint, one-time token) and a 6-digit PIN. The offer lives 2 minutes, is single use, and locks after 5 wrong attempts.
2. **QR path (primary):** the phone pins the fingerprint from the QR code, so a machine in the middle cannot present another certificate. The token proves the phone saw the screen.
3. **PIN path (manual IP):** the phone cannot know the fingerprint in advance. It accepts the certificate for that one handshake and shows a short code; the agent asks the person at the PC to approve after comparing both codes.
4. The phone creates an EC P-256 key pair in the Android Keystore (non-exportable) and sends only the public key. Every later connection authenticates with a signature over a fresh nonce **and the agent's certificate fingerprint**.
5. The agent keeps the device list on disk; removing a device revokes it immediately, even for an open connection.

**Residual risks of the PIN path.** Someone on the same network who learns the PIN can start a pairing request that shows a confirmation dialog on the PC; they cannot finish it unless the person approves. A man in the middle who relays the PIN handshake shows a different code than the PC does, which is why the codes must be compared. Anyone who approves without comparing weakens this path to "trust the local network". Use the QR path when in doubt.

## 4. Network
- The server accepts connections only from private-range addresses (loopback, RFC 1918, link-local, IPv6 unique-local); anything else is closed right after the WebSocket upgrade. It binds to all interfaces so that the phone can reach it on whichever network adapter is in use; this means the TLS handshake itself is answered for any peer, but no application data is. Restricting the bind to chosen adapters is a later improvement.
- Limits: 64 KiB frames, at most 16 simultaneous connections, bounded idle and anonymous lifetime, per-connection rate limit.
- mDNS announcements (`_sdhelper._tcp`) reveal only the computer name, port, agent id and protocol version. Announcing is best effort; pairing by QR or IP works without it.

### Phone: local network permission
From Android 17 the platform blocks traffic to the local network for apps targeting SDK 37 until the user grants `ACCESS_LOCAL_NETWORK`. SDHelper asks for it only when the person opens a screen that talks to a computer, explains why, and does not connect or scan before it is granted. The permission is a platform privacy control and does not replace pairing or TLS pinning.

### Phone: keys
One EC P-256 key per paired computer lives in the Android Keystore (non-exportable, signing only) and is deleted when the computer is forgotten or pairing fails. The phone stores only the computer's address, name, certificate fingerprint and the device id; none of it is secret.

## 5. Plugin security (honest limits)
- Plugins run as separate processes under the same Windows user as the agent. **There is no real sandbox.** Declared permissions are an informed-consent mechanism and are enforced where the agent mediates access (for example key injection through the host API), not against arbitrary code that a plugin process runs itself.
- Install only plugins you trust. The catalog repository applies review (see `PLUGIN_REPO_PLAN.md`), which reduces but does not remove risk.
- Plugin processes get a minimal environment and do not receive pairing keys.

## 6. Not covered in v1
Internet exposure, multi-user PCs, OS-level sandboxing of plugins, protection against a fully compromised PC or phone.

## 7. Reporting vulnerabilities
Use GitHub private vulnerability reporting on `RatmuX/SDHelper` (to be enabled when the repository is published).

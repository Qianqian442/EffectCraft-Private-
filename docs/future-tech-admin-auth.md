# 未来科技管理版 — secure administrator authentication

## Password handling

The administrator password supplied during product setup is a bootstrap credential, not a value to hardcode into source code.

It must never appear in:
- Git repositories or GitHub Actions logs;
- Android resources, BuildConfig constants, APK assets, or strings.xml;
- client-side database defaults;
- public release notes or test fixtures;
- crash logs or analytics events.

Because a password typed into a chat or ordinary issue is exposed to additional systems and history, rotate it before public deployment. Treat any bootstrap credential as temporary and require the first successful admin login to change it.

## Storage and verification

### Server-side source of truth
- Store a per-account password hash, never plaintext or reversible encryption.
- Prefer Argon2id with calibrated memory/time/parallelism. If the selected backend cannot support Argon2id, use a well-reviewed password-hashing implementation such as scrypt or PBKDF2-HMAC-SHA-256 with a unique random salt and strong parameters.
- Use a server-held pepper only through a secret manager/environment secret, not in the repository or APK.
- Compare derived hashes in constant time.
- Do not log request bodies containing passwords.

### Android client
- The admin APK sends credentials only over HTTPS to the authenticated server endpoint.
- Do not save the plaintext password after login.
- Store only short-lived, revocable tokens using Android Keystore-backed encrypted storage.
- Require re-authentication for password changes, unbans, allowlist changes, runtime release changes, and other high-risk actions.
- Force rotation after initial bootstrap login; revoke all other admin sessions after the change.
- Provide a server-side recovery/rotation process that requires proof of control of the administrator account/server.

## Bootstrap procedure

1. Operator provisions a one-time bootstrap credential directly into the server using a private deployment secret or protected console input.
2. The server hashes it immediately and stores only the hash and a `must_change_password=true` flag.
3. The admin APK performs first login over HTTPS.
4. The server issues a narrowly scoped, one-time password-change challenge.
5. Admin sets a new random password; server replaces the hash, clears the bootstrap flag and revokes existing sessions.
6. Bootstrap credential becomes invalid permanently.

The credential must not be placed in a public GitHub workflow YAML. If a hosted CI service is used, use its encrypted secret store and never print the secret.

## Authorization model

Roles:
- `user`: own profile, projects, runtime downloads and own sessions only.
- `admin`: account allowlist, bans, device revocation and runtime release management.
- `super_admin`: bootstrap, role grants, password recovery and security policy changes.

Every server endpoint performs authorization independent of Android UI visibility. Use deny-by-default access control.

## Audit record

Each privileged action records:
- unique event ID;
- actor account and role;
- action type;
- target account/device/release;
- UTC timestamp;
- reason;
- result and server correlation ID.

Do not include raw passwords, session tokens, recovery codes, or full private device identifiers in audit logs.

## Required tests

- Bootstrap credential can be used once and is rejected after rotation.
- Password never appears in logs, responses, build files, or APK contents.
- User tokens cannot call admin APIs.
- Removing an account from the allowlist revokes new session and refresh access.
- A banned account cannot login or renew tokens.
- Revoked sessions stop working.
- Rate limits and lockout protections work.
- Audit records are written for every admin mutation.

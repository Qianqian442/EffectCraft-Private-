# 未来科技 (Future Technology)

This repository contains two separate Android applications and the account server for the Future Technology Windows-compatibility project.

- **未来科技** — user APK, Ae-style QQ/WeChat registration, photo-picker avatar, device capability report, QQ/file-share import entry, and app-private Windows compatibility runtime control surface.
- **未来科技管理版** — administrator APK, online login, forced password change, account overview, whitelist, bans/unbans, and audit interface.

## Security and password lifecycle

- No administrator password is stored in this public repository, source code, APK, build output, or logs.
- The server reads `ADMIN_BOOTSTRAP_PASSWORD` only from a private Cloudflare Worker secret. First bootstrap login forces password change.
- Admin tokens are memory-only in the admin APK. Every app launch requires online login with the **current** server password. Password change revokes every admin session; a later download/reinstall therefore requires the new password.
- Server password hashes use PBKDF2-HMAC-SHA-256 with a unique random salt and 600,000 iterations. Plaintext passwords are never persisted or logged.
- User registration requires QQ or WeChat (either one is sufficient). New accounts remain pending until the administrator adds them to the whitelist.
- Allowlist, ban, unban, and session revocation are enforced by the server and recorded in an audit log.
- The runtime is app-private. It does not patch Android system partitions, weaken SELinux, require root, or bypass account verification.

## Automated builds

`.github/workflows/build-future-technology.yml` builds two separate APKs:
- `Future-Technology.apk`
- `Future-Technology-Admin.apk`

The workflow verifies ZIP integrity, application package IDs, APK signatures, and uploads both as a GitHub Actions artifact. These are development candidates until the account server is deployed and the Windows runtime has been integrated and device-tested.

The account Worker and D1 schema are checked by `.github/workflows/validate-future-technology-server.yml`.

## Account-server deployment

Cloudflare deployment is separate from APK compilation. It requires:
1. A Cloudflare API token with Workers and D1 permissions.
2. A D1 database named `future-tech-accounts`; copy its database ID.
3. These GitHub Actions secrets:
   - `CLOUDFLARE_API_TOKEN`
   - `CLOUDFLARE_ACCOUNT_ID`
   - `FUTURE_TECH_D1_DATABASE_ID`
   - `FUTURE_TECH_ADMIN_BOOTSTRAP_PASSWORD` (use a fresh random password; do not reuse credentials exposed in chat)
4. GitHub Actions variable `FUTURE_TECH_WORKER_URL` containing the deployed Worker URL, e.g. `https://future-tech-account-api.<your-cloudflare-subdomain>.workers.dev`.
5. Run the manual workflow **Deploy 未来科技 account server**.

The default first administrator name is `admin`. After first login, the server requires the bootstrap password to be changed. The admin APK deliberately asks for the current password on each launch.

Set GitHub Actions variable `FUTURE_TECH_API_BASE` to the same HTTPS Worker URL and rebuild both APKs after server deployment.

## Current limitations

- The user/admin Android project scaffolds and API source are present, but Cloudflare D1/Worker deployment has not been performed.
- Wine, Box64/Box86, the Linux root filesystem, graphics backends (Mesa/Turnip, DXVK/VKD3D), and a functional Windows process/session manager have not yet been integrated into the APK. The runtime switch currently reports this honestly and will not execute unverified binaries.
- The user-supplied icon image is not available as a materialized file in the active repository workspace yet; the app icon remains a replaceable placeholder until that image is added.

A normal Android app cannot silently implant a system-signed framework into protected Android system partitions. Future Technology uses an app-private compatibility container that can be enabled/disabled within Android's sandbox.

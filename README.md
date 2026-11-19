# 未来科技 (Future Technology)

This repository contains two separate Android applications and the account server for the Future Technology Windows-compatibility project.

- **未来科技** — user APK for importing/installing Windows desktop software inside an app-private compatibility container, showing installed apps on an in-app desktop, and creating shortcuts after installation. AE project imports belong to Ae.
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

The APK workflow is manual-only and requires an explicit release-readiness confirmation. Do not build or distribute an APK until the full Windows runtime, computer-software marketplace, automatic installed-app discovery, desktop icons/shortcuts, account backend, and real-device tests all pass. These are development candidates until the account server is deployed and the Windows runtime has been integrated and device-tested.

The public `GET /api/v1/marketplace/catalog` route reads active listings from the D1 `software_catalog` table. The Android marketplace displays searchable listings and downloads eligible redistribution-authorized packages directly to phone storage; it checks the exact file size and SHA-256 before marking a package verified. The Worker returns catalogue metadata and does not proxy installer bytes. No software listings are activated by default; an operator must validate and activate entries.

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

- QQ/Android file sharing is now copied into an app-private inbox with a SHA-256 digest and basic PE/ELF/WASM/AE-extension classification. This is file intake only: it does not yet parse `.aep` projects, resolve desktop media, load `.aex` plug-ins, or execute `.exe` files.\n- The user/admin Android project scaffolds and API source are present, but Cloudflare D1/Worker deployment has not been performed.
- Wine, Box64/Box86, the Linux root filesystem, graphics backends (Mesa/Turnip, DXVK/VKD3D), and a functional Windows process/session manager have not yet been integrated into the APK. The runtime switch currently reports this honestly and will not execute unverified binaries.
- The green image supplied on 2026-11-19 has now been added as a separate **电脑应用商城** icon asset. It is not the Android launcher icon. The marketplace UI/catalogue still needs implementation.

A normal Android app cannot silently implant a system-signed framework into protected Android system partitions. Future Technology uses an app-private compatibility container that can be enabled/disabled within Android's sandbox.

## 未来科技 (Future Technology)

The repository now contains separate Android app modules for:

- `android/main-app` — 未来科技 user APK.
- `android/admin-app` — 未来科技管理版 APK.
- `android/shared` — HTTPS API client, Android Keystore token vault, and avatar input helpers.
- `server/cloudflare` — selected Cloudflare Workers + D1 + R2 account backend starter.

The latest main APK and admin APK have a successful debug build, package/signature verification, and Windows software package intake compilation in [GitHub Actions run #44522729009](https://github.com/Qianqian442/EffectCraft-Private-/actions/runs/44522729009). The outputs are debug-signed review candidates, not production releases. Static import-safety checks are also added to the build workflow. The account server has not been deployed, and no real account login or ban/allowlist action has been tested against a live server.

**The Windows compatibility runtime is not yet integrated into either APK.** The current build can safely copy supported Windows installer packages into a private pending-install queue, but the actual Wine/Box64/Box86 engine is not yet integrated; installation, app discovery, in-app desktop icons and software launching are therefore not yet functional. Do not describe the current APKs as working Windows compatibility builds.

### First application integration target: Ae

After the Android Windows runtime has passed its independent device tests, Ae/EffectCraft is the first and only application integration target. The acceptance plan is in [docs/ae-first-integration-gate.md](docs/ae-first-integration-gate.md). It tests real desktop After Effects projects and plug-ins in Ae before expanding to any other Windows software.

### Account security

The management APK requires an online login using the latest server password after reinstall. Password rotation revokes server-side old sessions. Administrator passwords and signing keys must be configured through private deployment secrets, never committed to this public repository or embedded in APKs.

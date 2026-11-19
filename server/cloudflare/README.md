# 未来科技账户服务器

This is the selected Cloudflare Workers + D1 + R2 backend for the two Future Technology Android apps. It has not yet been deployed.

## Required Cloudflare resources

1. Create a D1 database named future-tech-accounts.
2. Create an R2 bucket named future-tech-avatars.
3. Create a Cloudflare Worker deployment token with permission to deploy Workers, manage D1, and manage R2.
4. In the GitHub repository Actions secrets, configure:
   - CLOUDFLARE_API_TOKEN
   - CLOUDFLARE_ACCOUNT_ID
   - FUTURE_TECH_D1_DATABASE_ID
   - FUTURE_TECH_JWT_SECRET (random, at least 48 bytes)
   - FUTURE_TECH_ADMIN_BOOTSTRAP_PASSWORD (new, random, at least 12 characters; do not reuse a password posted in chat)
5. In GitHub Actions variables, configure FUTURE_TECH_WORKER_URL as the Worker origin, and FUTURE_TECH_API_BASE as the same origin plus /api/v1.
6. Run the manual workflow "Deploy 未来科技 account server". It applies the D1 schema, configures private secrets and deploys the Worker.
7. The first request creates the super-admin account with username admin if it does not already exist.

The bootstrap password is read only from a Cloudflare secret, hashed immediately on first initialization, and never included in an APK or repository. First admin login must change the password. The server revokes all old administrator sessions after a password change. Re-downloading or reinstalling the management APK removes its local Keystore token; it must log in online with username admin and the latest password. The previous password will no longer work.

## Account behavior

Normal user registrations auto-activate by default, matching the Ae account flow. QQ or WeChat is required; both are not required. Users can select an avatar from the phone photo picker. An administrator can turn on whitelist mode; while it is enabled, new accounts remain pending until allowlisted. Banned accounts are always rejected, and ban actions revoke active sessions.

## Security boundary

- Passwords are PBKDF2-HMAC-SHA-256 hashes with a random per-account salt; plaintext is never stored.
- Short-lived access tokens and rotating refresh tokens are stored hashed on the server; the Android clients protect local token storage with Android Keystore.
- All admin routes check the server-side role on every request.
- Passwords, signing keys and Cloudflare credentials are never embedded in APKs.
- Windows compatibility processes must not receive account tokens or administrator credentials.

This backend still needs independent security review, abuse/bot controls, backups, alerting, secret rotation, avatar image re-encoding, and penetration testing before production use.

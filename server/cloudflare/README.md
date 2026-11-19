# 未来科技账户服务器

This is an initial Cloudflare Workers + D1 + R2 backend. It is not deployed yet. A Cloudflare account, HTTPS hostname, D1 database, R2 bucket and secrets are required.

1. Create D1 database future-tech-accounts and R2 bucket future-tech-avatars.
2. Copy wrangler.toml.example to wrangler.toml and replace the D1 database ID.
3. Apply schema.sql using Wrangler D1 commands.
4. Privately set JWT_SECRET, ADMIN_BOOTSTRAP_ACCOUNT and ADMIN_BOOTSTRAP_PASSWORD using Wrangler secret put.
5. Deploy with Wrangler.
6. Set the resulting HTTPS base URL as FUTURE_TECH_API_BASE for the Android build. Include /api/v1 in the base URL.

The administrator bootstrap password must be entered privately, never committed to this public repository, never embedded in an APK and never printed by CI. Credentials previously typed in chat must be treated as exposed; choose a new random password during deployment.

The first request creates the bootstrap super-admin if it does not exist. The server hashes its password and sets must_change_password. The first successful admin login must change it. Once changed, the old password is invalid. Re-downloading/reinstalling the admin APK does not reset the server password; a fresh install loses its local Keystore token and must log in using the latest password.

Normal user registration auto-activates by default to preserve the Ae registration behavior. The administrator can enable whitelist mode; while enabled, new accounts remain pending until allowlisted. Banned accounts are always rejected. Every privileged operation is authorized by the server, not by hiding client buttons.

This starter needs independent security review before production: bot protection for signup, per-account/device abuse controls, monitoring, backups, secret rotation, avatar re-encoding, and penetration testing. Keep Windows compatibility processes separate from account secrets and admin APIs.

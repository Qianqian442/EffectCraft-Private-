# 未来科技 (Future Technology) — product specification

## Two separate Android applications

### A. 未来科技 — main/user APK

Purpose: let a user enable or disable an app-private Windows compatibility environment on supported Android devices.

User-facing behavior:
- One large switch/button: **开启兼容环境 / 关闭兼容环境**.
- The button controls only the compatibility runtime owned by this app. It does not patch Android system partitions, modify the boot image, replace the Android linker, or install system services.
- While enabled, show runtime state, selected CPU translation path, graphics backend, storage usage, and current Windows-container session.
- When disabled: stop Windows applications, stop Wine/Box64 processes, release foreground service and graphics resources, close session handles, and optionally offer to delete the downloaded rootfs/runtime cache. Keep user documents and imported projects unless the user explicitly selects deletion.
- A persistent notification is shown while a long-running compatibility session needs a foreground service.
- Show the device compatibility report before downloading large components. Do not claim support based on CPU type alone.
- Import Windows executables and projects using Android Storage Access Framework (SAF), ACTION_VIEW and ACTION_SEND. Never assume a content URI is a filesystem path.
- Keep Windows programs in an app-private rootfs/container and communicate with the native app through a narrow, versioned IPC bridge.

Registration and user account:
- Follow the Ae registration pattern.
- Require at least one of QQ ID or WeChat ID; the other field remains optional.
- Support choosing an avatar directly from the Android photo picker.
- Support account registration, login, token renewal, logout, and a clear banned/not-whitelisted state.
- Register only through the configured account server using HTTPS and authenticated requests.
- Never embed administrator passwords, server signing keys, or permanent bearer tokens in the APK.

### B. 未来科技管理版 — administrator APK

Purpose: securely administer the account server and runtime distribution policies.

Admin capabilities:
- Admin login and forced password change when using a bootstrap credential.
- Account list and account detail.
- Allowlist management: add/remove permitted accounts and devices.
- Ban/unban accounts and devices, with a reason and audit record.
- Review registered account count, last login, device count, and session activity.
- Revoke sessions and rotate tokens.
- Manage compatible runtime release channels and minimum supported version.
- Audit log for administrator actions.
- Multi-factor or device-bound confirmation for sensitive actions where available.

Security rules:
- The administrator APK is not the source of truth. The server is.
- Every privileged operation must be checked again by the server.
- A hidden button or local role flag is not security.
- The main APK must not be able to call admin endpoints.
- Ban and allowlist checks are enforced on every new login/session and on token renewal.
- Admin actions require a fresh authenticated session; high-risk actions require re-authentication.
- Keep an append-only audit trail containing actor, action, target, timestamp, reason, and result.
- Rate-limit login and recovery endpoints; use generic authentication failure messages to reduce account enumeration.
- Use HTTPS and signed, short-lived access tokens with refresh-token rotation.
- Do not allow the compatibility container or Windows program to read administrator credentials or access the host account database directly.

## Compatibility runtime lifecycle

States:
- OFF
- CHECKING_DEVICE
- DOWNLOADING_COMPONENTS
- PREPARING_ROOTFS
- STARTING
- RUNNING
- STOPPING
- ERROR

The app may enter RUNNING only after runtime process and IPC health checks pass. The OFF transition must stop the runtime and confirm that no owned Wine/Box64/Windows process remains. If Android kills the process or revokes a permission, recover into a safe OFF/ERROR state rather than claiming the runtime is active.

## Platform and permission boundary

A normal Android app cannot silently implant a Google-Services-like system framework into Android's protected system image. Without OEM/system privileges or root, the achievable design is an app-private runtime that behaves like an installable compatibility environment. Android may still restrict execution, memory, filesystem and graphics behavior.

No root exploit, boot-image patch, SELinux weakening, signature spoofing, device-integrity bypass, or hidden system modification is part of this product.

## Current implementation status

This specification does not mean either APK is built. The compatibility lab currently contains research documents, a binary inspector, a project IR schema, and tests. The Winlator/Wine/Box64 runtime has not yet been vendored, built, or validated inside 未来科技.

# Future Technology — completion and release gate

This is the agreed product boundary for the final user-facing Future Technology Android app. It is a PC-software runtime and launcher on Android, not an After Effects project importer.

## Compatibility environment enable/bootstrap

- “开启兼容环境” means downloading/verifying and deploying the Windows compatibility runtime into Future Technology's app-private storage on first use, initializing the Windows container/prefix, and then starting that runtime. It does not modify Android system partitions, replace Android framework components, require root, or weaken SELinux.
- First-time enablement must show download/deployment progress, required phone storage, supported device/ABI checks, signed manifest and per-file hash verification, and clear recovery/rollback if any component is incomplete.
- After the runtime reports a healthy ready state, users can install and launch supported Windows software. Later enable/disable actions start/stop the existing container without deleting installed programs or user data.
- A manifest file alone is not proof that the environment is installed or usable. Do not show “已开启” until integrity, dependency, graphics/audio/input checks and container startup succeed.

## Marketplace catalogue and phone-side download implementation

- Public route: `GET /api/v1/marketplace/catalog`; only active D1 rows are returned.
- Android renders the dedicated marketplace icon, searchable catalogue cards and category filters.
- The marketplace downloads permitted installer bytes directly to this phone using Android DownloadManager. The Worker serves catalogue metadata only; it does not proxy the installer payload.
- A direct download is enabled only when the entry allows redistribution and provides HTTPS download URL, expected byte count and a 64-character SHA-256 digest.
- After download the app verifies exact byte count and SHA-256. An incomplete or mismatched file is deleted and never marked verified.
- A verified download is still not an installed program. It remains pending until the Windows container installer, installed-app discovery and launch integration are complete.

## Required user journey

1. Open the **电脑应用商城** inside Future Technology.
2. Browse software entries with publisher, version, licence, supported Windows architecture, installer format, download size, estimated RAM/storage needs, source, compatibility notes, and verified checksum/signature metadata.
3. Select **下载**. The installer is downloaded to storage on the phone itself. The account server/catalogue supplies metadata and download links; it must not proxy or consume the phone's installer storage.
4. When download and integrity verification finish, select **安装**. The package is installed inside Future Technology's isolated Windows container using Wine/Box64/Box86 or a verified compatible runtime. It is not installed as an Android APK.
5. When installation succeeds, the program is automatically discovered and appears in Future Technology's **电脑软件库/桌面** with a display name and icon. The user should not have to manually re-import the program.
6. From the Future Technology desktop/software library the user can launch, pin, unpin, configure, or uninstall the Windows program. The shortcut must point to the installed executable and its container/launch configuration, not to the downloaded installer.
7. The user may also choose **创建手机桌面快捷图标**. This is a real Android launcher/home-screen shortcut. It opens Future Technology and launches the selected installed Windows program in its container. It is separate from icons shown inside Future Technology.

## Marketplace and phone storage

- The software catalogue must only list software packages that may legally be distributed or linked. Paid/proprietary installers should link to the publisher's official download/ownership page unless the publisher explicitly permits redistribution. Do not bundle pirated or modified installers.
- Each catalogue record must include a stable app ID, name, publisher, version, Windows architecture, installer type, official source URL, expected byte size, SHA-256 (or stronger) digest, licence/redistribution status, minimum compatibility notes, and optional icon URL.
- Download directly to user-device storage using an Android-supported download mechanism, with progress, pause/cancel/retry where supported, resume, free-space check, network choice, collision-safe filenames, and persistent download history.
- Keep installer files in a clearly visible Future Technology downloads folder or an app-managed folder on the phone. Show actual disk usage. Support deleting the installer after successful installation without deleting the installed program.
- Verify downloaded bytes against the catalogue digest before installation. Reject incomplete, mismatched, or untrusted downloads. Never execute a download as an Android process.

## Windows installation, discovery, and launching

- The final APK must contain a real integrated Windows runtime, including all required Android ARM64 native components, Wine, Box64/Box86 as appropriate, a compatible root filesystem/container, audio, and tested graphics paths. Integrate source with required licences/notices and source-availability obligations.
- Software installation must run inside the Windows container/prefix. Support both installer-driven software (EXE/MSI) and explicitly supported portable archives. A file extension alone is not proof of a valid package.
- After installation, discover registered applications/Start Menu shortcuts from the container, resolve executable path, command-line arguments, working directory, icon, and container ID; then automatically add/update the entry in the Future Technology desktop/software library.
- Keep launch, process stop, uninstall, and application data scoped to the owning container. Never execute imported PE binaries directly in the Android app process; never require root or modify Android system partitions.
- Keep a manual **添加本地电脑软件安装包** route as a secondary option. It must use the same safe download/verification/install/discovery pipeline.
- Do not show .aep/.aepx project files or .aex After Effects plug-ins as Windows software installers. Those are Ae workflows.

## Android home-screen shortcuts

- A phone-home-screen shortcut must be requested through Android's supported pin-shortcut API. The launcher may display an approval prompt or may not support pinning; the app must report the result accurately and offer an in-app launch alternative.
- The shortcut label and icon come from the installed program. Its launch identity must resolve to the app ID and container launch record; it must not contain arbitrary installer commands from an untrusted catalogue.
- Removing a program must invalidate/remove its associated app shortcut where the launcher/API supports it.

## Memory and storage policy

- Do not enforce a fixed 1.12 GB, 1.5 GB, or other small runtime RAM ceiling.
- Allow the Windows runtime to use multiple gigabytes when the phone has enough available RAM. Keep a dynamic system safety margin, observe Android memory-pressure callbacks/low-memory state, and warn or stop cleanly rather than pretending the entire installed RAM is allocatable.
- Downloads and installed Windows files consume phone storage. Before download/install, calculate required space and remaining headroom; let the user choose an available storage location when Android permits.
- Display download size, installed footprint, and runtime memory guidance separately: these are different quantities.

## Accounts and existing product boundaries

- Preserve the separate administrator app, account server verification, registration/login, whitelist, ban/unban, device/session checks, and audit trail. Do not silently bypass the server.
- The green icon uploaded on 2026-11-19 is specifically the **电脑应用商城** icon. Keep it separate from the Future Technology Android launcher icon and the administrator-app icon.
- Future Technology and Ae remain separate products: Future Technology runs Windows software; Ae imports/edits After Effects projects.

## Release acceptance tests — all required before handing over an APK

1. Clean install and first-launch registration flow on a supported Android phone; manual login for existing accounts; admin controls remain enforced against a deployed test server.
2. Open the marketplace, inspect catalogue provenance/licence/architecture/size/checksum, download a listed installer to phone storage, interrupt/resume, verify progress and digest, and handle insufficient storage.
3. Install at least one verified Windows x86-64 desktop application inside the actual container; verify automatic appearance in Future Technology's desktop/library with correct name/icon.
4. Launch that app from Future Technology, interact with it, close it, relaunch it, and verify that it runs in the same isolated container.
5. Create a phone home-screen shortcut; approve it in the Android launcher; tap it from the phone launcher and verify it opens the correct installed Windows application. Verify unsupported launcher behavior.
6. Verify uninstall/shortcut cleanup, installed-data retention/removal choice, download deletion, and app update behavior.
7. Exercise a sustained multi-gigabyte-memory workload subject to device limits; verify warning/cleanup and no forced fixed low RAM cap.
8. Verify on the intended OPPO Android 12 device and separately record Huawei/other-device compatibility. Passing an emulator or APK-signature check alone is not enough.
9. Run clean build, APK signature/package verification, licence/notice checks, regression tests, and final artifact hash generation.

## Current status

The historical Future Technology review APKs are development scaffolds. They do not meet the release gate above: the integrated Windows runtime, marketplace, phone-local download/install pipeline, automatic installed-program discovery, Android home-screen shortcuts, live server deployment, and real-device end-to-end tests are still absent. No APK should be labelled or delivered as a finished Future Technology release until all required tests pass.
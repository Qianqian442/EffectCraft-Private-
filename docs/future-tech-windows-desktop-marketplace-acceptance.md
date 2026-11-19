# Future Technology Windows Desktop and Software Marketplace Acceptance

## Product definition

Future Technology is a Windows desktop-software environment on Android. It is not the Ae project importer. Ae owns After Effects projects (.aep/.aepx), AE plug-ins (.aex), and project conversion. Future Technology owns Windows application installation, desktop software launch, and per-application settings.

## Required user experience

1. Open Future Technology and enter the Windows desktop environment.
2. Open the built-in **电脑应用商城** (computer software marketplace) from that desktop.
3. Browse/search supported Windows desktop software; show architecture, minimum requirements, download size, publisher/source, version, compatibility rating, and known limitations before install.
4. Download a signed/catalogued installer or supported portable package. Verify HTTPS source, signed catalog metadata, SHA-256 digest, package size, architecture, and free storage before install.
5. Run the installer inside the selected app-private Windows container/prefix, never as an Android process and never with root or system-partition changes.
6. Detect successful installation automatically from the runtime's install completion signal plus Wine Start Menu/Desktop shortcut changes and uninstall registry entries. Do not rely solely on installer exit code.
7. Extract the installed application's real executable path, launch arguments, working directory, icon, container/prefix ID, and compatibility profile. Deduplicate entries and update changed installations.
8. Automatically add the installed app to Future Technology's in-app Windows desktop and software library. Offer create/remove shortcut, pin/unpin, rename shortcut, launch, repair, update, and uninstall actions. The installer itself must not appear as an installed application.
9. Tapping an app icon launches the executable in the correct container and uses that application's saved resolution, Box64 preset, graphics backend, environment variables, audio and input profile.
10. After restart, all installed-app entries and shortcuts are reconstructed from persistent runtime metadata and Wine prefix state. They must not disappear or depend on the original installer being present.

## Software catalog requirements

- Marketplace data must be served from a separately managed catalog API, with per-item package versions, architecture, publisher/source, package type, download URL, hash, signature metadata, minimum Android/RAM/GPU requirements, install recipe, launcher-discovery rules, and compatibility notes.
- Do not present an arbitrary download directory or an unsourced list of executables as an app store.
- Respect each software publisher's licence and redistribution terms. Where redistribution is not allowed, provide the official publisher's download page and install the user-selected package after validation.
- Catalog install and direct user-selected local installers should use the same verified install queue and runtime installation manager.
- The term **电脑应用商城** here means the Future Technology built-in catalog for supported Windows software. Microsoft Store/UWP packages are not assumed compatible merely because ordinary Win32 apps can run under Wine; that needs a separately tested compatibility path.

## Runtime implementation requirements

- Integrate and pin Wine, Box64/Box86, Android/ARM64 native launchers, PRoot/container filesystem, Windows root filesystem, Mesa/Turnip or supported graphics path, DXVK/VKD3D where appropriate, audio/input bridges, and process/session lifecycle from compatible upstream sources.
- Build native components for supported Android ABIs and package all required data/assets under the app-private sandbox. Verify upstream licences and notices.
- Implement a runtime manifest with signed file hashes, versioning, dependency graph, architecture, and rollback behavior. Do not enable the runtime merely because a manifest file exists.
- Build a real process manager for install, launch, foreground/window focus, stop, crash recovery, logs, and clean container shutdown.
- Build a Windows desktop shell with app grid, icons, shortcuts, context actions, app library, installer progress, and per-app settings. The Android launcher icon is not a substitute for this Windows desktop.
- Unsupported software must show a compatibility reason, not silently install or crash the whole environment.

## Memory and device policy

- No permanent 1.0/1.12/1.5 GB hard cap. Implement automatic, performance-priority, and system-priority memory policies.
- Automatic mode should use live ActivityManager.MemoryInfo, per-process pressure and container demand, while preserving Android/system headroom. Support multi-gigabyte runtime use when the device has enough physical RAM and available memory.
- Performance-priority may consume more available memory but must still react to Android low-memory pressure; it may not disable Android safety mechanisms.
- System-priority reduces background cache and pauses nonessential container tasks before the foreground Windows app is terminated.
- Warn when device RAM, GPU, Vulkan, thermal state, or storage is insufficient for a selected app. Do not claim compatibility from RAM alone.

## Account and administration gate

- First-use account screen must clearly offer registration first and separate manual login for existing accounts. Registration must not silently log in.
- QQ or WeChat: either one is sufficient. Avatar must accept a local photo selection.
- Account API must be live and verified for register/login/logout, refresh, pending/approved, whitelist, ban/unban, admin password rotation and audit trail before a release APK is distributed.
- Admin APK must use current server credentials and server-side authorization. No secrets or admin bootstrap password in source, APK or logs.

## Release acceptance tests

A release APK is not ready until all of these pass:

- Clean install on supported OPPO Android 12 and at least one additional Android device; verify Huawei compatibility separately where hardware is available.
- Runtime bootstrap, integrity verification, container create/start/stop/restart, and clean recovery after app force-stop/reboot.
- Install several representative Win32 x86 and x64 apps from the built-in marketplace; confirm automatic detection, correct icon, desktop listing, shortcut creation, app launch, uninstall and reinstall behavior.
- Install a local .exe, .msi, and supported .zip portable app; confirm the same automatic registration workflow.
- Confirm .aep, .aepx, .aex are routed to Ae and are never treated as marketplace software installers.
- Confirm app entries persist across app restarts and container restart; removed apps lose their stale shortcuts.
- Verify app-specific settings, display resizing, keyboard/mouse/touch, audio, file access, network, clipboard and crash isolation.
- Long-running multi-gigabyte memory pressure tests; verify Android remains responsive and foreground app behavior is stable.
- Live account server integration and administrator control tests.
- APK package, signature, permissions, open-source notices, dependency and data-integrity checks.

## Current status (do not misrepresent)

The repository currently contains a development UI, safe Windows installer-package intake/queue and account client/server source. The Windows runtime and catalog backend are not deployed/integrated. Installed-app discovery, desktop shortcuts, and launching are not implemented and have not passed device tests. Therefore there is no complete Future Technology release APK yet.

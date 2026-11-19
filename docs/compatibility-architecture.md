# Compatibility architecture

## The actual problem

Desktop-to-mobile compatibility has four independent dimensions:

1. **Container and file format** — can Ae parse the project or plug-in package?
2. **Instruction set** — does the executable contain x86, x86-64, ARM64, or WebAssembly instructions?
3. **Operating-system ABI** — does it depend on Windows PE/Win32, Android/Linux ELF, JNI, graphics, codecs, or other system APIs?
4. **Host application ABI** — does it call the desktop After Effects plug-in API, or an API implemented by the Ae mobile host?

Matching only the CPU architecture is insufficient. A Windows ARM64 DLL is still not an Android shared library; an x86-64 DLL still expects Windows APIs; and a correctly built Android library still needs an interface understood by the host.

## Proposed layered design

```text
Desktop project / plug-in package
             |
             v
   Safe format inspection
             |
       +-----+------+
       |            |
       v            v
 Project parser   Plug-in classifier
       |            |
       v            +---- WASM module + supported Ae ABI
 Canonical IR      |          |
       |            |          v
       |            |     Sandboxed portable runtime
       |            |
       |            +---- Source available
       |            |          |
       |            |          v
       |            |     Rebuild for Android ARM64
       |            |          |
       |            |          v
       |            |     Ae mobile adapter
       |            |
       |            +---- Windows-only binary
       |                       |
       |                       v
       |                Explicit unsupported /
       |                optional emulation research
       v
 Mobile renderer + feature fallbacks
```

## Track A — project interoperability

- Inspect and parse the source project format separately from rendering.
- Normalize supported constructs into a versioned intermediate representation (IR).
- Preserve unknown fields and original source payloads so round-tripping does not discard data the mobile app does not understand.
- Keep stable IDs, rational frame rates, time values, keyframes, expressions, asset references, layer order, blend modes, masks, and effect parameters.
- Resolve external media through Android Storage Access Framework (SAF) and document URI permissions; do not assume desktop absolute file paths exist on the phone.
- Report unsupported effects per layer. One unsupported effect must not prevent the rest of the project from opening.
- Never claim complete fidelity merely because the project file parsed.

## Track B — plug-in interoperability

Recommended order:

1. **Portable source / WASM**: build an agreed, sandboxed effect ABI and expose only supported capabilities.
2. **Source rebuild**: when plug-in source is available, compile an Android ARM64 implementation with the Android NDK and adapt it to the mobile host API.
3. **Compatible reimplementation**: replace a closed desktop effect with a separately implemented equivalent where licensing and behavior allow.
4. **Optional emulation research**: evaluate a Wine + x86 translation stack in an isolated process. Treat it as a separate, high-cost experimental feature, not as the default plug-in loader.

Do not load arbitrary PE files into the app process. Do not silently run untrusted plug-ins.

## Track C — explicit capability negotiation

Every effect should declare:
- required ABI version;
- CPU / binary target;
- required host capabilities;
- GPU backend requirements;
- input and output pixel formats;
- color-space and bit-depth support;
- licensing and provenance;
- optional fallback implementation.

At import time, classify each effect as `native`, `portable`, `rebuild_required`, `fallback_available`, or `unsupported`. Keep the project editable even when an effect cannot render.

## Safety and product boundaries

- The compatibility lab is separate from production APK CI.
- No change here may remove account registration, login, server verification, bans, or trusted-device checks.
- Do not introduce a bypass around server/account validation.
- Preserve the original project import entry and Android `ACTION_VIEW` / `ACTION_SEND` import pathway.
- File inspection must be read-only until a user explicitly chooses to import a file.
- Emulation must be isolated, permission-limited, and disabled by default.

## Milestones

- M0: read-only PE/ELF/WASM/Mach-O architecture inspector and tests.
- M1: versioned project IR schema and loss-preserving import contract.
- M2: desktop project parser adapters with fixture-based tests.
- M3: portable effect ABI specification and sample WASM host contract.
- M4: Android ARM64 native plug-in adapter and capability negotiation.
- M5: only if technically and legally viable, prototype isolated Windows plug-in emulation.
- M6: integrate into production only after independent regression tests and explicit review.


## Future Technology: Windows software installation and desktop

Future Technology is a Windows desktop-software runtime, not an After Effects project importer. The user flow is:

1. Choose a Windows installer (.exe or .msi) or a supported portable-software archive (.zip) from Android storage or a trusted share source.
2. Copy it into the app-private software installer queue. Do not execute the file as an Android process and do not silently install it as an Android APK.
3. Install the software inside a dedicated Windows/Wine container/prefix when the Wine + Box64/Box86 + rootfs and graphics runtime has passed device verification.
4. Discover installed applications from the container's registered launchers/shortcuts and show their names and icons in Future Technology's in-app Windows desktop / software library.
5. Allow the user to create or remove a desktop shortcut for an installed application. A shortcut points to the installed executable and launch configuration, not to the installer file. Tapping it starts the app in the same isolated container. A pinned Android home-screen shortcut may be offered separately and only after explicit user approval.
6. Keep uninstall, app data and compatibility settings scoped to that container. Never modify Android system partitions or require root.

After Effects project formats (.aep/.aepx) and AE plug-in formats (.aex) are not Windows software installers. They must be handled by Ae's project/plugin workflows, not by Future Technology's software installer queue.

### Memory policy

Do not impose a fixed 1.0/1.12/1.5 GB runtime cap. Provide automatic, performance-priority and system-priority policies. In automatic mode, derive runtime allocations from current available RAM and keep Android/system headroom; the actual process may use multiple gigabytes on devices where memory is available. Treat Android low-memory pressure as a dynamic signal, not a reason to permanently cap the process at 1.5 GB. On smaller devices, warn before installation/launch if the live memory state is insufficient rather than silently reducing functionality.

### Current implementation boundary

The current APK can safely store supported installer packages in a private pending-install queue, but the real Wine/Box64/Box86 runtime is not yet integrated. Installation, installed-app discovery, software desktop icons, shortcut creation and actual Windows application launching are not yet functional.

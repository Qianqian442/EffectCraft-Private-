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

# Plug-in compatibility

## Why a Windows plug-in does not run directly on Android

A typical Windows After Effects effect plug-in (.aex) is a Windows PE dynamic library. Even if its CPU instructions are x86-64 or ARM64, it may depend on:
- the Windows loader and Win32 APIs;
- the desktop After Effects plug-in ABI and host callbacks;
- Windows C/C++ runtime libraries;
- DirectX or desktop OpenGL assumptions;
- codecs, file paths, registry keys, or Windows services.

Android native libraries normally use ELF and the Android NDK ABI. JNI and Android lifecycle, permissions, media, graphics, and storage APIs are also distinct. Renaming .aex to .so cannot convert any of these interfaces.

## Compatibility routes

### 1. Portable plug-in format

Prioritize a sandboxed WebAssembly plug-in ABI for image effects, transitions, generators, and analyzers. Define:
- ABI version and manifest;
- pixel buffer format, stride, planes, and color space;
- parameter schema and animation support;
- deterministic time/frame context;
- bounded memory and execution time;
- cancellation, logging, and error reporting;
- optional GPU capability negotiation.

Use a portable WASM runtime such as Wasmtime or wasm3 after platform and license review. Never give arbitrary modules unrestricted filesystem or network access.

### 2. Source rebuild for Android

If the author supplies source and distribution rights, rebuild the effect for Android ARM64 using the Android NDK. Replace Windows-only APIs and adapt the desktop host calls to an Ae mobile plug-in bridge. Source availability alone does not guarantee that the desktop host API maps to mobile.

### 3. Standardized effects API

Investigate OpenFX as an optional interoperability layer. OpenFX is an open visual-effects plug-in standard and the upstream project is BSD-3-Clause. However:
- an OFX plug-in is not the same thing as an Adobe .aex plug-in;
- a host must implement the OFX API;
- CPU/GPU formats and host extensions differ;
- Android builds still need Android-compatible binary artifacts and dependency ports.

Do not claim that adopting OFX instantly makes every commercial plug-in compatible.

### 4. Windows execution compatibility

Prototype Wine + Box64 / FEX / Hangover only as an isolated optional subsystem. Winlator demonstrates that some Windows x86-64 applications can run on Android ARM64 using Wine and CPU translation. This is a proof that the general technique exists, not proof that After Effects desktop plug-ins work inside Ae:
- the Windows program may require a complete desktop host and its plug-in ABI;
- many plug-ins rely on specific Adobe host versions, installers, runtimes, graphics, or licensing;
- CPU translation and graphics translation add cost and device dependencies;
- kernel drivers and hardware-bound components are not available through ordinary user-mode emulation;
- an emulator container is not a drop-in in-process plug-in loader.

The safest experiment is a separate Wine prefix/container and a standalone host test—not loading PE modules into the Ae Android process.

## Required behavior at project import

- Enumerate every referenced plug-in and identify its format, CPU target, operating system, host ABI, and dependencies when possible.
- Never execute a plug-in while scanning a project.
- Show exact compatibility status and reason.
- If unsupported, preserve the effect block and parameters in the project; render a neutral/placeholder result or a user-selected fallback.
- Offer source rebuild or portable replacement only when legally available.
- Never silently discard effect data.

## Licensing

Before vendoring any project, record repository URL, pinned commit/tag, license, required notices, redistribution conditions, and any patent/trademark or third-party dependency terms. Do not bundle proprietary Adobe SDK headers, commercial plug-ins, or redistributable binaries without the necessary rights.

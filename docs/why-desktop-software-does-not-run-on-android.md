# Why desktop Windows software does not simply run on an Android phone

## Summary

A modern Android phone may have plenty of CPU and RAM, yet a Windows desktop application can still fail before its main window appears. Performance is only one issue. The executable format, CPU ABI, operating-system APIs, libraries, graphics stack, application host API, file model, and security model must all line up.

## The six main barriers

### 1. CPU instructions and ABI

Most Android phones use ARM64 (AArch64). Many Windows desktop programs and plug-ins are compiled for x86 or x86-64. An ABI includes more than instruction names: it also includes calling conventions, registers, stack layout, data types, symbol names, and executable format.

Android NDK binaries are packaged as ELF shared objects under ABI-specific directories such as `lib/arm64-v8a/`. Windows programs normally use PE/COFF. A native Android loader cannot treat a Windows PE DLL as an Android ELF library.

Reference: Android NDK ABI documentation — https://developer.android.com/ndk/guides/abis

### 2. Operating-system API

A Windows program may call Win32, NT, registry, COM, GDI, DirectShow, Windows services, and Windows-specific synchronization or file APIs. Android uses the Linux kernel with the Android Bionic C library, Android framework services, Binder, permissions, and different public native APIs.

Wine implements a large subset of Windows APIs; it is a compatibility layer, not a full Windows installation. Whether a program runs depends on the exact APIs and behaviors it uses.

Android's dynamic linker also restricts native libraries from linking to private platform APIs. Apps must use supported NDK/public APIs.

References:
- Wine: https://www.winehq.org/
- Android native linker rules: https://android.googlesource.com/platform/bionic/+/master/android-changes-for-ndk-developers.md

### 3. Runtime libraries and filesystem

Windows programs may require particular MSVC runtimes, .NET components, codecs, fonts, registry entries, installers, and Windows directory structures. Android apps normally have private data directories, scoped storage, and content URIs rather than unrestricted desktop drive letters.

A Linux root filesystem (often glibc-based) can be placed inside an app-managed container. PRoot can translate filesystem and process views without rooting the phone, but it does not turn the Android kernel into a normal desktop Linux kernel.

### 4. GPU and graphics APIs

A Windows application may expect Direct3D 9/10/11/12, desktop OpenGL, CUDA, vendor-specific drivers, or features not exposed by the phone's GPU. Android's primary low-level graphics API is Vulkan; OpenGL ES is also supported.

Potential translation layers:
- DXVK: Direct3D 8/9/10/11 over Vulkan.
- VKD3D-Proton: Direct3D 12 over Vulkan.
- Mesa Turnip: Vulkan driver for supported Qualcomm Adreno GPUs.
- Zink: OpenGL implementation over Vulkan.
- VirGL: virtualized/translated OpenGL path for some environments.

These layers do not add missing hardware capabilities. GPU vendor, driver version, Vulkan extensions, memory bandwidth, and device stability still matter.

References:
- Android Vulkan: https://developer.android.com/games/develop/vulkan/overview
- Android graphics architecture: https://source.android.com/docs/core/graphics/architecture
- DXVK: https://github.com/doitsujin/dxvk
- VKD3D-Proton: https://github.com/HansKristian-Work/vkd3d-proton

### 5. Host application plug-in ABI

An Adobe After Effects plug-in is not just a file with an extension. Effect plug-ins receive selectors through a defined entry point and call After Effects function suites. AEGP plug-ins register hooks and use suites for project, UI, render-queue, and other host functionality.

A Windows .aex can therefore fail even after its x86-64 instructions are translated and its Windows API calls are handled: Ae mobile is not the desktop After Effects host and does not automatically implement the same suites, lifecycle, pixel formats, threading rules, UI callbacks, GPU contexts, or version behavior.

Reference: Adobe After Effects C++ SDK Guide — https://ae-plugins.docsforadobe.dev/

### 6. Android sandbox and kernel restrictions

Android assigns apps separate UIDs and applies a kernel-enforced app sandbox. Modern devices also restrict certain syscalls and private APIs. A compatibility container must work within the app's permissions and security boundary; it cannot assume root, unrestricted ptrace, arbitrary device-node access, or direct access to the host GPU.

Reference: Android Application Sandbox — https://source.android.com/docs/security/app-sandbox

## What each compatibility layer can and cannot solve

| Layer | Can solve | Cannot solve by itself |
|---|---|---|
| Box64 / Box86 | Translate x86-64 / x86 CPU instructions to ARM in supported environments | Win32 APIs, Adobe host APIs, drivers, licensing |
| Wine | Implement many Windows user-mode APIs | Every Windows API, kernel drivers, all app-specific behavior |
| PRoot + rootfs | Present a Linux filesystem/process view and glibc userspace | Replace the Android kernel or remove Android sandbox rules |
| DXVK / VKD3D | Translate supported Direct3D calls to Vulkan | Add missing GPU hardware features or unsupported Vulkan extensions |
| OpenFX host | Support plug-ins built to the OpenFX standard | Load arbitrary Adobe .aex plug-ins unchanged |
| WASM runtime | Run modules compiled to a portable WASM ABI | Automatically translate Windows PE binaries or Adobe SDK callbacks |
| Android ARM64 rebuild | Provide native performance when source is available | Rebuild closed-source software without source/rights |

## Why the Winlator model matters

Winlator demonstrates a practical Android package that brings together a container front-end, Wine, Box86/Box64, PRoot, a Linux userspace/rootfs, Mesa-based graphics drivers, and optional Direct3D translation layers. It is evidence that *some* Windows applications can run on ARM64 Android, not evidence that all desktop programs or After Effects plug-ins can.

The upstream Winlator README lists hardware and graphics prerequisites for its own build and warns that results vary by device. Do not generalize its stated minimum requirements to every Winlator fork or every Windows application.

## Special note on FEX and Hangover

FEX's official FAQ says that FEX as a complete Linux emulation solution does not target Android because of syscall, filesystem, and GPU-device differences. FEXCore may be reusable as a library in other projects, but that is not a ready-made Android app runtime.

Hangover targets x86/x64 Windows applications on AArch64 Linux/Wine. Treat it as a research comparison, not as a drop-in Android runtime.

References:
- FEX FAQ: https://wiki.fex-emu.com/index.php/FAQ
- Hangover: https://github.com/AndreRH/hangover

## Practical conclusion

For Ae, use a native Android editor/rendering core for normal editing. Add an optional, isolated Windows compatibility environment for applications that can run under Wine + Box64/Box86. Separately define a mobile plug-in ABI and implement adapters for project data and effect processing.

Do not promise that every desktop program or plug-in runs. Measure compatibility using named test applications, plug-ins, phone models, GPU driver versions, Android versions, and actual project fixtures.

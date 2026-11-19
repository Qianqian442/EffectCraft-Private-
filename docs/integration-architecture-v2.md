# Ae compatibility runtime — integration architecture v2

## Product goal

Make Ae able to import and preserve as much desktop project/plugin data as possible, provide native Android equivalents where feasible, and optionally run compatible Windows applications in an isolated environment. This is a broad-compatibility goal, not a promise of unrestricted execution of every desktop binary.

## Chosen implementation strategy

### Keep three execution domains separate

1. **Ae native domain** — the existing Android editor, timeline, renderer, account client, and server-verification flow. It remains the primary application.
2. **Windows runtime domain** — optional app-private rootfs + Wine + Box64/Box86 process environment. It runs Windows user-mode applications only where dependencies and graphics translation permit.
3. **Portable plug-in domain** — a sandboxed WASM / OpenFX-inspired adapter for effects that can be compiled or authored to a supported cross-platform interface.

Do not load untrusted PE plug-ins directly into the Ae native process.

## Runtime composition for the first Windows-app prototype

```text
Ae Android UI
    |
    +-- Project / plug-in compatibility report
    |
    +-- Windows runtime manager (optional)
             |
             +-- app-private rootfs (glibc userspace)
             |       |
             |       +-- Wine (Win32 APIs)
             |       |
             |       +-- Box64 (x86-64 user-mode translation)
             |       +-- Box86 / 32-bit path (legacy x86)
             |
             +-- PRoot process/filesystem view
             |
             +-- Display + input + audio bridge
             |
             +-- Graphics selection
                      |
                      +-- Mesa Turnip where supported
                      +-- Zink / VirGL where applicable
                      +-- DXVK (D3D8/9/10/11 -> Vulkan)
                      +-- VKD3D-Proton (D3D12 -> Vulkan)
```

This architecture follows the practical integration pattern demonstrated by Winlator. It is not a claim that every listed component is suitable for every Android device.

## Why we do not make FEX the primary Android engine

FEX's official FAQ explicitly says the full FEX Linux environment is not an Android target because Android restricts syscalls, differs in filesystem layout, and does not expose a desktop Linux GPU device in the expected way. FEXCore may be evaluated as an isolated translation-library experiment, but it must not be a default runtime dependency until a real Android target and tests exist.

Hangover is a useful alternative architecture to study for ARM64 Linux + Wine. It is not currently a drop-in Android implementation. Keep both in the research tier.

## Project import pipeline

- Accept Android `ACTION_VIEW` and `ACTION_SEND`, including QQ's `content://` URI providers and temporary read grants.
- Stage content in app-private storage or stream it safely; never assume a URI can be converted to a normal path.
- Detect format by magic/version, not just file extension.
- Parse project metadata without executing scripts or plug-ins.
- Convert supported structures to versioned project IR while preserving original source chunks and unknown fields.
- Relink missing desktop footage through Android SAF.
- Produce a per-layer compatibility report. Unsupported plug-ins must not make the project disappear or prevent editable portions from opening.

## Plug-in paths

### A. Portable effects

Use a versioned WASM ABI for effects with explicit image-buffer formats, parameters, time, color space, memory, cancellation, and error semantics. Pick one runtime for production after benchmarks; Wasmtime and wasm3 are alternatives, not automatically both bundled.

### B. OpenFX path

OpenFX is an open plug-in standard with its own host API. Implementing an OpenFX host can broaden compatibility for plug-ins that target OpenFX. It does not make Adobe .aex files OpenFX binaries. A compatibility adapter may map a limited set of OpenFX concepts to the Ae renderer.

### C. Source rebuild

When a plug-in's source code and license permit it, build Android ARM64 code and adapt the plug-in's host API to the Ae native/portable ABI.

### D. Windows .aex research

Wine + Box64 can potentially execute some Windows user-mode code in an isolated Windows environment. It does not provide the Adobe After Effects Host API. An .aex still expects Adobe effect selectors, PICA/suite callbacks, and sometimes AEGP, UI, GPU, threading, and render-queue behavior.

A separate plug-in host would need to implement a substantial compatible subset of Adobe's public host ABI, or run a legitimate compatible desktop host in the Wine environment. Neither is a small file-format conversion. Do not copy proprietary Adobe SDK code or redistribute Adobe binaries without the required rights.

## Host bridge proposal

The Windows runtime and Ae editor must communicate out of process. Define a versioned RPC protocol:
- process lifecycle: launch, stop, heartbeat, crash report;
- plug-in/app manifest: binary format, CPU target, ABI, dependencies, feature capabilities;
- render request: dimensions, time, pixel format, color space, parameter block;
- render response: frame handle, error code, execution time;
- control/UI events: only a whitelisted subset;
- cancellation and timeout.

For large pixel data, prefer bounded shared-memory buffers or platform-supported shared memory instead of JSON-encoding frame pixels. All messages must be versioned and validated. The bridge must not expose arbitrary file, shell, network, clipboard, or account-server privileges.

## Device capability detection

At runtime, record (without hard-failing the native Ae editor):
- CPU ABI and available instruction features;
- Android version / API level;
- GPU vendor, renderer and driver version;
- Vulkan availability and extension set;
- memory pressure and available storage;
- display density/orientation and supported input methods.

Select graphics drivers by detected hardware and driver support. Turnip is not a universal GPU driver; it targets supported Adreno hardware. Do not assume all OPPO/Huawei devices use the same GPU.

## Server/account security boundary

The Windows compatibility process is not an identity provider and may not bypass Ae server verification. Keep registration, login, bans, trusted-device checks, and the existing server-verification UI/flow in the native Ae domain. Do not route account validation through Wine, PRoot, a public proxy, or the plugin host.

## Staged acceptance tests

1. Android arm64 device: start/stop rootfs and Wine environment without root.
2. Launch a minimal legal Windows console program and report its exit code.
3. Launch a minimal Win32 GUI test program and verify display/input/audio.
4. Run x86-64 and 32-bit x86 test binaries separately.
5. Exercise D3D9/11/12 test samples against supported Vulkan driver paths.
6. Load an open-source sample OpenFX plug-in in a dedicated host test.
7. Run a WASM sample effect against the Ae plug-in ABI.
8. Import representative project files from supported desktop applications, preserving unknown fields and media relinking.
9. Test one sample effect through the native adapter and report render parity.
10. Attempt selected .aex plug-ins only where licensing permits and where a meaningful test host exists.

Every result must record device model, SoC/GPU, Android API, driver, source revision, licence, test fixture, and observed limitations.

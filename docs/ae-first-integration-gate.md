# Ae-first integration gate — mandatory product priority

## Explicit product decision

After the Windows compatibility runtime has passed its own Android device tests, the **first and only application integration target is Ae (the EffectCraft mobile editor)**.

Do not spend the first integration milestone on arbitrary Windows desktop applications, a launcher catalogue, office software, games, or a generic app store. Prove the actual requirement first:

1. Import real desktop After Effects projects into Ae on Android.
2. Preserve project structure and keep the project editable.
3. Identify and resolve/relink desktop media.
4. Play and preview the imported composition without crashing.
5. Detect desktop AE plug-ins and determine whether each can actually run.
6. Make at least one legally distributable sample plug-in produce a verifiable rendered result through Ae.
7. Keep unsupported plug-in data and parameters in the project; do not silently delete them.

## Integration order

### Gate 0 — Windows runtime independently works on Android

Before touching Ae production code:
- Start/stop the app-private runtime without root.
- Prove a minimal Windows x86-64 console executable runs under Wine + Box64.
- Prove a minimal Win32 GUI sample can display and receive input where the selected Android display backend permits it.
- Record device model, SoC, CPU ABI, Android API level, GPU, Vulkan driver/extensions, RAM, runtime versions, binary hashes and exit results.
- Verify clean shutdown: no owned Wine/Box64/Windows process remains.
- Reject the runtime if it requires weakening SELinux, escaping the Android sandbox, rooting the device, or changing the boot/system image.

### Gate 1 — Project import into Ae (no plug-in execution)

Use real, legally obtained, representative fixtures:
- .aep project files from several desktop After Effects generations where available;
- .aepx XML projects;
- projects with nested compositions, footage, audio, fonts, expressions, masks, mattes, keyframes, time remapping, effects, and proxies;
- projects whose media paths are desktop absolute paths and must be relinked on Android.

Pass criteria:
- QQ ACTION_VIEW / ACTION_SEND / Android content:// URI launch path reaches Ae's existing auto-import workflow.
- Ae opens the import, not merely the home screen.
- The source project is preserved unchanged in import staging.
- Composition/layer IDs, ordering, rational frame rates, durations, keyframes, transforms, parent relationships, effect names/parameters, and unknown source chunks survive conversion.
- Missing media produces a relink workflow rather than a crash.
- Unsupported project blocks are reported and preserved as opaque data.
- Opening an unsupported plug-in block must not crash the entire project.

### Gate 2 — Ae-native project representation and timeline

Map the parser output to Ae's own project model:
- Stable composition and layer IDs.
- Correct source in/out, start time, stretch, frame rate, timebase and duration.
- Nested compositions and parenting.
- Keyframe values, interpolation, temporal/spatial handles and easing.
- Transform, masks, mattes, blend modes, audio and effect parameter values.
- Keep original source-format chunks and round-trip export data.

Run a project round-trip test: import -> save Ae project -> close app -> reopen -> compare structure and timing against the original normalized IR.

### Gate 3 — Plug-in compatibility classification

For every effect referenced by a project, classify without executing it:
- Adobe desktop .aex / Windows PE x86/x64;
- Windows dependent DLLs and runtime dependencies;
- Android ARM64 ELF libraries;
- OpenFX plug-ins;
- WASM modules;
- missing, unknown or unsupported formats.

Produce a user-readable report including:
- effect and plug-in name;
- binary architecture and OS;
- expected host ABI/version;
- required libraries and graphics APIs when detectable;
- proposed route: Android rebuild, OpenFX host adapter, portable WASM implementation, isolated Windows-host research, or unsupported;
- whether the layer can be rendered, substituted, or only preserved.

A PE/.aex header that says x86-64 is not proof that the plug-in is executable on Android. Wine + Box64 can address parts of Windows execution but does not automatically implement Adobe's desktop After Effects Host API.

### Gate 4 — First working plug-in inside Ae

Start with one open-source effect implementation whose license permits use and redistribution:
- build/rebuild for Android ARM64 or a selected portable plug-in ABI;
- adapt parameters, animation time, pixel formats, color space, image buffers and error handling to Ae's renderer;
- run out-of-process if the component uses the Windows runtime;
- never pass Ae account tokens, administrator credentials or server-verification state to the plug-in host.

Measure rendered output against a deterministic reference frame. Verify no crashes, leaks, hangs or corrupt frames.

### Gate 5 — Selected real desktop .aex compatibility experiments

Only after Gates 0–4 pass:
- inspect license and permitted testing/redistribution rights;
- select a small, named, legally usable .aex test set;
- verify its exact After Effects host version, SDK entry points, selectors, suites, dependencies and UI/GPU requirements;
- test it inside the isolated runtime/host bridge;
- report per-plug-in success/failure and reason.

Do not claim universal .aex support based on one test plug-in.

## Architecture boundary

Ae remains a native Android editor. Windows execution is an optional isolated service. A versioned local IPC protocol passes only specific operations:
- enumerate and inspect plug-ins;
- create a plug-in instance;
- set supported parameters;
- submit a bounded frame buffer and composition time;
- request render;
- return frame/error/timing metadata;
- cancel and destroy an instance.

Do not expose arbitrary shell, network, raw storage, account tokens or admin endpoints to a plug-in process. Validate IPC payload sizes and enforce render timeouts.

## Regression protections

The Ae integration must not remove or change:
- the existing project import entry and auto-import-on-launch behavior;
- QQ "Open with another app" import support;
- existing project editing, playback, timeline and export functionality;
- the original export UI and export presets;
- Ae server-verification UI, account registration/login, bans and trusted-device checks;
- current WASM/native plug-in capabilities.

Every integration PR must state which regression suite was run. If an existing feature regresses, the integration is blocked.

## Delivery criteria

Do not call the Windows-to-Ae compatibility work "successful" until:
- at least one real desktop project imports, remains editable, reopens and plays on a real Android device;
- at least one allowed desktop/portable effect plug-in produces a verified frame in Ae;
- unsupported plug-ins remain intact and are reported;
- Android import, playback, export and server-verification regression tests pass.

This is the first integration target after technical feasibility is proven; it is not complete merely because the runtime starts or a project file parses.

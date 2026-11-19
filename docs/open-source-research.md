# Open-source research candidates

Research snapshot: 2026-11-19. This is a candidate list, not a statement that any project has been integrated or validated in Ae.

| Project | Role | Potential use | Important limits |
|---|---|---|---|
| Winlator | Android app combining Wine and Box86/Box64 | Reference implementation for running some Windows x86/x64 applications on Android ARM64 | Application-level compatibility varies; does not prove AE .aex plug-in compatibility |
| Wine | Windows API compatibility layer | Implements portions of Win32/Win64 APIs | Not Windows itself; app-specific APIs and installer/runtime behavior may still fail |
| Box64 | x86-64 to ARM64 user-mode translation | Run some x86-64 Linux/Windows-on-Wine user-mode code on ARM64 | Not a Windows API implementation; CPU translation alone cannot supply Adobe host ABI |
| FEX | x86/x86-64 user-mode emulator for ARM64 Linux | Alternative translation engine worth evaluating | Primary documented target is ARM64 Linux; Android integration requires extra work and testing |
| Hangover | Wine + CPU emulation approach | Research hybrid ARM64 Wine with x86/x64 Windows apps | Project documentation notes architecture/build requirements; not a turnkey Android AE host |
| DXVK | Direct3D 8/9/10/11 over Vulkan | Possible graphics translation dependency inside Wine compatibility environment | Vulkan driver requirements and feature support vary; not a general desktop GPU API converter |
| VKD3D-Proton | Direct3D 12 over Vulkan | Possible D3D12 translation research | Demanding Vulkan/driver requirements; primarily Proton-focused |
| OpenFX | Open visual-effects plug-in standard | A portable host/plugin boundary for effects, if Ae implements an OFX host | Not Adobe .aex; plug-ins need compatible host/API and Android builds |
| Wasmtime | WebAssembly runtime | Sandboxed portable effects runtime candidate | Android support is less tested than major desktop platforms; benchmark and review Android integration |
| wasm3 | Lightweight WebAssembly interpreter | Alternative small runtime candidate for embedded/mobile use | Interpreter performance and supported WASM proposal set need tests |

## Repositories and primary references

- Winlator: https://github.com/winebox64/winlator
- Wine: https://gitlab.winehq.org/wine/wine
- Box64: https://github.com/ptitSeb/box64
- FEX: https://github.com/FEX-Emu/FEX
- Hangover: https://github.com/AndreRH/hangover
- DXVK: https://github.com/doitsujin/dxvk
- VKD3D-Proton: https://github.com/HansKristian-Work/vkd3d-proton
- OpenFX: https://github.com/AcademySoftwareFoundation/openfx
- Wasmtime: https://github.com/bytecodealliance/wasmtime
- wasm3: https://github.com/wasm3/wasm3

## Suggested evaluation order

1. Inspect the project's existing WASM plug-in loader and define a stable Ae plug-in ABI before adding another runtime.
2. Port/rebuild source-available plug-ins to Android ARM64.
3. Add OpenFX host support only after defining how OFX parameters, image buffers, color management, caching and GPU contexts map to the Ae editor.
4. Evaluate Wine + Box64 or Hangover in a separate sandbox with representative Windows test programs.
5. Test actual public/legal sample .aex plug-ins only after identifying the exact SDK/host/API expectations. Never use proprietary plug-ins without a valid license.
6. Only then decide whether an opt-in Windows plug-in container is practical on Android devices.

## Non-goal

Do not promise that every desktop application or plug-in can run natively on every phone. This requires impossible uniformity across CPU, Android version, GPU, drivers, host APIs, RAM/storage, licensing, and vendor-specific dependencies. Aim instead for broad support with precise capability reporting and safe fallback behavior.

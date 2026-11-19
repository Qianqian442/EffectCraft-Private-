# Ae Mobile Compatibility Lab

This repository is an isolated engineering workspace for researching how desktop Windows projects and plug-ins can be imported, inspected, adapted, or safely substituted on Android ARM64.

It is deliberately separate from the production APK build repository. Experimental compatibility work here must not silently change the shipping app or remove its existing features.

## Goals

- Separate project-file compatibility from executable plug-in compatibility.
- Inspect binary formats and CPU targets without loading or executing untrusted binaries.
- Define a portable effect ABI that can run on Android, desktop, and other supported targets.
- Provide a route to rebuild plug-ins from source for Android ARM64.
- Define explicit fallback behavior when a desktop-only plug-in cannot run on a phone.
- Build a project-interchange representation that preserves compositions, layers, timing, keyframes, assets, and effect parameters.
- Keep server/account validation outside this compatibility layer; it must not be bypassed or removed.

## Important limitation

A Windows `.aex` / `.dll` plug-in is not made Android-compatible by renaming it, changing its extension, or changing a CPU label. Compatibility may require the original source code, a compatible host API, Android ARM64 binaries, replacement implementations, or a controlled emulation environment. Even CPU emulation alone does not provide the Windows APIs or the After Effects host ABI.

## Current status

The initial milestone is a safe, read-only plug-in binary inspector plus format and architecture specifications. This does **not** claim universal Windows plug-in support and does not yet execute desktop binaries.

## Layout

- `docs/compatibility-architecture.md` — layered compatibility strategy.
- `docs/project-interchange.md` — common project representation and import rules.
- `docs/plugin-compatibility.md` — plug-in paths, ABI, and fallback behavior.
- `docs/open-source-research.md` — candidate open-source compatibility projects and evaluation order.
- `docs/why-desktop-software-does-not-run-on-android.md` — CPU, ABI, OS, graphics, host API and sandbox barriers.
- `docs/integration-architecture-v2.md` — Winlator-based isolated runtime and plug-in integration design.
- `compatibility/sources.lock.json` — upstream repositories pinned to exact commits with license and integration status.
- `tools/validate_sources.py` — source pin/license policy validator.
- `schemas/project-ir.schema.json` — versioned, loss-preserving project interchange contract.
- `tools/inspect_plugin.py` — read-only PE/ELF/WASM/Mach-O/ZIP classifier.
- `tests/` — synthetic header tests; no proprietary plug-ins required.
- `.github/workflows/compatibility-lab.yml` — automated checks for the inspector and schema.

## Run the tests

```bash
python -m unittest discover -s tests -v
python tools/validate_sources.py
python tools/inspect_plugin.py path/to/plugin.aex
```

The inspector only reads headers. It never loads or executes the file. CI checks the parser, tests, schema syntax, and read-only behavior. Passing these checks is not proof that a commercial plug-in or full desktop project works on Android.

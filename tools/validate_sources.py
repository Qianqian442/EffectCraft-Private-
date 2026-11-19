#!/usr/bin/env python3
"""Validate pinned compatibility source records without downloading or executing them."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "compatibility" / "sources.lock.json"
SHA = re.compile(r"^[0-9a-f]{40}$")
ALLOWED_TIERS = {
    "android_reference",
    "core_runtime_candidate",
    "secondary_runtime_candidate",
    "optional_graphics_candidate",
    "portable_plugin_candidate",
    "research_only",
}


def main() -> int:
    data = json.loads(LOCK.read_text(encoding="utf-8"))
    assert data["schemaVersion"] == "1.0"
    assert data["policy"]["runWindowsProgramsOutOfProcess"] is True
    assert data["policy"]["doNotBypassAndroidSandboxOrAeServerVerification"] is True

    seen: set[str] = set()
    for source in data["sources"]:
        for key in ("id", "repo", "commit", "license", "role", "tier", "status"):
            if not source.get(key):
                raise SystemExit(f"{source.get('id', '<unknown>')}: missing {key}")
        if source["id"] in seen:
            raise SystemExit(f"duplicate source id: {source['id']}")
        seen.add(source["id"])
        if not SHA.fullmatch(source["commit"]):
            raise SystemExit(f"{source['id']}: commit must be a full 40-character SHA")
        if "/" not in source["repo"]:
            raise SystemExit(f"{source['id']}: repo must be owner/name")
        if source["tier"] not in ALLOWED_TIERS:
            raise SystemExit(f"{source['id']}: unsupported tier {source['tier']}")

    by_id = {x["id"]: x for x in data["sources"]}
    required = {"winlator", "wine", "box64", "box86", "proot", "dxvk", "vkd3d-proton", "openfx", "wasmtime", "wasm3", "hangover", "fex"}
    missing = sorted(required - set(by_id))
    if missing:
        raise SystemExit("missing source candidates: " + ", ".join(missing))

    if by_id["fex"]["tier"] != "research_only" or by_id["fex"]["status"] != "not_android_target":
        raise SystemExit("FEX must remain research-only until a real Android target is validated.")
    if by_id["hangover"]["tier"] != "research_only":
        raise SystemExit("Hangover must remain research-only until Android support is proven.")
    if by_id["proot"]["license"] != "GPL-2.0":
        raise SystemExit("PRoot GPL licensing must remain explicit for redistribution review.")
    if by_id["openfx"]["license"] != "BSD-3-Clause":
        raise SystemExit("OpenFX license record must remain explicit.")

    print(f"Validated {len(data['sources'])} pinned upstream source records.")
    print("No sources were downloaded, built, loaded, or executed by this validator.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

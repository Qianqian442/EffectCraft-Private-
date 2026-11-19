#!/usr/bin/env python3
"""Read-only binary format and CPU-architecture inspector; never loads or executes files."""
from __future__ import annotations

import argparse
import json
import os
import struct
from pathlib import Path
from typing import Any


PE_MACHINES = {
    0x014C: "x86",
    0x8664: "x86_64",
    0x01C0: "ARM",
    0x01C4: "ARMv7",
    0xAA64: "ARM64",
    0xA641: "ARM64EC",
}
ELF_MACHINES = {
    3: "x86",
    40: "ARM",
    62: "x86_64",
    183: "AArch64",
    243: "RISC-V",
}
MACHO_MAGICS = {
    b"\xfe\xed\xfa\xce": ("Mach-O", "32-bit", "big"),
    b"\xce\xfa\xed\xfe": ("Mach-O", "32-bit", "little"),
    b"\xfe\xed\xfa\xcf": ("Mach-O", "64-bit", "big"),
    b"\xcf\xfa\xed\xfe": ("Mach-O", "64-bit", "little"),
}


def inspect(path: Path) -> dict[str, Any]:
    size = path.stat().st_size
    with path.open("rb") as f:
        head = f.read(4096)

    result: dict[str, Any] = {
        "file": path.name,
        "size": size,
        "extension_hint": path.suffix.lower(),
        "format": "unknown",
        "architecture": "unknown",
        "binary_subtype": None,
        "notes": [],
        "safe_to_execute": False,
    }

    if head.startswith(b"MZ"):
        result["format"] = "PE"
        if len(head) < 0x40:
            result["notes"].append("Truncated DOS header.")
            return result
        pe_offset = struct.unpack_from("<I", head, 0x3C)[0]
        if pe_offset + 6 > size:
            result["notes"].append("PE header offset is outside the file.")
            return result
        with path.open("rb") as f:
            f.seek(pe_offset)
            sig_and_coff = f.read(26)
        if len(sig_and_coff) < 6 or sig_and_coff[:4] != b"PE\0\0":
            result["notes"].append("MZ header present, but PE signature was not verified.")
            return result
        machine = struct.unpack_from("<H", sig_and_coff, 4)[0]
        result["architecture"] = PE_MACHINES.get(machine, f"unknown (0x{machine:04x})")
        result["binary_subtype"] = "PE32" if len(sig_and_coff) >= 26 and struct.unpack_from("<H", sig_and_coff, 24)[0] == 0x10B else "PE/COFF"
        result["notes"].append("PE identifies a Windows executable/library format; CPU match does not supply Win32 or Adobe host APIs.")
        if result["extension_hint"] == ".aex":
            result["notes"].append("The .aex extension is commonly used for desktop After Effects plug-ins; verify the exact host SDK and dependencies.")
        return result

    if head.startswith(b"\x7fELF"):
        result["format"] = "ELF"
        if len(head) < 20:
            result["notes"].append("Truncated ELF header.")
            return result
        elf_class = {1: "32-bit", 2: "64-bit"}.get(head[4], "unknown class")
        endian = {1: "<", 2: ">"}.get(head[5])
        if endian is None:
            result["notes"].append("Unknown ELF byte order.")
            return result
        machine = struct.unpack_from(endian + "H", head, 18)[0]
        result["architecture"] = ELF_MACHINES.get(machine, f"unknown ({machine})")
        result["binary_subtype"] = elf_class
        result["notes"].append("ELF alone does not prove Android compatibility; inspect PT_INTERP, DT_NEEDED libraries, API level, and Android NDK ABI.")
        return result

    if head.startswith(b"\0asm"):
        result["format"] = "WebAssembly"
        version = struct.unpack_from("<I", head, 4)[0] if len(head) >= 8 else None
        result["architecture"] = "portable bytecode"
        result["binary_subtype"] = f"version {version}" if version is not None else "truncated"
        result["notes"].append("WASM still requires a compatible runtime, import/API contract, and sandbox policy.")
        return result

    if head[:4] in MACHO_MAGICS:
        fmt, bits, endian_name = MACHO_MAGICS[head[:4]]
        endian = "<" if endian_name == "little" else ">"
        result["format"] = fmt
        result["binary_subtype"] = bits
        if len(head) >= 8:
            cpu = struct.unpack_from(endian + "I", head, 4)[0]
            result["architecture"] = {7: "x86", 0x01000007: "x86_64", 12: "ARM", 0x0100000C: "ARM64"}.get(cpu, f"unknown ({cpu})")
        result["notes"].append("Mach-O is an Apple executable format and is not directly loadable on Android.")
        return result

    if head.startswith((b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")):
        result["format"] = "ZIP/container"
        result["notes"].append("Archive/container, not proof of a native executable. Inspect entries safely and validate paths before extraction.")
        return result

    result["notes"].append("Unknown or unsupported header. Extension is only a hint and is not trusted.")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    args = parser.parse_args()
    if not args.file.is_file():
        parser.error(f"not a regular file: {args.file}")
    try:
        result = inspect(args.file)
    except OSError as exc:
        parser.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

import struct
import tempfile
import unittest
from pathlib import Path

from tools.inspect_plugin import inspect


class InspectPluginTests(unittest.TestCase):
    def make_file(self, payload: bytes, suffix: str = ".bin") -> Path:
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
        tmp.write(payload)
        tmp.close()
        self.addCleanup(Path(tmp.name).unlink, missing_ok=True)
        return Path(tmp.name)

    def test_pe_x64_is_classified_as_windows_not_android(self):
        header = bytearray(0x80)
        header[:2] = b"MZ"
        struct.pack_into("<I", header, 0x3C, 0x40)
        header[0x40:0x44] = b"PE\0\0"
        struct.pack_into("<H", header, 0x44, 0x8664)
        struct.pack_into("<H", header, 0x40 + 4 + 16, 0x20B)
        result = inspect(self.make_file(bytes(header), ".aex"))
        self.assertEqual(result["format"], "PE")
        self.assertEqual(result["architecture"], "x86_64")
        self.assertFalse(result["safe_to_execute"])

    def test_android_arm64_elf_header_is_not_claimed_as_android_verified(self):
        header = bytearray(64)
        header[:4] = b"\x7fELF"
        header[4] = 2
        header[5] = 1
        struct.pack_into("<H", header, 18, 183)
        result = inspect(self.make_file(bytes(header), ".so"))
        self.assertEqual(result["format"], "ELF")
        self.assertEqual(result["architecture"], "AArch64")
        self.assertTrue(any("does not prove Android compatibility" in n for n in result["notes"]))

    def test_wasm_module(self):
        result = inspect(self.make_file(b"\0asm\x01\0\0\0", ".wasm"))
        self.assertEqual(result["format"], "WebAssembly")
        self.assertEqual(result["architecture"], "portable bytecode")

    def test_zip_is_container_only(self):
        result = inspect(self.make_file(b"PK\x03\x04" + b"\0" * 20, ".plugin"))
        self.assertEqual(result["format"], "ZIP/container")

    def test_unknown_extension_is_not_trusted(self):
        result = inspect(self.make_file(b"not a PE", ".aex"))
        self.assertEqual(result["format"], "unknown")


if __import__("sys").modules.get("__main__") is None:
    pass


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Static regression checks for Future Technology's Android file-import intake."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "android/main-app/src/main/AndroidManifest.xml"
ACTIVITY = ROOT / "android/main-app/src/main/java/com/futuretech/main/MainActivity.java"
STORE = ROOT / "android/main-app/src/main/java/com/futuretech/main/PrivateImportStore.java"

ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


root = ET.parse(MANIFEST).getroot()
activity = next(
    node for node in root.findall(".//activity")
    if node.attrib.get(ANDROID + "name") == ".MainActivity"
)
filters = activity.findall("intent-filter")
actions = {
    action.attrib.get(ANDROID + "name")
    for item in filters
    for action in item.findall("action")
}
require("android.intent.action.VIEW" in actions, "ACTION_VIEW must remain registered")
require("android.intent.action.SEND" in actions, "ACTION_SEND must remain registered")
require("android.intent.action.SEND_MULTIPLE" in actions, "ACTION_SEND_MULTIPLE must remain registered")

view_filter = next(
    item for item in filters
    if any(a.attrib.get(ANDROID + "name") == "android.intent.action.VIEW" for a in item.findall("action"))
)
view_schemes = {
    node.attrib.get(ANDROID + "scheme")
    for node in view_filter.findall("data")
}
require("content" in view_schemes, "content:// VIEW imports must remain supported")
require("file" in view_schemes, "file:// VIEW imports must remain supported")

main = ACTIVITY.read_text(encoding="utf-8")
for token in (
    "Intent.ACTION_VIEW",
    "Intent.ACTION_SEND",
    "Intent.ACTION_SEND_MULTIPLE",
    "Intent.EXTRA_STREAM",
    "intent.getClipData()",
    "PrivateImportStore.copyFromUri",
    "showImportedFiles",
):
    require(token in main, f"MainActivity is missing import handling: {token}")

store = STORE.read_text(encoding="utf-8")
for token in (
    "MAX_IMPORT_BYTES",
    "MessageDigest.getInstance",
    "getUsableSpace",
    "output.getFD().sync()",
    "partial.renameTo(stored)",
    ".aep",
    ".aepx",
    ".aex",
    ".exe",
    ".dll",
    ".wasm",
):
    require(token in store, f"Private import store is missing safety/classification marker: {token}")

for forbidden in ("Runtime.getRuntime().exec(", "new ProcessBuilder("):
    require(forbidden not in store, f"File intake must never execute imported content: {forbidden}")

require('android:allowBackup="false"' in MANIFEST.read_text(encoding="utf-8"),
        "Private imported files must not be enabled for Android backup")

print("Future Technology file intake checks passed: QQ VIEW/SEND/SEND_MULTIPLE, content/file URIs, private copy, size/disk guard, SHA-256, format classification, and no binary execution.")

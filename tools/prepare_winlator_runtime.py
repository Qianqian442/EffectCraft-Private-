#!/usr/bin/env python3
"""Prepare the pinned Winlator app source as an internal Android library module.

The upstream source is checked out at third_party/winlator (recursive submodules).
Only a generated copy is transformed; the pinned upstream submodule is never edited.
"""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM_APP = ROOT / "third_party" / "winlator" / "app"
DEST = ROOT / "android" / "winlator-runtime"
MANIFEST = UPSTREAM_APP / "src" / "main" / "AndroidManifest.xml"

if not (UPSTREAM_APP / "build.gradle").is_file():
    raise SystemExit("Pinned Winlator app submodule is missing. Checkout submodules recursively.")
if not MANIFEST.is_file():
    raise SystemExit("Pinned Winlator AndroidManifest.xml is missing.")

if DEST.exists():
    shutil.rmtree(DEST)
shutil.copytree(UPSTREAM_APP, DEST, ignore=shutil.ignore_patterns(".git", ".DS_Store"))

gradle_path = DEST / "build.gradle"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = gradle.replace("apply plugin: 'com.android.application'", "apply plugin: 'com.android.library'")
gradle = gradle.replace("        applicationId 'com.winlator'\n", "")
gradle = gradle.replace("        targetSdkVersion 28\n", "")
gradle = gradle.replace("        versionCode 33\n", "")
gradle = gradle.replace('        versionName "11.2"\n', "")
# Library modules cannot own the app's release/minification policy.
start = gradle.find("    buildTypes {")
if start >= 0:
    depth = 0
    end = None
    for i in range(start, len(gradle)):
        if gradle[i] == "{":
            depth += 1
        elif gradle[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end is None:
        raise SystemExit("Cannot safely transform Winlator buildTypes block")
    gradle = gradle[:start] + gradle[end:]
# Keep only the ARM64 native implementation; this is the supported target for this port.
needle = "        minSdkVersion 26\n"
if needle not in gradle:
    raise SystemExit("Unexpected upstream defaultConfig block; refusing unsafe Gradle rewrite")
gradle = gradle.replace(needle, needle + "        ndk { abiFilters 'arm64-v8a' }\n", 1)
gradle_path.write_text(gradle, encoding="utf-8")

# Convert the upstream launcher into an internal activity while retaining the
# original container UI, X server display, input and foreground-service code.
tree = ET.parse(DEST / "src" / "main" / "AndroidManifest.xml")
root = tree.getroot()
root.attrib.pop("package", None)
android = "{http://schemas.android.com/apk/res/android}"
application = root.find("application")
if application is None:
    raise SystemExit("Winlator manifest has no application element")
for attr in ("icon", "roundIcon", "label", "appCategory", "isGame"):
    application.attrib.pop(android + attr, None)
main_activity = None
for activity in list(application.findall("activity")):
    name = activity.attrib.get(android + "name", "")
    if name == "com.winlator.MainActivity":
        main_activity = activity
        for intent_filter in list(activity.findall("intent-filter")):
            activity.remove(intent_filter)
        activity.set(android + "exported", "false")
if main_activity is None:
    raise SystemExit("Winlator internal MainActivity was not found")

# The app already owns its private storage sandbox. Do not prompt for legacy
# shared-storage permissions just to unpack the rootfs under filesDir.
for permission in list(root.findall("uses-permission")):
    if permission.attrib.get(android + "name") in (
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
    ):
        root.remove(permission)

manifest_path = DEST / "src" / "main" / "AndroidManifest.xml"
tree.write(manifest_path, encoding="utf-8", xml_declaration=True)

# RootFSInstaller's upstream entry is called from Winlator MainActivity after
# the old shared-storage permission prompt. Future Technology uses app-private
# storage, so initialize directly without that obsolete permission gate.
main_java = DEST / "src" / "main" / "java" / "com" / "winlator" / "MainActivity.java"
main_text = main_java.read_text(encoding="utf-8")
old = "            if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);"
if old not in main_text:
    raise SystemExit("Winlator MainActivity permission/bootstrap call changed; review before adapting")
main_text = main_text.replace(old, "            RootFSInstaller.installIfNeeded(this);")
main_java.write_text(main_text, encoding="utf-8")

print("Prepared Winlator 11.2 as an internal ARM64 Android library module.")
print("Upstream source remains pinned and untouched; launcher intent removed; rootfs bootstrap uses app-private storage.")

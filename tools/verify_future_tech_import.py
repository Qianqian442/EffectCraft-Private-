#!/usr/bin/env python3
"""Regression checks: Future Technology installs Windows software; Ae imports projects."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "android/main-app/src/main/AndroidManifest.xml"
ACTIVITY = ROOT / "android/main-app/src/main/java/com/futuretech/main/MainActivity.java"
STORE = ROOT / "android/main-app/src/main/java/com/futuretech/main/PrivateImportStore.java"
ICON = ROOT / "android/main-app/src/main/icon/future_tech_icon.jpg.b64"
MARKETPLACE_ICON = ROOT / "android/main-app/src/main/icon/computer_marketplace_icon.webp.b64"
GRADLE = ROOT / "android/main-app/build.gradle"
ANDROID = "{http://schemas.android.com/apk/res/android}"
def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")
root = ET.parse(MANIFEST).getroot()
activity = next(node for node in root.findall(".//activity") if node.attrib.get(ANDROID + "name") == ".MainActivity")
filters = activity.findall("intent-filter")
actions = {a.attrib.get(ANDROID + "name") for f in filters for a in f.findall("action")}
require("android.intent.action.MAIN" in actions, "launcher entry must remain")
require("android.intent.action.VIEW" in actions, "incoming Windows installer share must remain supported")
require("android.intent.action.SEND" in actions, "single Windows installer share must remain supported")
require("android.intent.action.SEND_MULTIPLE" in actions, "multiple installer shares must remain supported")
main = ACTIVITY.read_text(encoding="utf-8")
for token in ("添加电脑软件安装包", "我的电脑软件 / 安装包", "chooseSoftwarePackage", "confirmIncomingSoftware", "software/installers", "PrivateImportStore.copyFromUri", "isWindowsSoftwarePackage", "showSoftwareLibrary"):
    require(token in main, f"Windows software workflow missing: {token}")
require('lower.endsWith(".exe")' in main and 'lower.endsWith(".msi")' in main and 'lower.endsWith(".zip")' in main,
        "only Windows installers and portable archives should be accepted")
require("isAeProjectOrPlugin" in main and "请在 Ae 中导入" in main,
        "AE project/plugin files must be directed to Ae")
for forbidden in ("导入电脑工程", "showImportedFiles", "注册 / 登录账号"):
    require(forbidden not in main, f"ambiguous old label remains: {forbidden}")
require("注册账号（首次使用）" in main and "已有账号？登录" in main,
        "registration and existing-account login must be separate")
require("注册和登录是分开的" in main, "registration must not silently log in")
require("authenticatedThisLaunch = false" in main and "logoutButton.setVisibility(authenticatedThisLaunch ? View.VISIBLE : View.GONE)" in main,
        "no saved session should appear as automatically logged in")
require('getString("memory_policy", "auto")' in main and "按设备余量动态使用，可按需达到数 GB" in main,
        "runtime memory must not be hard-limited to 1.5 GB")
store = STORE.read_text(encoding="utf-8")
for token in ("MAX_IMPORT_BYTES", "MessageDigest.getInstance", "getUsableSpace", "output.getFD().sync()", "partial.renameTo(stored)"):
    require(token in store, f"private package store safety missing: {token}")
for forbidden in ("Runtime.getRuntime().exec(", "new ProcessBuilder("):
    require(forbidden not in main + store, f"imported software must not execute outside the Windows runtime: {forbidden}")
require("logo.setImageResource(R.drawable.future_tech_icon)" in main, "Future Technology launcher identity must remain separate")
gradle = GRADLE.read_text(encoding="utf-8")
require(MARKETPLACE_ICON.is_file() and len(MARKETPLACE_ICON.read_text(encoding="ascii").strip()) > 1000,
        "user-provided computer marketplace icon asset is missing")
require("materializeComputerMarketplaceIcon" in gradle and "computer_marketplace_icon.webp" in gradle,
        "Gradle must decode the computer marketplace icon before compilation")
require(ICON.is_file() and len(ICON.read_text(encoding="ascii").strip()) > 5000, "Future Technology main icon asset is missing")
require("materializeFutureTechIcon" in gradle and "Base64.getDecoder().decode" in gradle, "Gradle must decode the supplied logo before compilation")
print("Future Technology checks passed: Windows installer inbox, Ae project separation, registration-first UI, manual login, dynamic multi-GB memory policy, safe package copying and separate marketplace icon asset.")

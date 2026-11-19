#!/usr/bin/env python3
"""Regression checks: Future Technology installs Windows software; Ae imports projects."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "android/main-app/src/main/AndroidManifest.xml"
ACTIVITY = ROOT / "android/main-app/src/main/java/com/futuretech/main/MainActivity.java"
MARKETPLACE_ACTIVITY = ROOT / "android/main-app/src/main/java/com/futuretech/main/ComputerMarketplaceActivity.java"
WORKER = ROOT / "server/cloudflare/src/index.js"
SCHEMA = ROOT / "server/cloudflare/schema.sql"
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
require('button("打开电脑应用商城"' in main, "main app must expose the computer marketplace")
require('android:name=".ComputerMarketplaceActivity"' in MANIFEST.read_text(encoding="utf-8"),
        "marketplace activity must be registered")
marketplace = MARKETPLACE_ACTIVITY.read_text(encoding="utf-8")
for token in ("电脑应用商城", "/marketplace/catalog", "DownloadManager", "setDestinationUri",
              "SHA-256", "expectedBytes", "STATUS_SUCCESSFUL", "computer_marketplace_icon"):
    require(token in marketplace, f"marketplace/download verification missing: {token}")
require("当前 Windows 兼容安装引擎尚未完成集成" in marketplace,
        "download must not be falsely described as a Windows installation")
worker = WORKER.read_text(encoding="utf-8")
schema = SCHEMA.read_text(encoding="utf-8")
require('/api/v1/marketplace/catalog' in worker and 'FROM software_catalog WHERE is_active=1' in worker,
        "catalogue API must only return activated catalogue entries")
for token in ("CREATE TABLE IF NOT EXISTS software_catalog", "redistribution_allowed", "expected_bytes", "sha256", "source_url", "is_active"):
    require(token in schema, f"catalogue database schema missing: {token}")
gradle = GRADLE.read_text(encoding="utf-8")
require(MARKETPLACE_ICON.is_file() and len(MARKETPLACE_ICON.read_text(encoding="ascii").strip()) > 1000,
        "user-provided computer marketplace icon asset is missing")
require("materializeComputerMarketplaceIcon" in gradle and "computer_marketplace_icon.webp" in gradle,
        "Gradle must decode the computer marketplace icon before compilation")
require(ICON.is_file() and len(ICON.read_text(encoding="ascii").strip()) > 5000, "Future Technology main icon asset is missing")
require("materializeFutureTechIcon" in gradle and "Base64.getDecoder().decode" in gradle, "Gradle must decode the supplied logo before compilation")
print("Future Technology checks passed: Windows installer inbox, Ae project separation, registration-first UI, manual login, dynamic multi-GB memory policy, safe package copying, marketplace catalogue API and on-device checksum-verified downloads.")

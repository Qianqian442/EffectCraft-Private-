#!/usr/bin/env python3
"""Prepare the pinned Winlator app source as an internal Android library module.

The upstream source is checked out at third_party/winlator (recursive submodules).
Only a generated copy is transformed; the pinned upstream submodule is never edited.
"""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

import re

def _matching_delimiter(source, opening, left, right):
    """Return the matching delimiter while ignoring Java strings and comments."""
    depth = 0
    state = "code"
    i = opening
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        elif state == "string":
            if ch == "\\":
                i += 1
            elif ch == '"':
                state = "code"
        elif state == "char":
            if ch == "\\":
                i += 1
            elif ch == "'":
                state = "code"
        else:
            if ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == left:
                depth += 1
            elif ch == right:
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    raise SystemExit("Unbalanced Java delimiter while adapting Winlator resource switch")

def _find_switches(source):
    found = []
    pattern = re.compile(r"\bswitch\s*\(")
    for match in pattern.finditer(source):
        paren_open = source.find("(", match.start(), match.end())
        paren_close = _matching_delimiter(source, paren_open, "(", ")")
        brace_open = paren_close + 1
        while brace_open < len(source) and source[brace_open].isspace():
            brace_open += 1
        if brace_open >= len(source) or source[brace_open] != "{":
            continue
        brace_close = _matching_delimiter(source, brace_open, "{", "}")
        found.append((match.start(), paren_open, paren_close, brace_open, brace_close))
    return found

def _top_level_case_labels(body):
    labels = []
    state = "code"
    brace_depth = paren_depth = bracket_depth = 0
    i = 0
    line_start = 0
    while i < len(body):
        if i == line_start and brace_depth == 0 and state == "code":
            line_end = body.find("\n", i)
            if line_end < 0:
                line_end = len(body)
            line = body[i:line_end]
            marker = re.match(r"(\s*)(case\s+([^:\n]+)|default)\s*:", line)
            if marker:
                label_start = i + len(marker.group(1))
                label_end = i + marker.end()
                label_text = marker.group(2).strip()
                labels.append((label_start, label_end, label_text))
                i = label_end
                continue
        ch = body[i]
        nxt = body[i + 1] if i + 1 < len(body) else ""
        if state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        elif state == "string":
            if ch == "\\":
                i += 1
            elif ch == '"':
                state = "code"
        elif state == "char":
            if ch == "\\":
                i += 1
            elif ch == "'":
                state = "code"
        else:
            if ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch == "{":
                brace_depth += 1
            elif ch == "}":
                brace_depth -= 1
            elif ch == "(":
                paren_depth += 1
            elif ch == ")":
                paren_depth -= 1
            elif ch == "[":
                bracket_depth += 1
            elif ch == "]":
                bracket_depth -= 1
        if ch == "\n":
            line_start = i + 1
        i += 1
    return labels

def _strip_switch_breaks(body):
    # These upstream resource-ID switches are UI dispatchers; each break exits
    # the switch, not an inner loop. Remove those terminators after converting
    # the dispatcher to if/else. Nested switch expressions are retained.
    return re.sub(r"\bbreak\s*;", "", body)

def _convert_one_resource_switch(source, switch):
    start, paren_open, paren_close, brace_open, brace_close = switch
    body = source[brace_open + 1:brace_close]
    labels = _top_level_case_labels(body)
    if not any(re.search(r"\bR\.id\.", label[2]) for label in labels):
        return source, False

    expr = source[paren_open + 1:paren_close].strip()
    if not expr:
        raise SystemExit("Empty switch expression while adapting Winlator resource switch")
    groups = []
    pending_labels = []
    for index, (label_start, label_end, label_text) in enumerate(labels):
        next_start = labels[index + 1][0] if index + 1 < len(labels) else len(body)
        case_body = body[label_end:next_start]
        if not re.sub(r"//[^\n]*|/\*[\s\S]*?\*/", "", case_body).strip():
            pending_labels.append(label_text)
            continue
        pending_labels.append(label_text)
        groups.append((pending_labels, _strip_switch_breaks(case_body)))
        pending_labels = []
    if pending_labels:
        groups.append((pending_labels, ""))

    lines = ["{", "    final int __futureTechResourceSwitch = (" + expr + ");"]
    emitted_default = False
    emitted_condition = False
    for case_labels, case_body in groups:
        default_case = any(value == "default" for value in case_labels)
        if default_case:
            lines.append("    " + ("else " if emitted_condition else "") + "{")
            emitted_default = True
        else:
            comparisons = []
            for value in case_labels:
                if value.startswith("case "):
                    comparisons.append("__futureTechResourceSwitch == (" + value[5:].strip() + ")")
            if not comparisons:
                continue
            prefix = "if" if not emitted_condition else "else if"
            lines.append("    " + prefix + " (" + " || ".join(comparisons) + ") {")
            emitted_condition = True
        if case_body.strip():
            lines.extend("        " + line if line else "" for line in case_body.strip("\n").splitlines())
        lines.append("    }")
    if not emitted_condition and not emitted_default:
        return source, False
    if emitted_default and not emitted_condition:
        raise SystemExit("Invalid default-only resource switch adaptation")
    lines.append("}")
    replacement = "\n".join(lines)
    return source[:start] + replacement + source[brace_close + 1:], True

def _self_test_resource_switch_conversion():
    sample = """class Example {
    void onClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.first:
                first();
                break;
            case R.id.second:
                second();
                break;
            default:
                fallback();
                break;
        }
    }
}"""
    converted = convert_resource_switches(sample)
    if "switch (item.getItemId())" in converted:
        raise SystemExit("Resource switch conversion left the original switch statement")
    if "if (__futureTechResourceSwitch == (R.id.first))" not in converted:
        raise SystemExit("Resource switch conversion did not produce the expected if branch")
    if "else if (__futureTechResourceSwitch == (R.id.second))" not in converted:
        raise SystemExit("Resource switch conversion did not produce the expected else-if branch")
    if "fallback();" not in converted or converted.count("{") != converted.count("}"):
        raise SystemExit("Resource switch conversion produced malformed block structure")

def convert_resource_switches(source):
    # Work from innermost/rightmost switch outward so nested UI dispatchers
    # are rewritten independently without corrupting surrounding blocks.
    while True:
        switches = _find_switches(source)
        target = None
        for candidate in reversed(switches):
            body = source[candidate[3] + 1:candidate[4]]
            if any(re.search(r"\bR\.id\.", label[2])
                   for label in _top_level_case_labels(body)):
                target = candidate
                break
        if target is None:
            return source
        source, changed = _convert_one_resource_switch(source, target)
        if not changed:
            return source


_self_test_resource_switch_conversion()

ROOT = Path(__file__).resolve().parents[1]
# The Winlator parent repo's "app" submodule is the winlator-app repository;
# its actual Android Gradle application module is nested at app/app.
UPSTREAM_APP = ROOT / "third_party" / "winlator" / "app" / "app"
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
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
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
main_text = main_text.replace(old, "            RootFSInstaller.installIfNeeded(this);\n            ensureFutureTechnologyDefaultContainer();")

imports = """import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.WineThemeManager;
import com.winlator.xenvironment.RootFS;
"""
main_text = main_text.replace("import android.os.Bundle;\n", "import android.os.Bundle;\n" + imports)

bootstrap_method = """
    /**
     * Future Technology creates one isolated, app-private Wine container after
     * the runtime root filesystem has been installed. Existing user containers
     * are never modified or deleted.
     */
    private void ensureFutureTechnologyDefaultContainer() {
        Handler handler = new Handler(Looper.getMainLooper());
        final int[] attempts = {0};
        Runnable[] retry = new Runnable[1];
        retry[0] = () -> {
            if (isFinishing() || isDestroyed()) return;
            RootFS root = RootFS.find(this);
            if (!root.isValid()) {
                if (++attempts[0] < 900) handler.postDelayed(retry[0], 1000L);
                return;
            }
            ContainerManager manager = new ContainerManager(this);
            if (!manager.getContainers().isEmpty()) return;
            try {
                JSONObject data = new JSONObject();
                data.put("name", "未来科技默认容器");
                data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
                data.put("envVars", Container.DEFAULT_ENV_VARS);
                data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(this));
                data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
                data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
                data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
                data.put("drives", Container.DEFAULT_DRIVES);
                data.put("hudMode", 0);
                data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
                data.put("box64Preset", Box64Preset.DEFAULT);
                data.put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME);
                manager.createContainerAsync(data, container -> runOnUiThread(() -> {
                    if (container == null) {
                        AppUtils.showToast(this, "默认 Windows 容器创建失败，请检查手机剩余空间");
                        return;
                    }
                    NavigationView navigation = findViewById(R.id.NavigationView);
                    if (navigation != null) {
                        navigation.setCheckedItem(R.id.menu_item_containers);
                        onNavigationItemSelected(navigation.getMenu().findItem(R.id.menu_item_containers));
                    }
                    AppUtils.showToast(this, "兼容环境已初始化，默认 Windows 容器已创建");
                }));
            } catch (Exception error) {
                AppUtils.showToast(this, "默认 Windows 容器初始化失败：" + error.getMessage());
            }
        };
        handler.postDelayed(retry[0], 500L);
    }

"""
marker="    @Override\n    protected void attachBaseContext(Context newBase) {"
if marker not in main_text:
    raise SystemExit("Winlator MainActivity insertion marker not found")
main_text = main_text.replace(marker, bootstrap_method + marker)
main_java.write_text(main_text, encoding="utf-8")

# Android library R.id fields are intentionally non-final. Rewrite upstream
# UI switches that used those fields as case labels into equivalent if/else
# dispatchers; this preserves behavior while allowing library compilation.
converted_files = []
for java_file in (DEST / "src" / "main" / "java").rglob("*.java"):
    original = java_file.read_text(encoding="utf-8")
    adapted = convert_resource_switches(original)
    if adapted != original:
        java_file.write_text(adapted, encoding="utf-8")
        converted_files.append(str(java_file.relative_to(DEST)))
print("Converted resource-ID switch dispatchers:", ", ".join(converted_files))

print("Prepared Winlator 11.2 as an internal ARM64 Android library module.")
print("Upstream source remains pinned and untouched; launcher intent removed; rootfs bootstrap uses app-private storage.")

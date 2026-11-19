#!/system/bin/sh
# Read-only Android capability probe for compatibility testing.
# Works from adb shell or Termux where the required commands are available.
# Does not request root, modify settings, install packages, or read private app data.

set +e

section() { printf '\n===== %s =====\n' "$1"; }

section "Device / OS"
printf 'uname: '; uname -a 2>/dev/null
for key in ro.product.manufacturer ro.product.model ro.board.platform ro.hardware ro.build.version.release ro.build.version.sdk ro.product.cpu.abi ro.product.cpu.abilist; do
  value="$(getprop "$key" 2>/dev/null)"
  [ -n "$value" ] && printf '%s=%s\n' "$key" "$value"
done

section "CPU"
if [ -r /proc/cpuinfo ]; then
  grep -E '^(Processor|model name|Hardware|Features|CPU architecture)[[:space:]]*:' /proc/cpuinfo 2>/dev/null | sort -u | head -n 60
fi

section "Memory and storage"
if command -v free >/dev/null 2>&1; then free -h 2>/dev/null; else cat /proc/meminfo 2>/dev/null | head -n 12; fi
df -h /data 2>/dev/null

section "Graphics / Vulkan"
if command -v vulkaninfo >/dev/null 2>&1; then
  vulkaninfo --summary 2>&1 | head -n 120
else
  echo "vulkaninfo is not installed; Vulkan support is not inferred from this absence."
fi
if command -v getprop >/dev/null 2>&1; then
  for key in ro.hardware.egl ro.opengles.version; do
    value="$(getprop "$key" 2>/dev/null)"
    [ -n "$value" ] && printf '%s=%s\n' "$key" "$value"
  done
fi

section "Runtime commands"
for cmd in proot box64 box86 wine wineserver; do
  if command -v "$cmd" >/dev/null 2>&1; then
    printf '%s=%s\n' "$cmd" "$(command -v "$cmd")"
  else
    printf '%s=not-found\n' "$cmd"
  fi
done

section "Probe safety"
echo "Read-only probe complete. Missing optional tools do not prove that the device lacks the corresponding hardware feature."
exit 0

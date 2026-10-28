#!/usr/bin/env bash
# Applies the same light tuning profile to one or more TVs over adb: animations at half length, so the TV feels quicker.
# Only animation scales change (no picture, sound, network or account settings); --undo puts them back to 1.
# Usage: tools/tune-tv.sh [--undo] <ip:port> [<ip:port> ...]
set -euo pipefail

scale=0.5
if [ "${1:-}" = "--undo" ]; then scale=1.0; shift; fi
[ $# -gt 0 ] || { echo "Usage: tools/tune-tv.sh [--undo] <ip:port> [<ip:port> ...]"; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb not found. See README.md."; exit 1; }

for tv in "$@"; do
  adb connect "$tv" >/dev/null
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb -s "$tv" shell settings put global "$key" "$scale"
  done
  echo "$tv: animation scales set to $scale"
done

cat <<'EOF'

Left for the person at the TV (Google keeps these inside Google Play services, so adb can't set them):
  Settings > Privacy > Ads > Delete advertising ID
  Settings > Privacy > Usage & diagnostics: off
  Settings > Privacy > Location (and Google location accuracy): off
EOF

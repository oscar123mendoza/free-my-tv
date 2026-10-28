#!/usr/bin/env bash
# Applies the same light tuning profile to one or more TVs over adb: animations at half length, so the TV feels quicker.
# Only animation scales change (no picture, sound, network or account settings); --undo puts them back to 1.
# --free-memory also stops the apps you installed (never system apps), but only on a TV showing the Free My TV home
# screen, so nobody loses what they are watching. The apps start again when opened. Android keeps them cached, so run it
# again whenever memory feels tight.
# Usage: tools/tune-tv.sh [--undo] [--free-memory] <ip:port> [<ip:port> ...]
set -euo pipefail

scale=0.5
free=false
while [ $# -gt 0 ]; do
  case "$1" in
    --undo) scale=1.0 ;;
    --free-memory) free=true ;;
    *) break ;;
  esac
  shift
done
[ $# -gt 0 ] || { echo "Usage: tools/tune-tv.sh [--undo] [--free-memory] <ip:port> [<ip:port> ...]"; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb not found. See README.md."; exit 1; }

for tv in "$@"; do
  adb connect "$tv" >/dev/null
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb -s "$tv" shell settings put global "$key" "$scale"
  done
  echo "$tv: animation scales set to $scale"

  if $free; then
    if adb -s "$tv" shell dumpsys activity activities | grep "topResumedActivity" | grep -q "io.freemytv"; then
      stopped=0
      for pkg in $(adb -s "$tv" shell pm list packages -3 | tr -d '\r' | cut -d: -f2); do
        [ "$pkg" = "io.freemytv" ] && continue
        adb -s "$tv" shell am force-stop "$pkg"
        stopped=$((stopped + 1))
      done
      echo "$tv: stopped $stopped apps"
    else
      echo "$tv: something else is on screen, so no apps were stopped (open the Free My TV home screen and run it again)"
    fi
  fi
done

cat <<'EOF'

Left for the person at the TV (Google keeps these inside Google Play services, so adb can't set them):
  Settings > Privacy > Ads > Delete advertising ID
  Settings > Privacy > Usage & diagnostics: off
  Settings > Privacy > Location (and Google location accuracy): off
EOF

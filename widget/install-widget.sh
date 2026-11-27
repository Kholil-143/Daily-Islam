#!/usr/bin/env bash
# Daily Islam — home-screen widget installer.
#
# Copies the native Android AppWidget sources from widget/ into the
# Capacitor-generated android/ project and registers the widget receiver
# and its configuration activity in AndroidManifest.xml.
#
# Run by the GitHub Actions workflow AFTER:
#   npm install && npx cap add android && npx cap sync android
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_MAIN="$ROOT/android/app/src/main"
MANIFEST="$APP_MAIN/AndroidManifest.xml"

if [ ! -f "$MANIFEST" ]; then
  echo "ERROR: $MANIFEST not found. Run 'npx cap add android' first." >&2
  exit 1
fi

echo "Copying widget Java sources..."
mkdir -p "$APP_MAIN/java/com/kholil/dailyislam/widget"
cp "$ROOT"/widget/java/com/kholil/dailyislam/widget/*.java \
   "$APP_MAIN/java/com/kholil/dailyislam/widget/"

echo "Copying widget resources..."
mkdir -p "$APP_MAIN/res/layout" "$APP_MAIN/res/xml" \
         "$APP_MAIN/res/drawable" "$APP_MAIN/res/values" \
         "$APP_MAIN/res/drawable-night" "$APP_MAIN/res/values-night"
cp "$ROOT"/widget/res/layout/*.xml   "$APP_MAIN/res/layout/"
cp "$ROOT"/widget/res/xml/*.xml      "$APP_MAIN/res/xml/"
cp "$ROOT"/widget/res/drawable/*.xml "$APP_MAIN/res/drawable/"
cp "$ROOT"/widget/res/values/*.xml   "$APP_MAIN/res/values/"
# Night (dark-theme) variants: Android picks these automatically when the
# phone is in dark mode, giving the widget its black theme; the default
# values/ set is the white (light) theme.
if [ -d "$ROOT/widget/res/drawable-night" ]; then
  cp "$ROOT"/widget/res/drawable-night/*.xml "$APP_MAIN/res/drawable-night/"
fi
if [ -d "$ROOT/widget/res/values-night" ]; then
  cp "$ROOT"/widget/res/values-night/*.xml "$APP_MAIN/res/values-night/"
fi

echo "Registering widget receiver + config activity in AndroidManifest.xml..."
python3 - "$MANIFEST" <<'PYEOF'
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    xml = f.read()

if "PrayerWidgetProvider" in xml:
    print("Widget already registered; manifest left unchanged.")
    sys.exit(0)

snippet = """
        <!-- Daily Islam home-screen widget -->
        <receiver
            android:name=".widget.PrayerWidgetProvider"
            android:exported="false"
            android:label="@string/widget_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/prayer_widget_info" />
        </receiver>

        <activity
            android:name=".widget.WidgetConfigActivity"
            android:exported="true"
            android:label="@string/widget_config_title">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
"""

anchor = "</application>"
if anchor not in xml:
    sys.exit("ERROR: '</application>' not found in " + path)

xml = xml.replace(anchor, snippet + "    " + anchor, 1)
with open(path, "w", encoding="utf-8") as f:
    f.write(xml)
print("AndroidManifest.xml patched.")
PYEOF

grep -q "PrayerWidgetProvider" "$MANIFEST"
echo "Widget installation complete."

# --- App launcher icon ---------------------------------------------------
# The Capacitor template ships a default launcher icon. Replace it with the
# Daily Islam icon stored (base64, split at 80,000 chars where needed) under
# icon/res/ in this repo: legacy + round icons and the adaptive foreground
# for every density, plus the adaptive background colour (#FFFFFF, the
# icon's own white).
echo "Installing Daily Islam launcher icon..."
ICON_RES="$ROOT/icon/res"
if [ -d "$ICON_RES" ]; then
  for density_dir in "$ICON_RES"/mipmap-*; do
    density="$(basename "$density_dir")"
    mkdir -p "$APP_MAIN/res/$density"
    for b64 in "$density_dir"/*.png.b64; do
      [ -e "$b64" ] || continue
      out="$APP_MAIN/res/$density/$(basename "${b64%.b64}")"
      base64 --decode "$b64" > "$out"
      echo "  $density/$(basename "$out")"
    done
    for first in "$density_dir"/*.png.b64.part-0; do
      [ -e "$first" ] || continue
      target="$(basename "$first" .part-0)"
      out="$APP_MAIN/res/$density/${target%.b64}"
      cat "$density_dir/$target".part-* | base64 --decode > "$out"
      echo "  $density/$(basename "$out")"
    done
  done
  if [ -f "$ICON_RES/values/ic_launcher_background.xml" ]; then
    mkdir -p "$APP_MAIN/res/values"
    cp "$ICON_RES/values/ic_launcher_background.xml" "$APP_MAIN/res/values/"
    echo "  values/ic_launcher_background.xml"
  fi
  echo "Launcher icon installation complete."
else
  echo "WARNING: $ICON_RES not found; default Capacitor icon kept." >&2
fi

# --- App version -------------------------------------------------------------
# The Capacitor template defaults to versionCode 1 / versionName "1.0", which
# is what Settings > App info showed for every release so far. Set the real
# release version here (this script runs after `cap sync`, before the build).
echo "Setting app version (versionName 2.9, versionCode 29)..."
python3 - "$ROOT/android/app/build.gradle" <<'PYEOF'
import sys, re
path = sys.argv[1]
s = open(path).read()
s, n1 = re.subn(r'versionCode\s+\d+', 'versionCode 29', s, count=1)
s, n2 = re.subn(r'versionName\s+"[^"]*"', 'versionName "2.9"', s, count=1)
if n1 != 1 or n2 != 1:
    sys.exit('ERROR: versionCode/versionName not found in ' + path)
open(path, 'w').write(s)
print('app/build.gradle: versionCode 29, versionName "2.9"')
PYEOF

# --- Firebase / Google Sign-In -------------------------------------------
bash "$ROOT/firebase/install-firebase.sh"

# --- Daily ayah & hadith notifications (v2.9) ------------------------------
bash "$ROOT/notify/install-notify.sh"

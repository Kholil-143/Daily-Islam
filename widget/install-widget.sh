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
         "$APP_MAIN/res/drawable" "$APP_MAIN/res/values"
cp "$ROOT"/widget/res/layout/*.xml   "$APP_MAIN/res/layout/"
cp "$ROOT"/widget/res/xml/*.xml      "$APP_MAIN/res/xml/"
cp "$ROOT"/widget/res/drawable/*.xml "$APP_MAIN/res/drawable/"
cp "$ROOT"/widget/res/values/*.xml   "$APP_MAIN/res/values/"

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

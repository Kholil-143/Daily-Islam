#!/usr/bin/env bash
# Daily Islam — daily ayah & hadith notification installer (v2.9).
# Copies the DailyNotify Capacitor plugin + receivers into the generated
# android/ project, registers the plugin in MainActivity and adds the
# receivers + permissions to AndroidManifest.xml.
# Run by widget/install-widget.sh AFTER cap sync (and after the widget
# installer has patched the manifest).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_MAIN="$ROOT/android/app/src/main"
MANIFEST="$APP_MAIN/AndroidManifest.xml"
MAINACT="$APP_MAIN/java/com/kholil/dailyislam/MainActivity.java"

if [ ! -f "$MANIFEST" ] || [ ! -f "$MAINACT" ]; then
  echo "ERROR: generated android project not found. Run 'npx cap add android' first." >&2
  exit 1
fi

echo "Copying DailyNotify Java sources..."
mkdir -p "$APP_MAIN/java/com/kholil/dailyislam/notify"
cp "$ROOT"/notify/java/com/kholil/dailyislam/notify/*.java \
   "$APP_MAIN/java/com/kholil/dailyislam/notify/"

echo "Patching AndroidManifest.xml (permissions + receivers)..."
python3 - "$MANIFEST" <<'PYEOF'
import sys
path = sys.argv[1]
xml = open(path, encoding='utf-8').read()
if 'DailyNotifyReceiver' not in xml:
    perms = ('    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
             '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />\n'
             '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n')
    anchor = '<application'
    if anchor not in xml:
        sys.exit('ERROR: <application not found in ' + path)
    xml = xml.replace(anchor, perms + '    ' + anchor, 1)
    snippet = """
        <!-- Daily ayah & hadith notifications (v2.9) -->
        <receiver
            android:name=".notify.DailyNotifyReceiver"
            android:exported="false" />

        <receiver
            android:name=".notify.DailyNotifyBootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
"""
    if '</application>' not in xml:
        sys.exit('ERROR: </application> not found in ' + path)
    xml = xml.replace('</application>', snippet + '    </application>', 1)
    open(path, 'w', encoding='utf-8').write(xml)
    print('AndroidManifest.xml patched for DailyNotify.')
else:
    print('DailyNotify already in manifest; unchanged.')
PYEOF

echo "Registering DailyNotify plugin in MainActivity..."
python3 - "$MAINACT" <<'PYEOF'
import sys, re
path = sys.argv[1]
s = open(path, encoding='utf-8').read()
if 'DailyNotifyPlugin' in s:
    print('MainActivity already registers DailyNotifyPlugin.')
    sys.exit(0)
if 'import com.getcapacitor.BridgeActivity;' not in s:
    sys.exit('ERROR: BridgeActivity import not found in ' + path)
s = s.replace('import com.getcapacitor.BridgeActivity;',
              'import android.os.Bundle;\n'
              'import com.getcapacitor.BridgeActivity;\n'
              'import com.kholil.dailyislam.notify.DailyNotifyPlugin;', 1)
new_body = ('public class MainActivity extends BridgeActivity {\n'
            '    @Override\n'
            '    public void onCreate(Bundle savedInstanceState) {\n'
            '        registerPlugin(DailyNotifyPlugin.class);\n'
            '        super.onCreate(savedInstanceState);\n'
            '    }\n'
            '}')
s2, n = re.subn(r'public class MainActivity extends BridgeActivity \{\s*\}', new_body, s, count=1)
if n != 1:
    sys.exit('ERROR: default MainActivity class body not found in ' + path)
open(path, 'w', encoding='utf-8').write(s2)
print('MainActivity patched.')
PYEOF

grep -q "DailyNotifyReceiver" "$MANIFEST"
grep -q "DailyNotifyPlugin" "$MAINACT"
echo "DailyNotify installation complete."

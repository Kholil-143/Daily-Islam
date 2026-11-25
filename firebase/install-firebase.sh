#!/usr/bin/env bash
# Daily Islam — Firebase / Google Sign-In installer.
# Copies google-services.json + the fixed signing keystore into the
# Capacitor-generated android/ project, applies the google-services
# Gradle plugin and points debug+release builds at the fixed keystore
# (its SHA-1 is registered in Firebase, which Google Sign-In requires).
# Run by widget/install-widget.sh AFTER cap sync.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/android/app"

if [ ! -d "$APP" ]; then
  echo "ERROR: $APP not found. Run 'npx cap add android' first." >&2
  exit 1
fi

echo "Copying google-services.json..."
cp "$ROOT/firebase/google-services.json" "$APP/google-services.json"

echo "Installing fixed signing keystore..."
base64 --decode "$ROOT/firebase/daily-islam.p12.b64" > "$APP/daily-islam.p12"

echo "Patching Gradle files..."
python3 - "$ROOT" <<'PYEOF2'
import sys, re
root = sys.argv[1]

# --- project-level build.gradle: google-services classpath ---
p = root + '/android/build.gradle'
s = open(p).read()
if 'com.google.gms:google-services' not in s:
    m = re.search(r'(dependencies\s*\{)', s)
    if not m:
        sys.exit('ERROR: dependencies block not found in android/build.gradle')
    s = s[:m.end()] + "\n        classpath 'com.google.gms:google-services:4.4.2'" + s[m.end():]
    open(p, 'w').write(s)
print('android/build.gradle OK')

# --- app-level build.gradle ---
p = root + '/android/app/build.gradle'
s = open(p).read()
if 'com.google.gms.google-services' not in s:
    s = s.rstrip() + "\napply plugin: 'com.google.gms.google-services'\n"
if 'daily-islam.p12' not in s:
    signing = """
    signingConfigs {
        fixed {
            storeFile file('daily-islam.p12')
            storePassword 'dailyislam143'
            keyAlias 'android'
            keyPassword 'dailyislam143'
            storeType 'PKCS12'
        }
    }
"""
    if 'android {' not in s:
        sys.exit('ERROR: android { not found in app/build.gradle')
    s = s.replace('android {', 'android {' + signing, 1)
    # buildTypes: ensure a debug block with our signing config exists,
    # and add the signing config to the release block.
    bt = s.find('buildTypes {')
    if bt < 0:
        sys.exit('ERROR: buildTypes { not found in app/build.gradle')
    region = s[bt:]
    if not re.search(r'\bdebug\s*\{', region):
        s = s[:bt] + 'buildTypes {\n        debug {\n            signingConfig signingConfigs.fixed\n        }' + s[bt+len('buildTypes {'):]
    else:
        s = re.sub(r'(\bdebug\s*\{)', r'\1\n            signingConfig signingConfigs.fixed', s, count=1)
    s = re.sub(r'(\brelease\s*\{)', r'\1\n            signingConfig signingConfigs.fixed', s, count=1)
    open(p, 'w').write(s)
print('android/app/build.gradle OK')
PYEOF2

grep -q "google-services" "$APP/build.gradle" || true
grep -q "daily-islam.p12" "$APP/build.gradle"
echo "Firebase installation complete."

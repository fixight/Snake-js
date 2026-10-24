#!/usr/bin/env bash
set -euo pipefail
# Existing isolated workspace: use its checkouts, do not create worktrees.
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME=/workspace/tools/android-sdk
export ANDROID_USER_HOME=/workspace/.android
export GRADLE_USER_HOME=/workspace/.gradle
mkdir -p /workspace/tools "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
export JAVA_HOME=/workspace/tools/jdk-17.0.21+9
if [[ ! -x "$JAVA_HOME/bin/jlink" ]]; then
  curl -fLsS 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz' -o /workspace/tools/jdk17.tar.gz
  curl -fLsS 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz.sha256.txt' -o /workspace/tools/jdk17.sha256
  python3 - <<'PYJDK'
import hashlib
assert hashlib.sha256(open('/workspace/tools/jdk17.tar.gz','rb').read()).hexdigest()==open('/workspace/tools/jdk17.sha256').read().split()[0]
PYJDK
  tar -xzf /workspace/tools/jdk17.tar.gz -C /workspace/tools
fi
export PATH="$JAVA_HOME/bin:$PATH"
# Use the environment-provided system CA store, including its HTTPS proxy CA.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
proxy_args=()
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  mapfile -t proxy_parts < <(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
if p.username or p.password: raise SystemExit('Authenticated proxy requires supported Java credential configuration')
print(p.hostname or ''); print(p.port or 80)
PY
)
  [[ ${#proxy_parts[@]} == 2 && -n ${proxy_parts[0]} ]]
  proxy_args=(--proxy=http "--proxy_host=${proxy_parts[0]}" "--proxy_port=${proxy_parts[1]}")
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttps.proxyHost=${proxy_parts[0]} -Dhttps.proxyPort=${proxy_parts[1]} -Dhttp.proxyHost=${proxy_parts[0]} -Dhttp.proxyPort=${proxy_parts[1]}"
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fLsS https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /workspace/tools/cmdline.zip
  curl -fLsS https://dl.google.com/android/repository/repository2-1.xml -o /workspace/tools/repository.xml
  python3 - <<'PY'
import hashlib,xml.etree.ElementTree as E
for a in E.parse('/workspace/tools/repository.xml').getroot().iter('archive'):
    u=a.find('complete/url')
    if u is not None and u.text=='commandlinetools-linux-11076708_latest.zip':
        assert hashlib.sha1(open('/workspace/tools/cmdline.zip','rb').read()).hexdigest()==a.find('complete/checksum').text
        break
else: raise SystemExit('Pinned tools checksum unavailable in official repository metadata')
PY
  mkdir -p "$ANDROID_HOME/cmdline-tools" /workspace/tools/cmdline-unpack
  unzip -q /workspace/tools/cmdline.zip -d /workspace/tools/cmdline-unpack
  mv /workspace/tools/cmdline-unpack/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
fi
python3 - "${proxy_args[@]}" <<'PY'
import os,subprocess,sys
b=[os.environ['ANDROID_HOME']+'/cmdline-tools/latest/bin/sdkmanager','--sdk_root='+os.environ['ANDROID_HOME']]+sys.argv[1:]
subprocess.run(b+['--licenses'],input='y\n'*100,text=True,check=True)
subprocess.run(b+['platforms;android-35','build-tools;35.0.0','ndk;27.2.12479018','cmake;3.22.1','platform-tools'],check=True)
PY
cd "$project_dir"
./scripts/test-host.sh
./gradlew --no-daemon assembleDebug lintDebug

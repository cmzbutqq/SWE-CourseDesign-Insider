#!/usr/bin/env sh
set -eu

if [ -n "${MAVEN_MIRROR_URL:-}" ]; then
  cat >/tmp/maven-settings.xml <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>custom-mirror</id>
      <name>Custom Mirror</name>
      <url>${MAVEN_MIRROR_URL}</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF
  set -- -s /tmp/maven-settings.xml "$@"
fi

exec mvn "$@"

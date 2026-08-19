#!/bin/sh
# Maven Wrapper script. Downloads Maven 3.9.11 on first use.
set -e
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.11
MAVEN_HOME="$BASE_DIR/.mvn/wrapper/apache-maven-$MAVEN_VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  ARCHIVE="$BASE_DIR/.mvn/wrapper/apache-maven-$MAVEN_VERSION-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip" -o "$ARCHIVE"; else wget -q "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip" -O "$ARCHIVE"; fi
  fi
  unzip -q "$ARCHIVE" -d "$BASE_DIR/.mvn/wrapper"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"

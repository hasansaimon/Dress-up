#!/bin/sh
# Minimal Gradle wrapper shim that ensures Gradle 8.4 is available locally and runs it.
# This avoids needing gradle-wrapper.jar in the repo while providing reproducible Gradle 8.4 builds.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_DIR="$DIR/.gradle-wrapper"
GRADLE_VERSION="8.4"
GRADLE_DIR="$WRAPPER_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME not set. Make sure JDK is available."
fi

if [ ! -x "$GRADLE_BIN" ]; then
  echo "Gradle $GRADLE_VERSION not found. Downloading..."
  mkdir -p "$WRAPPER_DIR"
  cd "$WRAPPER_DIR"
  ZIP="gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -sSL "https://services.gradle.org/distributions/$ZIP" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "https://services.gradle.org/distributions/$ZIP" -O "$ZIP"
    else
      echo "curl or wget required to download Gradle" >&2
      exit 2
    fi
  fi
  unzip -q "$ZIP"
fi

exec "$GRADLE_BIN" "$@"

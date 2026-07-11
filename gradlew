#!/usr/bin/env sh
set -eu
VERSION=8.10.2
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$VERSION-bin/manual"
ZIP="$BASE/gradle-$VERSION-bin.zip"
HOME_DIR="$BASE/gradle-$VERSION"
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  mkdir -p "$BASE"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $VERSION..."
    if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"; else wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"; fi
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$HOME_DIR/bin/gradle" "$@"

#!/usr/bin/env bash
# Compiles and runs every version-independent test without Gradle, Minecraft or any download.
# Takes about two seconds and catches the logic bugs that matter.
set -e
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
# Excluded: the files that need Gson or Minecraft.
SRCS=$(find src/main/java -name '*.java' \
  ! -name 'ProgressionParser.java' \
  ! -name 'BossStore.java' \
  ! -name 'PriceStore.java' \
  ! -path '*/client/*' \
  ! -path '*/mixin/*' \
  ! -path '*/data/*')
javac -d "$OUT" -encoding UTF-8 $SRCS src/test/java/minerefinehud/*.java
# "$@" rather than "${1:-}": the latter passes an empty string when no data.js is given, which
# CoreTests then tries to read as a file and crashes on.
echo "=== core ===";   java -cp "$OUT" minerefinehud.CoreTests "$@"
echo; echo "=== shop ===";   java -cp "$OUT" minerefinehud.ShopTests
echo; echo "=== layout ==="; java -cp "$OUT" minerefinehud.LayoutTests
echo; echo "=== progress ==="; java -cp "$OUT" minerefinehud.ProgressTests

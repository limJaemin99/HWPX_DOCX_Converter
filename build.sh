#!/usr/bin/env bash
# JDK만으로 빌드·시험 (Maven 없이). Windows는 build.ps1
#
#   bash build.sh          빌드 + 시험
#   bash build.sh build    컴파일 → jar → 시험 클래스 컴파일 (JDK 9 이상)
#   bash build.sh test     만들어 둔 jar로 시험만 (JDK 8 이상)
#   bash build.sh clean
#
# JAVA_HOME이 있으면 그 JDK를, 없으면 PATH의 java·javac를 씀. 결과는 build/
set -euo pipefail
cd "$(dirname "$0")"

NAME=hwpx-docx-converter
MAIN=io.github.limjaemin99.hwpxdocx.cli.Main
RUNNER=io.github.limjaemin99.hwpxdocx.testkit.TestRunner
# parent가 없으므로 pom.xml의 첫 <version>이 프로젝트 버전
VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)
OUT=build
JAR="$OUT/libs/$NAME-$VERSION.jar"

SEP=':'
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) SEP=';' ;; esac

tool() {
  if [ -n "${JAVA_HOME:-}" ]; then echo "$JAVA_HOME/bin/$1"; else echo "$1"; fi
}

do_clean() {
  rm -rf "$OUT"
}

do_build() {
  rm -rf "$OUT/classes" "$OUT/test-classes" "$OUT/libs"
  mkdir -p "$OUT/classes/META-INF" "$OUT/test-classes" "$OUT/libs"

  find src/main/java -name '*.java' | sort > "$OUT/main-sources.txt"
  "$(tool javac)" --release 8 -encoding UTF-8 -Xlint:all,-options \
    -d "$OUT/classes" @"$OUT/main-sources.txt"
  if [ -d src/main/resources ]; then cp -R src/main/resources/. "$OUT/classes/"; fi
  cp LICENSE NOTICE "$OUT/classes/META-INF/"

  cat > "$OUT/MANIFEST.MF" <<EOF
Main-Class: $MAIN
Implementation-Title: $NAME
Implementation-Version: $VERSION
Automatic-Module-Name: io.github.limjaemin99.hwpxdocx
EOF
  "$(tool jar)" cfm "$JAR" "$OUT/MANIFEST.MF" -C "$OUT/classes" .

  find src/test/java -name '*.java' | sort > "$OUT/test-sources.txt"
  "$(tool javac)" --release 8 -encoding UTF-8 -Xlint:all,-options \
    -cp "$JAR" -d "$OUT/test-classes" @"$OUT/test-sources.txt"
  if [ -d src/test/resources ]; then cp -R src/test/resources/. "$OUT/test-classes/"; fi

  echo "jar: $JAR"
}

do_test() {
  if [ ! -f "$JAR" ]; then
    echo "jar가 없음: $JAR (먼저 bash build.sh build)" >&2
    exit 1
  fi
  "$(tool java)" -cp "$JAR$SEP$OUT/test-classes" "$RUNNER" "$OUT/test-classes"
  "$(tool java)" -jar "$JAR" --version
}

case "${1:-all}" in
  all) do_build; do_test ;;
  build) do_build ;;
  test) do_test ;;
  clean) do_clean ;;
  *) echo "사용법: bash build.sh [all|build|test|clean]" >&2; exit 2 ;;
esac

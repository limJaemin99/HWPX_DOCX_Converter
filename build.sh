#!/usr/bin/env bash
# JDK만으로 빌드·시험 (Maven 없이). Windows는 build.ps1
#
#   bash build.sh          빌드 + 시험
#   bash build.sh build    컴파일 → jar → 시험 클래스 컴파일 (JDK 9 이상)
#   bash build.sh check    만들어 둔 jar·src/main에 네트워크 관련 API가 없는지 검사 (JDK 9 이상, jdeps)
#   bash build.sh test     만들어 둔 jar로 시험 + 공개 시험 문서 회귀 시험 (JDK 8 이상)
#   bash build.sh regress <문서 폴더> <기준값 폴더> [결과 폴더]
#                          다른 문서 폴더로 회귀 시험 (저장소 밖 문서도 됨)
#   bash build.sh baseline [문서 폴더 기준값 폴더]
#                          기준값을 지금 수치로 다시 씀 (기본은 공개 시험 문서)
#   bash build.sh clean
#
# JAVA_HOME이 있으면 그 JDK를, 없으면 PATH의 java·javac를 씀. 결과는 build/
set -euo pipefail
cd "$(dirname "$0")"

NAME=hwpx-docx-converter
MAIN=io.github.limjaemin99.hwpxdocx.cli.Main
RUNNER=io.github.limjaemin99.hwpxdocx.testkit.TestRunner
REGRESSION=io.github.limjaemin99.hwpxdocx.regress.Regression
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

# 폐쇄망에서 쓰는 jar라 네트워크 관련 API를 쓰면 실패.
# jdeps는 컴파일된 참조를, 소스 검사는 리플렉션 문자열·getResource(URL을 돌려줌) 같은 우회를 잡음
NET_PACKAGES='^(java\.net|javax\.net|jdk\.net|com\.sun\.net|sun\.net|java\.rmi|javax\.rmi|javax\.naming)(\.|$)'
NET_SOURCE='java\.net|javax\.net|jdk\.net|jdk\.httpserver|com\.sun\.net|sun\.net|java\.rmi|javax\.rmi|javax\.naming|URLClassLoader|\bgetResources?\(|\btoURI\(|\btoURL\('

do_check() {
  if [ ! -f "$JAR" ]; then
    echo "jar가 없음: $JAR (먼저 bash build.sh build)" >&2
    exit 1
  fi
  local bad=0 deps hits
  deps=$("$(tool jdeps)" -verbose:package "$JAR" | awk '$2 == "->" { print $1 " -> " $3 }')
  hits=$(echo "$deps" | awk '{ print $3 }' | grep -E "$NET_PACKAGES" || true)
  if [ -n "$hits" ]; then
    echo "jar가 네트워크 관련 패키지를 씀:" >&2
    echo "$deps" | grep -E -- "-> ($(echo "$hits" | sort -u | paste -sd'|' -))$" >&2
    bad=1
  fi
  hits=$(grep -rnE "$NET_SOURCE" src/main || true)
  if [ -n "$hits" ]; then
    echo "src/main에 네트워크 관련 이름이 있음:" >&2
    echo "$hits" >&2
    bad=1
  fi
  if [ "$bad" -ne 0 ]; then exit 1; fi
  echo "네트워크 검사 통과: $(echo "$deps" | awk '{ print $3 }' | sort -u | wc -l)개 패키지 참조, 금지 패키지 없음"
}

do_test() {
  if [ ! -f "$JAR" ]; then
    echo "jar가 없음: $JAR (먼저 bash build.sh build)" >&2
    exit 1
  fi
  "$(tool java)" -cp "$JAR$SEP$OUT/test-classes" "$RUNNER" "$OUT/test-classes"
  "$(tool java)" -cp "$JAR$SEP$OUT/test-classes" "$REGRESSION" \
    --docs "$OUT/test-classes/docs" --baseline "$OUT/test-classes/baseline"
  "$(tool java)" -jar "$JAR" --version
}

do_regress() {
  if [ ! -d "$OUT/test-classes" ]; then
    echo "시험 클래스가 없음 (먼저 bash build.sh build)" >&2
    exit 1
  fi
  "$(tool java)" -cp "$JAR$SEP$OUT/test-classes" "$REGRESSION" "$@"
}

case "${1:-all}" in
  all) do_build; do_check; do_test ;;
  build) do_build ;;
  check) do_check ;;
  test) do_test ;;
  regress)
    if [ $# -lt 3 ]; then echo "사용법: bash build.sh regress <문서 폴더> <기준값 폴더> [결과 폴더]" >&2; exit 2; fi
    do_regress --docs "$2" --baseline "$3" ${4:+--out "$4"} ;;
  baseline) do_regress --docs "${2:-src/test/resources/docs}" --baseline "${3:-src/test/resources/baseline}" --update ;;
  clean) do_clean ;;
  *) echo "사용법: bash build.sh [all|build|check|test|regress|baseline|clean]" >&2; exit 2 ;;
esac

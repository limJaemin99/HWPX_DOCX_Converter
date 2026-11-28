#!/usr/bin/env bash
# tools/docgen의 .fodt를 LibreOffice로 DOCX로 바꾸고 문서 저장 정보를 비운다.
# 공개 시험 문서는 이걸로 한 번 만들어 저장소에 고정한다 (CI마다 다시 만들지 않음).
# 사용: bash tools/docgen/make-docx.sh <결과 폴더>
set -euo pipefail
out=$1
src=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$out"

profile="file://$(mktemp -d)"
for f in "$src"/*.fodt; do
  name=$(basename "$f" .fodt)
  rm -f "$out/$name.docx"
  timeout 120 soffice -env:UserInstallation="$profile" --headless --norestore \
    --convert-to 'docx:MS Word 2007 XML' --outdir "$out" "$f"
  if [ ! -s "$out/$name.docx" ]; then
    echo "DOCX가 나오지 않음: $name" >&2
    exit 1
  fi
done

python3 "$src/clear-meta.py" "$out"/*.docx
ls -l "$out"

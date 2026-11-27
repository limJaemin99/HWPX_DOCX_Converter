#!/usr/bin/env bash
# 폴더 안 DOCX를 LibreOffice로 PDF로 바꾸고 쪽수를 적는다. 열리지 않거나 PDF가 안 나오면 실패.
# 쪽수는 참고값 (Word와 줄 나눔·글꼴이 달라 실패 조건으로 쓰지 않음).
# 사용: bash tools/libreoffice-pdf.sh <DOCX 폴더> <PDF 폴더> [문서당 제한 초]
set -u
in_dir=$1
out_dir=$2
limit=${3:-120}
mkdir -p "$out_dir"
pages_file="$out_dir/pages.tsv"
printf '# 문서\t결과\t쪽수\n' > "$pages_file"

# 실행마다 새 사용자 프로필을 써서 이전 실행의 설정·잠금이 남지 않게 함
profile="file://$(mktemp -d)"
failed=0
count=0
while IFS= read -r f; do
  count=$((count + 1))
  rel=${f#"$in_dir"/}
  name=$(basename "$f" .docx)
  sub="$out_dir/$(dirname "$rel")"
  mkdir -p "$sub"
  if timeout "$limit" soffice -env:UserInstallation="$profile" --headless --norestore \
      --convert-to pdf --outdir "$sub" "$f" > "$sub/$name.log" 2>&1 && [ -s "$sub/$name.pdf" ]; then
    pages=$(pdfinfo "$sub/$name.pdf" 2>/dev/null | awk '/^Pages:/ {print $2}')
    printf '%s\tok\t%s\n' "$rel" "${pages:-?}" >> "$pages_file"
  else
    failed=$((failed + 1))
    printf '%s\t실패\t-\n' "$rel" >> "$pages_file"
    echo "PDF로 바꾸지 못함: $rel"
    cat "$sub/$name.log"
  fi
done < <(find "$in_dir" -name '*.docx' | sort)

column -t -s $'\t' "$pages_file"
echo "LibreOffice 변환: 문서 ${count}개, 실패 ${failed}개 (쪽수는 참고값)"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "## LibreOffice 변환: 문서 ${count}개, 실패 ${failed}개"
    echo
    echo "쪽수는 참고값입니다 (Word와 줄 나눔·글꼴이 다름)."
    echo
    echo "| 문서 | 결과 | 쪽수 |"
    echo "|---|---|---|"
    tail -n +2 "$pages_file" | awk -F'\t' '{printf "| %s | %s | %s |\n", $1, $2, $3}'
  } >> "$GITHUB_STEP_SUMMARY"
fi
[ "$count" -gt 0 ] && [ "$failed" -eq 0 ]

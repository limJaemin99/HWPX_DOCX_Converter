# HWPX DOCX Converter

[![CI](https://github.com/limJaemin99/HWPX_DOCX_Converter/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/limJaemin99/HWPX_DOCX_Converter/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/limJaemin99/HWPX_DOCX_Converter?include_prereleases&sort=semver)](https://github.com/limJaemin99/HWPX_DOCX_Converter/releases)
[![License](https://img.shields.io/github/license/limJaemin99/HWPX_DOCX_Converter)](LICENSE)

HWPX(한컴오피스 한글) 문서와 DOCX(Microsoft Word) 문서를 서로 바꾸는 자바 라이브러리입니다.

- 외부 라이브러리 없이 JDK 기본 기능만 씁니다. jar 하나만 넣으면 됩니다
- Java 8 이상에서 돌아갑니다
- 법령·규정처럼 글과 표 위주의 문서를 먼저 지원하고, 글상자·도형이 많은 일반 문서는 그 뒤에 넓힙니다

> 아직 개발 중입니다. 첫 릴리스(v0.1.0) 전까지는 API가 바뀔 수 있습니다.

## 쪽 배치는 같게 맞추지 않습니다

한글과 Word는 줄을 나누는 계산, 줄 간격의 기준, 쓸 수 있는 글꼴이 서로 달라서, 같은 내용이라도 쪽 배치까지 똑같이 맞출 수는 없습니다.
이 라이브러리는 글자·문단·표·서식을 빠짐없이 옮기는 것을 목표로 하고, 쪽수 차이는 작게 줄이는 쪽으로 다룹니다.

## 사용법

> 두 방향 모두 글자·문단, 표, 그림, 구역별 쪽 설정, 머리말·꼬리말, 자동 번호·글머리표, 각주·미주, 책갈피를 옮깁니다. 링크는 HWPX → DOCX만 됩니다. 아래 "지원 범위"를 보세요.

```java
try (InputStream in = Files.newInputStream(Paths.get("in.hwpx"));
     OutputStream out = Files.newOutputStream(Paths.get("out.docx"))) {
    ConvertResult r = HwpxDocxConverter.hwpxToDocx(in, out, ConvertOptions.defaults());
    System.out.println(r);              // 문단 120, 표 3, 그림 1, 건너뜀 0, 경고 0
    System.out.println(r.getSkipped()); // 옮기지 못한 요소와 개수
}
```

- 입력·출력은 스트림이고, 라이브러리가 스트림을 닫지 않습니다
- 정적 상태가 없어 여러 스레드에서 동시에 불러도 됩니다
- 옮길 수 없는 요소는 기본으로 건너뛰고 결과에 남깁니다. `ConvertOptions.builder().failOnUnsupported(true)`로 하면 그 자리에서 멈춥니다
- 깨진 문서는 `ConvertException`(`IOException` 하위)으로 알립니다. 압축을 푼 크기(1 GiB), 패키지 항목 수(10,000개), 표 크기(열 10,000개, 칸 4,000,000개)에 상한이 있어 비정상 문서가 메모리를 다 쓰지 않습니다. 시험 묶음이 속성값을 바꾸거나 XML을 자른 문서 1,000개로 이를 확인합니다
- 문서를 메모리에 올려 바꾸므로 본문 XML 크기의 10배쯤 되는 힙이 필요합니다. 예: 본문 XML 21 MB(압축 0.5 MB)인 DOCX는 힙 192 MB에서 바뀝니다

명령줄 도구도 jar에 들어 있습니다. 확장자로 방향을 정합니다.

```
java -jar hwpx-docx-converter-<버전>.jar 입력.hwpx 출력.docx
java -jar hwpx-docx-converter-<버전>.jar 입력.docx 출력.hwpx
```

종료 코드는 0 성공, 1 변환 실패, 2 사용법 오류입니다. 실패하면 출력 파일을 남기지 않습니다.

## 지원 범위

개발 중이라 기능이 늘어날 때마다 이 표를 고칩니다.

| 항목 | HWPX → DOCX | DOCX → HWPX |
|---|---|---|
| 본문 글, 탭, 줄바꿈 | 됨 | 됨 |
| 글자 모양: 글꼴, 크기, 진하게, 기울임, 밑줄, 취소선, 색, 음영, 장평, 자간, 위·아래 첨자 | 됨 | 됨 (스타일 상속·테마 글꼴 포함) |
| 문단 모양: 정렬, 여백, 들여쓰기·내어쓰기, 문단 위·아래 간격, 줄 간격, 쪽 나눔 | 됨 | 됨 |
| 표 (셀 합치기, 테두리, 배경, 머리 행 반복, 표 안 표) | 됨 | 됨 (표 스타일 테두리 포함) |
| 그림 (글자처럼 취급, 떠 있는 그림의 위치·배치) | 됨 | 됨 (Word 2007 형식 VML 그림·그림 워터마크 포함) |
| 쪽 설정(용지·방향·여백·다단), 머리말·꼬리말(홀짝·첫 쪽 감추기), 쪽 번호 | 됨 | 됨 |
| 여러 구역 (구역별 쪽 설정·머리말) | 됨 | 됨 |
| 문단 번호·개요 번호·글머리표 | 됨 (그림 글머리표는 글자로) | 됨 (스타일에서 온 번호 포함, 그림 글머리표는 글자로) |
| 각주·미주 (번호 모양·구분선) | 됨 | 됨 (번호 모양·위치) |
| 책갈피 | 됨 | 됨 |
| 링크 (외부 주소·문서 안 책갈피) | 됨 | 글만 옮김 (링크 형식 확인 중) |

- 한컴 전용 글꼴(HFT)은 Word에서 쓸 수 없어 같은 모양의 TTF 이름으로 바꿉니다 (예: 한양신명조 → HY신명조)
- 한글의 비율 줄 간격(글자 크기 × 비율)은 Word의 배수 줄 간격과 기준이 달라서, 문단에서 가장 큰 글자 크기로 줄 높이를 계산해 고정합니다
- 한글은 머리말·꼬리말을 본문 중간 문단에서 바꿀 수 있지만 Word 구역에는 종류별로 하나라서, 바뀌는 문단 앞에서 Word 구역을 나눕니다. 그 문단이 새 쪽에서 시작하면 '다음 페이지부터', 쪽 중간이면 '이어서' 구역 나누기라 Word에서는 다음 쪽부터 바뀝니다(경고). 바뀌지 않은 종류는 "이전과 같게"로 둡니다
- 한글 구역은 늘 새 쪽에서 시작합니다. Word의 '이어서' 구역 나누기는 용지·여백·쪽 번호가 앞 구역과 같으면 구역을 나누지 않고 그 자리에서 단 설정만 바꿉니다(한글의 다단 설정 나누기와 같은 모양, 같은 쪽에서 이어짐). 다르면 다음 쪽에서 시작하는 구역이 됩니다
- Word의 '다음 페이지부터' 구역 나누기는 종류·머리말·꼬리말·단 말고 구역 설정이 앞 구역과 똑같고 쪽 번호 새로 시작·첫 쪽 다르게가 없으면, 한 구역 안에서 그 문단에 쪽 나눔을 겁니다
- 위 두 경우 머리말·꼬리말이 바뀌면 그 문단에 바뀐 머리말·꼬리말을 둡니다 (한글은 구역 중간에서 머리말을 바꿀 수 있음). 그래서 HWPX → DOCX → HWPX 왕복에서 구역이 늘지 않습니다
- '홀수/짝수 페이지부터'·'다음 단부터' 구역 나누기는 다음 쪽에서 시작하는 구역이 됩니다
- 반대로 한글의 다단 설정 나누기(구역 중간 문단의 단 설정)는 Word에서 그 문단 앞 '이어서' 구역 나누기가 됩니다. 머리말·꼬리말·쪽 설정은 앞 구역 것을 이어 씁니다
- 한글은 첫 쪽 머리말을 따로 둘 수 없어, Word의 '첫 페이지를 다르게'는 첫 쪽 머리말·꼬리말 감추기로 바꿉니다
- 그림 흐리게 효과는 Word '희미하게'와 한글 '워터마크 효과'(밝기 70, 대비 -50)를 서로 바꿉니다. 그 밖의 밝기·대비 값은 대응값이 없어 DOCX → HWPX는 워터마크 효과로, HWPX → DOCX는 원래 색으로 두고 경고를 남깁니다
- Word는 문단 안에 표를 둘 수 없어, 표가 든 문단은 표 앞뒤로 나눕니다
- DOCX → HWPX는 Word 스타일 상속(문서 기본값 → 문단 스타일 → 글자 스타일 → 직접 서식)을 풀어 실제 값으로 옮기고, 한글 문서에는 스타일 '바탕글' 하나만 둡니다
- Word 배수 줄 간격은 글꼴의 한 줄 높이 기준이라, jar 안 글꼴 줄 높이 표(아래 "글꼴 줄 높이 표")의 비율을 곱해 한글 비율 줄 간격으로 바꿉니다. 표에 없는 글꼴은 1.3을 씁니다
- Word 글머리표는 수준마다 글자를 달리 둘 수 있지만 한글 글머리표는 글자가 하나라서, 글자마다 한글 글머리표를 따로 만듭니다. 기호 글꼴(Symbol·Wingdings) 글머리표는 같은 모양의 일반 글자로 바꿉니다
- Word는 머리말·꼬리말과 각주 안에 각주를 둘 수 없어, 그런 한글 각주는 빼고 경고를 남깁니다. 각주 구분선의 길이·굵기는 Word 기본값을 씁니다. DOCX → HWPX 각주 구분선·간격은 한글 새 문서 기본값을 씁니다
- Word 책갈피 이름에는 빈칸을 쓸 수 없어 밑줄로 바꾸고 40자에서 자릅니다. 문단을 넘는 한글 링크는 Word에서 문단마다 링크로 나뉩니다
- 한글에 없는 Word 번호 모양(서수 등)은 숫자 번호로 바꾸고 경고를 남깁니다
- 아직 옮기지 못하는 요소는 건너뛰고 `ConvertResult.getSkipped()`에 이름과 개수를 남깁니다
- HWPX → DOCX에서 건너뛴 요소가 든 본문 문단은 원본을 DOCX 안 사용자 정의 XML 파트(`customXml/item1.xml`)에 원본 `header.xml`과 함께 보관하고, 그 문단을 숨은 책갈피(`_hwpx_N`)로 표시합니다. 건너뛴 요소가 없으면 보관하지 않습니다
  - 다시 HWPX로 바꿀 때 책갈피가 남은 문단의 글이 보관 당시와 같으면 원본 문단으로 되살립니다. 원본이 가리키는 글자·문단 모양, 테두리, 탭, 글꼴은 보관한 `header.xml`에서 옮겨 붙이고 번호를 다시 매깁니다. 줄 배치(`linesegarray`)와 구역 설정은 빼고 넣습니다(한글이 다시 계산하고, 쪽 설정은 DOCX 구역 설정에서 만듦)
  - 글이 바뀐 문단, 구역 나누기를 넘는 문단, 그림 데이터(`binaryItemIDRef`)·문단 번호·그 밖에 옮길 수 없는 참조가 있는 문단은 되살리지 않고 변환 결과를 쓰며 경고를 남깁니다

## 폐쇄망 사용

인터넷이 연결되지 않은 환경에서 쓸 수 있게 만듭니다.

- jar 하나만 넣으면 됩니다. 외부 라이브러리가 없습니다
- Java 8 이상이면 됩니다
- 실행 중에 네트워크에 접근하지 않습니다
  - 업데이트 확인, 사용 통계 전송, 원격 자원 읽기가 없습니다
  - 문서 안 XML의 외부 참조(외부 엔티티, 외부 DTD, XInclude)를 읽지 않습니다. DOCTYPE이 든 XML은 거부합니다
  - 줄 간격 보정에 쓰는 글꼴 줄 높이 표는 jar 안에 들어 있어, 실행하는 서버에 글꼴 파일이 없어도 됩니다
- CI가 PR마다 jar(`jdeps`)와 소스를 검사해 `java.net`·`javax.net`·`java.rmi`·`javax.naming` 등 네트워크 관련 API가 들어가면 합치지 않습니다 (`build.sh check`)

반입할 때는 릴리스에 함께 올라간 `.sha256` 파일로 jar가 바뀌지 않았는지 확인합니다.

```
# Windows: 출력된 값이 .sha256 파일 안의 값과 같은지 봅니다
certutil -hashfile hwpx-docx-converter-<버전>.jar SHA256

# Linux: jar와 .sha256 파일을 같은 폴더에 두고
sha256sum -c hwpx-docx-converter-<버전>.jar.sha256
```

## 직접 빌드

빌드에는 JDK 9 이상(개발은 JDK 17)이 필요하고, 만든 jar는 Java 8 이상에서 돌아갑니다. Maven 없이 JDK만으로 빌드합니다.

| 환경 | 명령 |
|---|---|
| Windows | `powershell -ExecutionPolicy Bypass -File build.ps1` |
| Linux·macOS | `bash build.sh` |
| 이클립스 | File → Import → Existing Maven Projects 로 `pom.xml`을 가져옴 |

- 결과는 `build/libs/hwpx-docx-converter-<버전>.jar`
- 다른 JDK에서 시험: `build.ps1 -TestJavaHome <JDK 8 경로>,<JDK 17 경로>` / `JAVA_HOME=<JDK 경로> bash build.sh test`
- 시험은 JUnit 없이 `src/test/java/.../testkit`의 작은 실행기로 돕니다

### 회귀 시험

`test` 단계에서 `src/test/resources/docs/`의 HWPX·DOCX를 모두 반대 형식으로 바꾸고, 문서마다 글자 수(공백 제외)·문단·표·그림·건너뛴 요소 수를 `src/test/resources/baseline/regression.tsv`와 비교합니다. 기준값보다 나빠진 문서가 있으면 실패합니다.

| 할 일 | Windows | Linux·macOS |
|---|---|---|
| 다른 폴더의 문서로 시험 | `build.ps1 regress -Docs <문서 폴더> -Baseline <기준값 폴더> [-Out <결과 폴더>]` | `bash build.sh regress <문서 폴더> <기준값 폴더> [결과 폴더]` |
| 기준값을 지금 수치로 갱신 | `build.ps1 baseline` | `bash build.sh baseline` |

- 나빠짐으로 보는 것: 변환 실패, 결과 글자·문단·표·그림 수 감소, 건너뛴 요소 증가, 기준값에 없는 문서, 문서가 없어진 기준값
- 원본 글자 수가 기준값과 다르면 문서가 바뀐 것이므로 기준값을 갱신합니다

공개 시험 문서 (`src/test/resources/docs/hwpx/`, 한컴오피스 한글 2022에서 작성·저장):

| 파일 | 보는 기능 |
|---|---|
| `char-format.hwpx` | 글자 크기·진하게·기울임·밑줄·색·글꼴, 한 문단 안에서 섞인 글자 모양, 위첨자 |
| `para-format.hwpx` | 가운데·오른쪽·양쪽 정렬, 들여쓰기·내어쓰기, 왼쪽 여백, 줄 간격 130·160·200% |
| `table-merge.hwpx` | 표 두 개, 세로 셀 합치기(3행), 가로 셀 합치기(3열) |
| `header-footer.hwpx` | 머리말, 꼬리말, 쪽 번호(아래 가운데), 쪽 나누기 |
| `multi-section.hwpx` | 구역 3개: 세로 용지, 가로 용지, 2단 |
| `numbering.hwpx` | 문단 번호, 글머리표 |
| `footnote.hwpx` | 각주 2개, 미주 1개 |
| `header-change.hwpx` | 구역 하나 안에서 둘째 쪽 첫 문단에 새 머리말 (왕복 시험에서 구역이 늘지 않는지) |
| `kept-equation.hwpx` | DOCX로 옮기지 못하는 수식이 든 문단 (왕복 시험에서 원본 문단이 되살아나는지) |

공개 시험 문서 (`src/test/resources/docs/docx/`, LibreOffice 26.2.7.3으로 `tools/docgen/`의 원본에서 만듦):

| 파일 | 보는 기능 |
|---|---|
| `char-format.docx` | 굵게·기울임·밑줄·글자 색·크기·글꼴·취소선·형광펜 |
| `para-format.docx` | 제목 1·2 스타일, 가운데·오른쪽·양쪽 정렬, 들여쓰기·내어쓰기, 줄 간격 160%·고정, 문단 위·아래 간격, 오른쪽 여백 |
| `table-merge.docx` | 표 하나, 세로 셀 합치기 2곳, 가로 셀 합치기 1곳, 머리 행 음영·굵은 아래 테두리, 셀 음영 |
| `header-footer.docx` | 머리말, 첫 쪽 머리말 따로, 꼬리말 쪽 번호, 쪽 나누기 |
| `multi-section.docx` | 구역 4개: 세로 용지, 2단(이어서), 가로 용지, 세로 용지 |
| `numbering.docx` | 3단계 번호 목록, 글머리표, 앞 목록에 이어지는 번호, 1부터 다시 시작 |
| `footnote.docx` | 각주 2개, 미주 1개 |

### DOCX 규격 검사

CI의 `docx-validate` 작업이 결과 DOCX를 [Open XML SDK](https://github.com/dotnet/Open-XML-SDK)의 `OpenXmlValidator`로 검사합니다. 오류가 하나라도 있으면 실패하고, 오류 목록(파트, 위치, 설명)은 작업 요약에 남습니다.

- 검사 대상: 단위 시험이 만든 DOCX 전부(환경 변수 `HWPXDOCX_TEST_DOCX_OUT`에 폴더를 주면 시험이 결과를 저장함)와 공개 시험 문서를 변환한 결과
- 기준 버전: Office2007. 가장 오래된 기준이라 그 뒤 버전에만 있는 요소를 쓰면 바로 걸립니다. 어느 Word에서나 열리는 범위로 맞추려는 것입니다
- 도구는 `tools/docx-validate/`(.NET 8)에 있고 jar에는 들어가지 않습니다. 직접 돌릴 때: `dotnet run --project tools/docx-validate -- <DOCX 폴더> [Office2007|…|Microsoft365]`

### LibreOffice 변환 시험

CI의 `libreoffice` 작업이 공개 시험 문서를 변환한 DOCX를 LibreOffice Writer(한글 글꼴 `fonts-nanum`)로 열어 PDF로 바꿉니다. 열리지 않거나 PDF가 만들어지지 않거나 문서당 120초를 넘으면 실패합니다.

- 문서별 PDF 쪽수는 작업 요약과 산출물(`libreoffice-pdf`의 `pages.tsv`)에 남습니다. **쪽수는 참고값**입니다. Word와 줄 나눔·글꼴이 달라 실패 조건으로 쓰지 않습니다
- 직접 돌릴 때 (LibreOffice와 `pdfinfo`가 있는 Linux): `bash tools/libreoffice-pdf.sh <DOCX 폴더> <PDF 폴더> [문서당 제한 초]`

### 글꼴 줄 높이 표

Word 배수 줄 간격은 글꼴의 한 줄 높이 기준이라, 글꼴마다 "한 줄 높이 ÷ 글자 크기"를 미리 계산해 `src/main/resources/io/github/limjaemin99/hwpxdocx/font-line-height.tsv`에 둡니다. 글꼴 파일은 저장소에 넣지 않습니다.

- 서양 글꼴: (hhea ascender − descender + lineGap) ÷ unitsPerEm
- 동아시아 글꼴(OS/2 한중일 코드 페이지): (winAscent + winDescent) ÷ unitsPerEm × 1.3
- Word에서 30줄을 이어 쓰고 잰 평균 비율이 있는 글꼴은 그 값을 씁니다: 맑은 고딕 1.730, 함초롬바탕·함초롬돋움 1.690, 나눔고딕 1.500, 바탕·굴림·돋움·HY중고딕·HY신명조·HY견고딕 1.3025. 표의 셋째 칸에 값마다 출처(`Word 실측(30줄 평균)` / `TTF 계산`)를 적습니다
  - Times New Roman·Arial·Calibri는 잴 때 대체 글꼴로 그려져 그 글꼴의 값이 아니므로 TTF 계산값을 씁니다
- 다시 만들기 (글꼴이 깔린 Windows에서, 빌드 후): `java -cp build/test-classes io.github.limjaemin99.hwpxdocx.tools.FontLineHeights src/main/resources/io/github/limjaemin99/hwpxdocx/font-line-height.tsv`

## 개발 방식

| 브랜치 | 용도 |
|---|---|
| `main` | 릴리스된 상태만. 합쳐지면 `v{버전}` 태그와 Release(jar + SHA-256)가 자동으로 만들어짐 |
| `develop` | 다음 릴리스를 모으는 기본 브랜치 |
| `feature/<이슈>-<이름>`, `fix/…`, `docs/…` | 이슈 하나 = 브랜치 하나 = PR 하나 |

- 기능 PR은 develop으로 squash 해서 합칩니다. 합치려면 CI(JDK 17 빌드, JDK 8·17·21 시험)가 통과해야 합니다
- develop → main 릴리스 PR은 모인 PR들의 확인 목록대로 시험한 뒤 합칩니다
- 마일스톤이 개발 단계입니다: v0.1.0 프로젝트 틀 → v0.2.0 기본 변환 → v0.3.0 여러 구역·자동 번호·각주 → v0.4.0 줄 간격 보정 → v0.5.0 원본 보관(왕복 변환) → v1.0.0
- 커밋과 PR 제목은 [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) 형식(`feat:`, `fix:`, `docs:` …)으로 쓰고 요약은 한국어로 씁니다
- 형식의 근거는 공개 규격입니다: HWPX는 OWPML(KS X 6101), DOCX는 ECMA-376(ISO/IEC 29500)
- 변경 기록은 [CHANGELOG.md](CHANGELOG.md)에 있습니다

## 시험 문서 출처

- 공개 시험 문서의 문장은 법제처 국가법령정보센터(https://www.law.go.kr)에 공개된 「대한민국헌법」 조문입니다. 법령은 저작권법 제7조에 따라 보호받지 않는 저작물입니다
- 표 칸 제목·머리말·꼬리말·각주 문장 일부는 시험용으로 새로 쓴 것입니다
- 문서 저장 정보(작성자·마지막 저장한 사람)는 비웠습니다
- DOCX 시험 문서는 `tools/docgen/`의 원본(Flat ODT, `.fodt`)을 LibreOffice로 변환해 만듭니다. 수동 실행 워크플로(`시험 문서 만들기 (LibreOffice DOCX)`)로 한 번 만들어 저장소에 고정하고, CI는 고정된 파일을 씁니다. 지금 파일은 LibreOffice 26.2.7.3(Ubuntu)으로 만들었습니다

## 라이선스

[Apache License 2.0](LICENSE) · Copyright 2026 Lim Jaemin

한글·HWP·HWPX는 ㈜한글과컴퓨터의 상표이고, Word는 Microsoft Corporation의 상표입니다. 이 프로젝트는 두 회사와 관계가 없습니다.

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

> 두 방향 모두 글자·문단, 표, 그림, 쪽 설정, 머리말·꼬리말을 옮깁니다. 여러 구역·자동 번호·각주는 다음 단계입니다. 아래 "지원 범위"를 보세요.

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
| 그림 (글자처럼 취급, 떠 있는 그림의 위치·배치) | 됨 | 됨 |
| 쪽 설정(용지·방향·여백·다단), 머리말·꼬리말(홀짝·첫 쪽 감추기), 쪽 번호 | 됨 | 됨 (마지막 구역 기준) |
| 여러 구역 (구역별 쪽 설정·머리말) | 됨 | 예정 (v0.3.0) |
| 문단 번호·글머리표, 각주, 링크 | 예정 (v0.3.0) | 예정 (v0.3.0) |

- 한컴 전용 글꼴(HFT)은 Word에서 쓸 수 없어 같은 모양의 TTF 이름으로 바꿉니다 (예: 한양신명조 → HY신명조)
- 한글의 비율 줄 간격(글자 크기 × 비율)은 Word의 배수 줄 간격과 기준이 달라서, 문단에서 가장 큰 글자 크기로 줄 높이를 계산해 고정합니다
- 한글은 머리말·꼬리말을 쪽마다 바꿀 수 있지만 Word 구역에는 종류별로 하나라서 처음 것을 씁니다. 머리말·꼬리말 안 그림은 아직 옮기지 않습니다
- 한글은 첫 쪽 머리말을 따로 둘 수 없어, Word의 '첫 페이지를 다르게'는 첫 쪽 머리말·꼬리말 감추기로 바꿉니다
- Word는 문단 안에 표를 둘 수 없어, 표가 든 문단은 표 앞뒤로 나눕니다
- DOCX → HWPX는 Word 스타일 상속(문서 기본값 → 문단 스타일 → 글자 스타일 → 직접 서식)을 풀어 실제 값으로 옮기고, 한글 문서에는 스타일 '바탕글' 하나만 둡니다
- Word 배수 줄 간격은 글꼴의 한 줄 높이 기준이라, 글꼴별로 잰 비율을 곱해 한글 비율 줄 간격으로 바꿉니다
- 아직 옮기지 못하는 요소는 건너뛰고 `ConvertResult.getSkipped()`에 이름과 개수를 남깁니다

## 폐쇄망 사용

인터넷이 연결되지 않은 환경에서 쓸 수 있게 만듭니다.

- jar 하나만 넣으면 됩니다. 외부 라이브러리가 없습니다
- Java 8 이상이면 됩니다
- 실행 중에 네트워크에 접근하지 않습니다
  - 업데이트 확인, 사용 통계 전송, 원격 자원 읽기가 없습니다
  - 문서 안 XML의 외부 참조(외부 엔티티, 외부 DTD, XInclude)를 읽지 않습니다. DOCTYPE이 든 XML은 거부합니다
  - 줄 간격 보정에 쓰는 글꼴 줄 높이 표는 jar 안에 들어 있어, 실행하는 서버에 글꼴 파일이 없어도 됩니다 (v0.4.0에서 추가 예정)
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

## 라이선스

[Apache License 2.0](LICENSE) · Copyright 2026 Lim Jaemin

한글·HWP·HWPX는 ㈜한글과컴퓨터의 상표이고, Word는 Microsoft Corporation의 상표입니다. 이 프로젝트는 두 회사와 관계가 없습니다.

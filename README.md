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

> 변환 기능은 아직 구현 전이라 지금은 두 방향 모두 `ConvertException`이 납니다. API 모양은 아래와 같습니다.

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

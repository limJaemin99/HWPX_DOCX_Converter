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

## 라이선스

[Apache License 2.0](LICENSE) · Copyright 2026 Lim Jaemin

한글·HWP·HWPX는 ㈜한글과컴퓨터의 상표이고, Word는 Microsoft Corporation의 상표입니다. 이 프로젝트는 두 회사와 관계가 없습니다.

# 변경 기록

이 파일은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 형식을 따르고, 버전은 [유의적 버전](https://semver.org/lang/ko/)을 따릅니다.

## [Unreleased]

### 추가
- 프로젝트 기본 틀: Maven 구성, JDK만으로 빌드하는 `build.ps1`·`build.sh`, JUnit 없이 도는 시험 실행기
- 공개 API 뼈대: `HwpxDocxConverter`, `ConvertOptions`, `ConvertResult`, `ConvertException`
- 명령줄 도구 뼈대 (`java -jar hwpx-docx-converter-<버전>.jar 입력 출력`)
- GitHub Actions: CI(JDK 17 빌드, JDK 8·17·21 시험), main 합칠 때 자동 릴리스(jar + SHA-256), CodeQL, Dependabot
- 이슈 양식(기능, 버그)과 PR 양식, README 개발 방식

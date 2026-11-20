# Tottenham 캘린더 (안드로이드)

## Build & test
- JDK: Android Studio 내장 JBR 사용 → `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`
- 테스트: `./gradlew :app:testDebugUnitTest`
- 빌드: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `local.properties`(SDK 경로, API 키)는 git 제외 파일. 커밋 금지.

## Agent skills

### Issue tracker
스펙과 티켓은 `.scratch/<feature>/` 아래 로컬 마크다운으로 관리. See `docs/agents/issue-tracker.md`.

### Triage labels
기본 다섯 가지 라벨(needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs
Single-context: 루트 `GLOSSARY.md` + `docs/adr/`. See `docs/agents/domain.md`.

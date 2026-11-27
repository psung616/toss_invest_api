# docs — 문서 지도

역할별로 폴더를 나눈다. 새 문서는 아래 중 맞는 폴더에 넣고 이 표에 한 줄 추가한다.
우선순위: `governance/` > `requirements/` > `design/` > `process/` (충돌 시 위쪽이 이긴다. 코드와 다르면 코드가 사실).

| 폴더 | 역할 | 문서 |
|---|---|---|
| `governance/` | **원칙·확정 결정** — 거의 바뀌지 않음. 바꾸려면 사람의 명시 승인 | [PRINCIPLES.md](governance/PRINCIPLES.md) 절대 원칙 · [DECISIONS.md](governance/DECISIONS.md) 확정 결정과 근거 |
| `requirements/` | **무엇을 만들까** — 요구사항·작업 목록·변경 이력 | [TASKS.md](requirements/TASKS.md) 작업 ID·완료 기준 · [CHANGES.md](requirements/CHANGES.md) 요구사항 변경 이력 |
| `requirements/discord/` | 디스코드 채팅 주문(P8, 보류) 기능 요구사항 | [DISCORD_PLAN.md](requirements/discord/DISCORD_PLAN.md) · DISCORD_COMMANDS.md(미커밋 초안) |
| `design/` | **어떻게 만들까** — 구조·설정·도구 명세, 외부 API 사실 | [ARCHITECTURE.md](design/ARCHITECTURE.md) · [TOSS_API_NOTES.md](design/TOSS_API_NOTES.md) · [WEB_SECURITY.md](design/WEB_SECURITY.md) web 모드 보안 정책(RC-002, 승인 대기) |
| `process/` | **어떻게 일할까** — 세션 인수인계, 진행 가이드, Claude Code 설정 | [HANDOFF.md](process/HANDOFF.md) · [VIBE_CODING_GUIDE.md](process/VIBE_CODING_GUIDE.md) · `setup/claude-settings.json` |
| `qa/` | **검증** — QA 계획·결함 대장·보고서 | [QA_PLAN.md](qa/QA_PLAN.md) · [DEFECTS.md](qa/DEFECTS.md) · `reports/` |
| `api/` | 공식 openapi.json·asyncapi.json (`./gradlew fetchSpecs`, **gitignore — 커밋 금지**) | — |

## 요구사항을 바꿀 때
1. 사람이 바꾸고 싶은 내용을 말한다.
2. Claude 가 [CHANGES.md](requirements/CHANGES.md) 에 `RC-NNN` 으로 등록(상태 `제안`)하고, 영향받는 작업 ID·문서·코드를 적는다.
3. 사람이 승인하면(`승인`) TASKS.md·ARCHITECTURE.md 등 해당 문서를 고치고, 바뀐 작업 항목에 `(RC-NNN)` 표시를 붙인다(`반영`).
4. 코드 변경은 그 뒤 평소처럼 작업 ID 단위로 진행한다.
5. `governance/` 의 원칙과 부딪히는 변경은 바로 반영하지 않는다. 사람의 명시 승인을 받고 DECISIONS.md 에도 기록한다.

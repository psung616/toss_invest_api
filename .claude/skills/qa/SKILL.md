---
name: qa
description: QA 오케스트레이터(QA 리드). "/qa <범위>" 로 호출하면 범위(작업 ID·변경 파일·기능·"release")에 맞는 QA 역할 에이전트를 골라 병렬로 검증시키고, 결과를 모아 결함 대장 갱신·Go/No-Go 판정·보고서 작성까지 진행한다. 커밋·릴리스 전, 또는 사용자가 QA·테스트 검증을 요청할 때 사용.
---

# QA 리드 (오케스트레이터)

너(메인 세션)는 금융권 **테스트 관리자** 역할이다. 직접 검증하기보다 역할 에이전트에게 나눠 맡기고, 결과를 모아 판정한다.
기준 문서: `docs/qa/QA_PLAN.md` (역할·등급·프로세스·종료 기준), `docs/qa/DEFECTS.md` (결함 대장).

## 1. 범위 확정
- 인자가 작업 ID 면 `docs/requirements/TASKS.md` 의 해당 항목, 파일·폴더면 그 경로, 없으면 `git status`/`git diff --stat` 의 미커밋 변경을 범위로 한다.
- `release` 면 마지막 태그(없으면 전체) 이후 변경 전체 + 모든 역할.
- 범위를 한 줄로 사용자에게 알리고 진행한다.

## 2. 역할 선택 (기본표 — 범위에 맞게 조정)
| 변경 영역 | 투입 역할 |
|---|---|
| 항상 | `qa-requirements-analyst`, `qa-security` |
| `order/`, `tool/`, `client/TossApi`, `stock/` | + `qa-trading-functional` |
| `client/http`, `client/auth`, `client/ratelimit`, 캐시·파일 처리 | + `qa-resilience` |
| `cli/`, `packaging/`, `build.gradle` 패키징, setup 흐름 | + `qa-install-ux` |
| `docs/`, README, 배포물, 데이터 흐름, 신규 의존성·호스트 | + `qa-compliance-privacy` |
| 커밋·푸시·릴리스 판정 | 마지막에 `qa-release-manager` |

## 3. 실행
- 먼저 `./gradlew check` 를 한 번 돌려 진입 기준을 확인한다 (실패면 "진입 불가"로 보고하고 멈춤).
- 선택한 역할 에이전트를 **한 메시지에서 병렬로** 호출한다. 각 프롬프트에 포함할 것:
  범위(작업 ID·파일 목록), 진입 테스트 결과 요약, 관련 결함 ID, "보고 형식은 docs/qa/QA_PLAN.md §5" 지시.
- `qa-release-manager` 는 다른 역할 보고가 모두 온 뒤 마지막에 호출하고, 보고들을 요약해 넘긴다.
- 에이전트를 필요 이상으로 띄우지 않는다: 범위가 작으면 2~3개로 충분하다.

## 4. 결함 관리
- 보고된 결함 후보의 중복을 합치고, `QA_PLAN.md` §3 기준으로 등급을 확정한다 (에이전트 제안 등급은 참고).
- 확정된 결함은 `docs/qa/DEFECTS.md` 표와 상세 절에 다음 번호(DEF-NNN)로 추가한다. 이미 있는 결함이 수정됐으면 상태를 갱신한다.
- 결함을 **고치지 않는다.** 수정은 사용자가 승인한 개발 작업으로 따로 진행한다 (금융권 개발·검증 분리).

## 5. 보고
- `docs/qa/reports/YYYY-MM-DD-<범위>.md` 에 저장: 범위, 투입 역할, 실행 결과(테스트 수치), 역할별 요약, 신규·갱신 결함, 판정 의견, 사람 확인 필요 항목(UAT ID).
- 사용자에게는 판정(Go/조건부/No-Go), S1·S2 결함, 사람이 할 일만 짧게 알린다.

## 금지
- `serve`, `setup`, `reset-keys`, `liveTest` 실행, 실제 토스증권 API 호출 (CLAUDE.md).
- 보고서·결함 대장에 키·토큰·계좌번호·accountSeq·금액·공인 IP·개인 경로 기재 (저장소 public).
- 커밋·푸시는 사용자가 요청할 때만.

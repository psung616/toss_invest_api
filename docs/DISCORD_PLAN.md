# 디스코드 채팅 주문 — 세부 작업 계획 (P8)

> 목표: 디스코드에서 `삼성전자 3주 매수` 라고 치면 봇이 주문을 이해하고, **미리보기 → [확인] 버튼 → 주문**까지 진행한다.
> 작성: 2026-11-24 · 상태: **계획 (미착수)** · 작업 ID 는 `docs/TASKS.md` 의 P8 섹션과 같다.
> 이 문서는 `docs/PRINCIPLES.md` 보다 우선하지 않는다. 원칙과 충돌하는 부분은 §2 의 결정 게이트에서 사람이 먼저 승인해야 한다.

---

## 1. 사용자 경험 (완성 모습)

```
[디스코드 DM — 나 ↔ 내 봇]

나:  삼성전자 3주 매수
봇:  ┌ 주문 미리보기 ─────────────────────────┐
     │ 종목   삼성전자 (005930)               │
     │ 구분   매수 · 지정가 71,000원          │
     │ 수량   3주                              │
     │ 예상   213,000원                        │
     │ 모드   🧪 DRY RUN (실제 주문 안 나감)   │
     │ 만료   5분 후                           │
     └─────────────────────────────────────────┘
     [✅ 주문 확정]  [❌ 취소]

나:  (✅ 클릭)
봇:  🧪 DRY RUN — 주문을 기록했습니다. 실제 주문은 전송되지 않았습니다.
```
- 지원 입력 예: `삼성전자 3주 매수`, `005930 3주 71000원에 사줘`, `NVDA 2주 시장가 매수`, `삼성전자 1주 팔아줘`,
  `/매수 종목:삼성전자 수량:3 가격:71000`, `현재가 삼성전자`, `잔고`, `미체결`, `주문취소 <주문번호>`
- 가격을 말하지 않은 매수는 **지정가 = 현재가**로 미리보기를 만들고 그 가격을 명시한다 (시장가는 기본 차단이므로).
- 애매하면 주문하지 않고 되묻는다: `"삼성" 으로 3개 종목이 검색됩니다: 삼성전자(005930), 삼성SDI(006400), … 번호를 골라주세요`

## 2. 결정 게이트 (구현 전에 👤 승인 필요)

현재 원칙은 "외부 통신은 토스증권 호스트만" 이다. 디스코드 연동은 이 원칙을 **바꾸는 일**이므로, 아래를 먼저 결정해서
`docs/DECISIONS.md` 와 `docs/PRINCIPLES.md` 에 기록한 뒤 착수한다 (P8-0).

| # | 결정할 것 | 권장안 | 이유 |
|---|---|---|---|
| D1 | 허용 호스트에 디스코드 추가 | **`discord` 모드에서만** `discord.com`, `gateway.discord.gg`(+JDA 가 쓰는 하위 호스트) 허용. `serve` 모드 허용목록은 그대로 | MCP 서버의 "토스만 통신" 보장은 유지 |
| D2 | 토스 데이터가 디스코드 서버를 거침 | **본인 DM 에만** 전송, 잔고·금액은 요약만. 서버(길드) 채널 금지 기본값 | 토스 FAQ: API 정보 제3자 배포 금지 → 공개 채널 노출 차단 |
| D3 | 자연어 해석 방식 | **규칙 기반 파서 (LLM 없음)** 를 기본. LLM(Claude API) 해석은 P8-12 선택 과제 | 돈이 걸린 입력은 결정적이어야 함, 추가 키·호스트 불필요 |
| D4 | 봇 운영 형태 | **각자 자기 봇을 자기 PC 에서 실행** (BYOK 그대로). 공용 봇 운영 금지 | PRINCIPLES 2·4번 — 남의 키를 보관하지 않음 |
| D5 | 새 의존성 | `net.dv8tion:JDA:6.7.0` (2026-09 릴리스, 활발히 관리, Apache-2.0). 음성 기능 제외 설정 | 런타임 의존성은 승인 필요 (CLAUDE.md) |
| D6 | 디스코드 봇 토큰 보관 | 토스 키와 동일하게 OS 자격 증명 저장소 (`tossinvest-mcp-discord`) | 저장소·파일에 토큰 금지 |

**결정이 나기 전에는 P8-1 이후 작업을 시작하지 않는다.**

## 3. 아키텍처

```
[디스코드 앱(폰/PC)] ──DM──▶ [Discord Gateway]
                                   │ WebSocket (JDA)
                                   ▼
┌─ tossinvest-mcp discord 모드 (사용자 PC, 상시 실행) ───────────────────────┐
│ interfaces/discord/                                                       │
│   DiscordBot (JDA 수명주기) → OwnerGuard (내 user ID 만 통과)              │
│   ├ MessageListener   자유 텍스트(DM) ─┐                                  │
│   ├ SlashCommandListener  /매수 /매도 ─┼▶ command/OrderIntentParser        │
│   └ ButtonListener  [확정][취소]       │      ↓ OrderIntent               │
│                                        └▶ command/IntentResolver           │
│                                             (종목명→코드: StockSearch,     │
│                                              가격 없음→현재가 지정가)       │
│   DiscordRenderer (Embed·버튼 생성, 금액 포맷, 마스킹)                      │
│                         ↓ OrderCommand                                    │
│ order/OrderService.preview() → GuardrailService → PreviewStore            │
│ order/OrderService.place(previewId) ← [확정] 버튼                          │
└──────────────────────────────────────┬────────────────────────────────────┘
                                       │ HTTPS (허용 호스트만)
                                       ▼
                                [토스증권 Open API]
```
- **핵심 원칙: 디스코드 계층은 얇은 어댑터.** 주문 검증·가드레일·감사로그는 기존 `order` 패키지를 그대로 쓴다.
  MCP(`tool/`)와 디스코드(`interfaces/discord/`)가 같은 `OrderService` 를 공유하므로 안전장치가 한 곳에 있다.
- 새 패키지 (제안):
  - `command/` — 채널 무관한 명령 해석: `OrderIntent`, `OrderIntentParser`, `IntentResolver`, `ParseResult`
  - `interfaces/discord/` — JDA 의존 코드 전부. ArchUnit 으로 JDA 사용 위치를 이 패키지로 제한
  - `stock/` — `StockMasterCache`, `StockSearch` (P3-2, 디스코드의 선행 조건)
- 실행 모드: `tossinvest-mcp discord` (Spring 컨텍스트 전체, `spring.ai.mcp.server.enabled=false`, web none).
  MCP `serve` 와 **동시에 실행될 수 있음** → 토큰 공유(P2-1) 가 선행돼야 함.

## 4. 선행 조건 (P8 착수 전 완료해야 하는 기존 작업)

| 작업 | 왜 필요한가 |
|---|---|
| **P2-1 토큰 캐시 파일 + 파일 잠금** | `serve` 와 `discord` 두 프로세스가 각자 토큰을 발급하면 클라이언트당 1개 정책 때문에 서로 무효화(`token-revoked`)를 반복한다 |
| **P3-2 종목 마스터 + StockSearch** | "삼성전자" → 005930 변환 |
| **P1-8 ArchitectureTest** | JDA 를 `interfaces.discord` 밖에서 못 쓰게, 허용 호스트 규칙을 모드별로 검사 |
| **P4-1 장 운영 시간 검사** | 디스코드는 언제든 입력 가능 → 장 마감 후 주문 시도를 미리보기에서 막아야 함 |
| P2-2 레이트리미터 (권장) | 채팅으로 연속 입력 시 429 방지 |
| P4-5 취소 (권장) | `주문취소` 명령 |
| P1-4 마스킹 (권장) | 봇 토큰 마스킹 패턴 추가 |

## 5. 작업 분해 (P8-0 ~ P8-12)

각 작업은 한 세션에서 끝낼 수 있는 크기로 잘랐다. 🤖 Claude Code · 👤 사람 · 🔍 diff 한 줄씩 리뷰.
공통 완료 기준: `./gradlew spotlessApply check` 통과 + `docs/TASKS.md` 체크 + `docs/HANDOFF.md` 갱신.

### P8-0 👤🤖 결정 게이트 기록
- §2 의 D1~D6 을 사람이 승인 → `DECISIONS.md` 에 "디스코드 연동" 결정 추가, `PRINCIPLES.md` 데이터 흐름 그림에
  "사용자 본인 디스코드 DM" 경로 추가, `ARCHITECTURE.md` §10 에 모드별 허용 호스트 규칙 추가
- 완료 기준: 👤 문서 diff 승인

### P8-1 🤖 명령 문법 명세 + 파서 테스트 먼저 (`command/`)
- `docs/DISCORD_COMMANDS.md` 에 지원 문법을 표로 정의 (아래 §6 초안을 확정)
- `OrderIntent` record: `action(BUY|SELL|QUOTE|BALANCE|OPEN_ORDERS|CANCEL|HELP)`, `stockQuery`, `quantity`, `price?`, `orderType?`, `orderId?`
- `ParseResult`: `Ok(OrderIntent)` / `NeedClarification(String question)` / `NotACommand`
- **테스트를 먼저 50개 이상** 작성: 띄어쓰기 변형, 숫자 표기(`3주`, `3 주`, `세 주`는 미지원→되묻기), 가격(`71000원`, `7만1천원`, `71,000`),
  동사(`매수/사줘/살래/구매`, `매도/팔아/팔아줘`), 순서 바뀜(`3주 삼성전자 매수`), 오타 방어, 음수·0·소수 수량 거부,
  매수/매도 동사 둘 다 있으면 되묻기, 금액 기반(`10만원어치`)은 v1 미지원 안내
- 완료 기준: 테스트가 실패하는 상태로 커밋하지 말 것 — P8-2 와 같은 세션에서 끝내거나, 테스트만 `@Disabled` 없이 통과하도록 최소 구현

### P8-2 🤖🔍 규칙 기반 파서 구현 (`command/OrderIntentParser`)
- 정규식 + 토큰화. 한국어 숫자 단위(`만`, `천`) 파싱은 `money/Money` (P1-7) 와 함께 `BigDecimal` 로
- 입력 길이 제한(200자), 제어문자 제거, 멘션·코드블록 무시
- 완료 기준: P8-1 테스트 전부 통과, 파서는 네트워크·Spring 의존 없음 (ArchUnit 규칙 추가)

### P8-3 🤖 의도 해석기 (`command/IntentResolver`)
- `stockQuery` → `StockSearch` 결과 1개면 확정, 여러 개면 `NeedClarification`(번호 선택지), 0개면 "찾을 수 없음"
- 가격 없는 매수/매도 → 현재가(`getPrices`)로 **지정가** 채움 + 메시지에 "현재가 기준 지정가" 명시
- 출력: 기존 `OrderCommand` (그대로 `OrderService.preview` 에 전달)
- 테스트: 가짜 `StockSearch`·`TossApi`(Mockito) 로 분기 전부

### P8-4 🤖 JDA 의존성 + `discord` 실행 모드 뼈대
- D5 승인 후 `net.dv8tion:JDA:6.7.0` 추가 (음성 제외: `exclude module: 'opus-java'` 등 — JDA README 기준으로 확인)
- `TossMcpApplication`: `case "discord"` → 외부 설정 차단·로그 폴더 설정은 serve 와 동일, MCP 끔
- `DiscordBot` 빈: 토큰 로드 → `JDABuilder.createLight(...)` (필요한 intent 만: DM 메시지. 길드 Message Content 는 쓰지 않음) → 종료 훅
- `AllowedHostInterceptor` 는 토스 전용 그대로. JDA 의 HTTP/WebSocket 은 JDA 내부 클라이언트가 쓰므로
  **JDA 가 연결하는 호스트를 실측해 문서화**하고, ArchUnit 으로 JDA 사용 위치만 제한 (D1)
- 완료 기준: 봇 토큰 없이 실행하면 "setup 에서 디스코드 봇을 설정하세요" 안내 후 종료 코드 1

### P8-5 🤖🔍 소유자 인증 (`OwnerGuard`)
- 모든 이벤트에서 `event.getUser().getIdLong() == ownerId` 가 아니면 **무응답** (존재를 알리지 않음)
- 기본은 DM 만 처리. 길드 채널은 `TOSSINVEST_DISCORD_ALLOW_GUILD=true` + 채널 ID 지정 시에만, 그때도 소유자만
- 버튼 클릭도 동일 검사 (다른 사람이 버튼을 눌러도 무시) + 버튼 custom ID 에 previewId 와 소유자 ID 포함, 서버에서 재검증
- 테스트: 타인 메시지·타인 버튼·봇 자신의 메시지·웹훅 메시지 모두 무시

### P8-6 🤖 미리보기 → 버튼 → 주문 흐름
- 메시지 → 파서 → 해석기 → `OrderService.preview` → `DiscordRenderer` 로 Embed + `[✅ 주문 확정]` `[❌ 취소]`
- 확정 버튼 → `OrderService.place(previewId)` → 결과 Embed 로 원 메시지 **수정**(버튼 제거, 중복 클릭 방지)
- 취소 버튼 → previewId 폐기(`PreviewStore.take` 후 버림), 메시지 수정
- 5분 만료 시 버튼 비활성화 (JDA 스케줄러 또는 클릭 시점 검사로 충분 — 클릭 시 만료면 "다시 입력하세요")
- 가드레일 위반은 미리보기 단계에서 위반 사유 목록을 그대로 보여주고 버튼을 만들지 않음
- 테스트: `OrderService` 를 가짜로 두고 렌더링·흐름 단위 테스트, previewId 재사용 거부

### P8-7 🤖 슬래시 명령 (`/매수`, `/매도`, `/현재가`, `/잔고`, `/미체결`, `/취소`, `/도움말`)
- 구조화 입력이라 파서 없이 바로 `OrderIntent` 생성 → 자유 텍스트보다 오인식 위험이 낮음
- 종목 옵션은 자동완성(autocomplete)으로 `StockSearch` 결과 제시
- 슬래시 명령은 DM 에서도 쓰도록 등록 (전역 명령, 반영까지 시간 걸릴 수 있음 → 문서화)
- 테스트: 옵션 → `OrderIntent` 변환

### P8-8 🤖 조회 명령 렌더링
- `현재가 <종목>`, `잔고`(=get_portfolio, P3-4 필요), `미체결`, `매수가능`
- **D2 준수**: 잔고는 종목·수량·평가손익률 요약만, 총액 표시 여부는 설정(`TOSSINVEST_DISCORD_SHOW_AMOUNTS`, 기본 false)

### P8-9 🤖🔍 setup 마법사에 디스코드 단계 추가
- 콘솔·창 모두: "디스코드 봇도 설정할까요?" → 단계별 안내
  1. Discord Developer Portal 에서 New Application → Bot → Reset Token → 복사 (링크 제공)
  2. 봇 토큰 붙여넣기 → OS 저장소 `tossinvest-mcp-discord` 에 저장 (`OsKeyring` 재사용, 서비스명만 다름)
  3. 내 디스코드 사용자 ID 입력 (개발자 모드 → 프로필 우클릭 → ID 복사 안내) → 설정 파일이 아닌 OS 저장소의 account 필드에 저장
  4. 봇 연결 테스트(`/users/@me`) → 봇 이름 표시
  5. DM 을 받으려면 봇과 같은 서버가 필요 → **초대 링크 자동 생성**(권한 최소, `applications.commands` + `bot`), 개인 서버 만들기 안내
- `reset-keys` 에 디스코드 토큰 삭제 포함
- 테스트: 가짜 키링으로 저장·삭제, 출력에 토큰 없음

### P8-10 🤖 상시 실행 편의
- Windows: "로그인 시 자동 실행" 선택 시 작업 스케줄러 등록(`schtasks`) / 해제 — 사람 확인 후에만
- 트레이 아이콘(선택): 실행 중 표시 + 종료 메뉴 (`java.awt.SystemTray`)
- `diagnose` 에 디스코드 연결 상태(연결됨/토큰 없음/오류 코드) 추가 — 토큰·ID 는 출력 금지
- jpackage exe 로 `discord` 모드 실행 확인 (`tossinvest-mcp.exe discord`)

### P8-11 👤 실사용 테스트 (DRY RUN)
- setup 으로 봇 설정 → 개인 서버에 초대 → DM 으로 `삼성전자 1주 매수` → 미리보기 → 확정 → `audit.jsonl` 에 dryRun 기록
- 체크리스트: 타인 계정으로 DM 시 무응답, 비허용 종목 거부, 장 마감 후 거부, 버튼 두 번 클릭 시 두 번째 거부,
  봇 재시작 후 이전 버튼 클릭 시 "만료" 안내, `serve` 와 동시 실행 시 토큰 충돌 없음

### P8-12 (선택) 🤖 LLM 자연어 해석
- D3 에서 승인된 경우만. 규칙 파서가 `NotACommand`/`NeedClarification` 을 낼 때만 보조로 사용
- 사용자 본인의 Anthropic API 키(BYOK, OS 저장소), 허용 호스트에 `api.anthropic.com` 추가(디스코드 모드 한정)
- LLM 은 **OrderIntent JSON 만 출력**하게 하고, 결과는 반드시 같은 해석기·미리보기·버튼을 거침 (LLM 이 주문을 직접 실행하지 않음)
- 프롬프트에 잔고 등 계좌 데이터를 넣지 않음

## 6. 명령 문법 초안 (P8-1 에서 확정)

| 의도 | 예시 입력 | 결과 |
|---|---|---|
| 매수(가격 생략) | `삼성전자 3주 매수`, `삼성전자 3주 사줘` | 현재가 지정가 미리보기 |
| 매수(지정가) | `005930 3주 71000원 매수`, `삼성전자 3주 7만1천원에 사줘` | 지정가 미리보기 |
| 매수(시장가) | `삼성전자 3주 시장가 매수` | 시장가 미리보기 (기본 설정에선 가드레일이 거부) |
| 매도 | `삼성전자 1주 매도`, `삼성전자 1주 팔아줘` | 매도 미리보기 (기본 설정에선 거부) |
| 현재가 | `삼성전자 현재가`, `현재가 NVDA`, `삼성전자 얼마` | 시세 |
| 잔고 | `잔고`, `내 주식`, `포트폴리오` | 요약 |
| 미체결 | `미체결`, `주문내역` | 목록 |
| 취소 | `주문취소 <주문번호>` | 취소 미리보기 → 버튼 |
| 도움말 | `도움말`, `help`, `?` | 문법 안내 |
| 미지원 | `10만원어치 사줘`, `전부 팔아` | "v1 에서는 수량(주)으로 입력하세요" |

규칙: 수량은 양의 정수 + `주` 필수(오인식 방지: `삼성전자 3 매수` 는 되묻기). 매수·매도 동사가 없으면 주문으로 보지 않는다.

## 7. 보안·안전 체크리스트 (P8 전체 완료 조건)
- [ ] 봇 토큰·토스 키가 저장소/로그/디스코드 메시지 어디에도 없음 (마스킹 테스트)
- [ ] 소유자 외 사용자의 모든 이벤트 무시 (테스트)
- [ ] 모든 주문은 `OrderService.preview → place` 경유, 디스코드 코드에서 `TossApi.createOrder` 직접 호출 없음 (ArchUnit)
- [ ] 버튼 1회성 (previewId 1회용 + 메시지 수정으로 버튼 제거)
- [ ] 기본값: 주문 꺼짐, DRY RUN, 허용 종목 비어 있으면 주문 불가 — 디스코드 모드에서도 동일
- [ ] 길드 채널 기본 차단, 잔고 금액 기본 비표시
- [ ] JDA 사용은 `interfaces.discord` 패키지만 (ArchUnit)
- [ ] `serve` + `discord` 동시 실행 시 토큰 충돌 없음 (P2-1)
- [ ] README 에 "디스코드 메시지는 Discord 서버에 저장된다, 본인 DM 만 사용하라" 고지

## 8. 예상 규모
| 묶음 | 작업 | 규모 감 |
|---|---|---|
| 선행 | P1-8, P2-1, P3-2, P4-1 (+P2-2, P4-5, P1-4) | 세션 3~5회 |
| 핵심 | P8-0 ~ P8-6 | 세션 4~6회 |
| 완성 | P8-7 ~ P8-10 | 세션 3~4회 |
| 검증 | P8-11 (👤) | 사람 30분~1시간 |
| 선택 | P8-12 | 세션 1~2회 |

권장 진행 순서: **P8-0 → (선행: P1-8 → P2-1 → P3-2 → P4-1) → P8-1 → P8-2 → P8-3 → P8-4 → P8-5 → P8-6 → P8-9 → P8-11(1차 실사용) → P8-7 → P8-8 → P8-10**

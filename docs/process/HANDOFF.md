# HANDOFF — 세션 인수인계

> 새 Claude Code 세션은 이 문서부터 읽는다 (`CLAUDE.md` 에서 `@` import 로 자동 로드됨).
> 작업을 마칠 때마다 **"현재 상태"·"다음 할 일"·"작업 로그"를 갱신**하고 커밋한다.
> 이 문서와 코드가 다르면 코드가 사실이다. 원칙 충돌 시 `docs/governance/PRINCIPLES.md` 가 우선한다.

마지막 갱신: 2026-11-27 (P4-5/6 완료 — 계획된 개발 순서 끝. 👤 일괄 테스트 대기) · 기준 커밋: `91907d9` · 미커밋: P8 파서 초안(보류, `command/`·DISCORD_COMMANDS.md)

---

## 1. 한눈에 보기
- **무엇**: 토스증권 Open API 를 감싸는 비공식 로컬 서버. Claude 에서 자연어로(MCP `serve`) + **내 PC 브라우저에서(`web`, 127.0.0.1 전용 — RC-001, P9-2 로그인·상태 화면까지)** 시세 조회·주문.
- **스택**: Java 21 (Temurin) · Spring Boot 4.2.0 · Spring AI 2.1.0 MCP Server(STDIO, SYNC) · Gradle 9.9.2 wrapper · Spotless(palantir) · JNA 5.19.1
- **패키지 루트**: `io.github.psung616.tossmcp`
- **저장소**: https://github.com/psung616/toss_invest_api — **이미 public** (2026-11-23 확인). 모든 커밋이 즉시 공개되므로
  키·계좌·실데이터·개인 경로가 들어가지 않게 커밋 전 반드시 확인. (P6-6 의 "public 전환"은 사실상 완료, 태그·보호 설정은 남음)
- **배포 모델**: BYOK. 소스만 배포, 각 사용자가 자기 키로 자기 PC 에서 실행. 키는 저장소에 절대 두지 않음.

## 2. 현재 상태 (무엇이 동작하는가)

| 영역 | 상태 | 검증 방법 |
|---|---|---|
| 빌드·테스트 | `./gradlew check` 통과, 테스트 341개 (P8 파서 70개는 미커밋 보류 — 커밋 기준 271개) | 로컬 실행 |
| 공급망 | `gradle.lockfile` + `gradle/verification-metadata.xml`(sha256). 의존성 변경 시 재생성 필요(아래 §8) | 체크섬 변조 시 빌드 실패 확인 |
| 레이트리밋·재시도 | 그룹별 토큰버킷(피크 ORDER_INFO 3), 429 Retry-After·백오프 3회, 헤더로 한도 하향 | `RateLimiterTest`, `TossHttpClientTest` |
| 로그 마스킹 | server.log·stderr 에 키·토큰·계좌·금액 `***` (audit.jsonl 제외) | `MaskingTest`, `LogbackConfigTest` |
| `serve` (MCP stdio) | 동작. stdout 은 JSON-RPC 만, 로그는 사용자 폴더 + stderr | jar·exe 를 서브프로세스로 띄워 `initialize`/`tools/list`/`tools/call` 확인 |
| 도구 | 조회 4개 항상: `get_quote`, `search_stock`, `get_buying_power`, `list_orders` / 주문 2개는 `TOSSINVEST_ENABLE_ORDERS=true` 일 때만: `preview_order`, `place_order` | tools/list |
| 토스 API 연동 | **실제 키로는 아직 미검증.** 가짜 키로 토큰 요청 → `401 invalid_client` 수신·안내문 변환 확인 | 실호출 |
| 키 저장 (Windows) | JNA → 자격 증명 관리자 쓰기/읽기/삭제 동작 | `WindowsCredentialManagerTest` (실제 OS 사용, 테스트 전용 이름) |
| 키 저장 (macOS/Linux) | 코드만 있음, **실기기 미검증** | — |
| setup 마법사 | 콘솔(`setup`) + 창(인자 없이 더블클릭 / `setup --gui`). 저장 → 연결 테스트 → Claude Code 자동 등록 | 창 실행 확인, 자동 등록은 테스트 이름으로 등록 → `claude mcp get` 에서 ✔ Connected → 삭제 |
| `.mcpb` (⏸ 보류, RC-001) | `./gradlew mcpb` → `build/mcpb/tossinvest-mcp-0.1.0-SNAPSHOT-win32.mcpb` (~80MB). JDK 없이 exe 실행·env 키 전달 확인 | **Claude Desktop 실제 설치는 미검증** (P6-8) |
| `diagnose` | 동작(P3-5): 버전·OS·키 설정 여부·연결 결과 코드·최근 에러 코드 5개·주문 플래그·감사로그 건수·web 포트. `--offline` | `DiagnosticsServiceTest`, `DiagnoseCommandTest`(민감정보 미출력) |
| `web` (localhost REST·UI) | **P9-2·P9-9 까지**: 127.0.0.1 Tomcat(MCP 끔) → 콘솔에 일회용 접속 URL + 브라우저 자동 열기 → `tim_session` 쿠키 → 상태 화면(`/`)·`GET /api/status` + **키 없이도 기동, 화면에서 키 입력·교체(`POST /api/keys`, RC-003)**. 요청 검사 6단계·보안 헤더·본문 없는 에러(WEB_SECURITY.md). **실제 브라우저 확인은 👤 미실시.** 조회 REST(시세 등)는 P9-3/4 | `WebModeStartupTest`(실 Tomcat 로그인 흐름·LAN 연결 거부), `LocalAccessFilterTest`(MockMvc 17), `LaunchCodeStoreTest`, `SessionStoreTest`, `StaticUiTest` |

## 3. 다음 할 일 (우선순위 순)

### ▶ 2026-11-27 P9-9 완료 (RC-003 승인) — 다음 세션 시작점
- 이 PC 에는 **저장된 키가 없다**(§6). 그래서 `web` 이 키 없이도 뜨고 화면에서 키를 넣게 바꿈 — E1 교체 허용 · E2 Claude Code 등록 안 함 · E3 P9-9 먼저.
- 👤 Claude Code 입력창에서 새 창으로 실행(이 창의 `!` 는 Git Bash):
  `! powershell.exe -Command "Start-Process powershell -ArgumentList '-NoExit','-Command','chcp 65001; java -jar build\libs\tossinvest-mcp-0.1.0-SNAPSHOT.jar web'"`
  → 브라우저 "API 키 입력" 카드에 키 붙여넣기 → [저장하고 연결 테스트] → 결과 문구 공유(키 값 X). 허용 IP 미등록이면 "저장했지만 연결 실패" 가 정상.
- ✅ 커밋 완료(`58b6cf7`, `91907d9`, 푸시 안 함). 👤 실제 키 저장·연결 성공 확인.
- ✅ P9-3 완료·커밋: 조회 로직 `service/` 로 추출, MCP 문구 그대로(문구 고정 테스트).
- ✅ P9-4 완료·커밋: 조회 REST 4개(입력 검증·금액 문자열·에러 정규화 — 토스 인증 실패는 502).
- ✅ P9-5 완료·커밋: 화면에 종목 검색→현재가, 매수 가능 금액(KRW/USD), 주문 내역(미체결/완료). **실제 브라우저 확인은 👤 (P9-7)**.
- ✅ P9-6 완료·커밋: ArchUnit 9~12(tool→service, web 의존 범위, tool↛web, CORS 금지), README `web` 사용법. diagnose 의 web 항목은 P3-5 로 이관.
- 👤 **P9-7 실사용 확인**: `web` 실행 → 검색 "삼성전자" → 현재가 → 매수 가능 금액 → 주문 내역. 방화벽 창 안 뜸, 다른 기기 접속 불가, 시크릿 창에서 `/api/quote?symbol=005930` 직접 열면 401.
- **사용자 지시(2026-11-27): "개발이 모두 완료되면 한번에 테스트하겠다"** — P9-7 등 👤 확인은 개발 끝난 뒤 한꺼번에. 그동안 🤖 은 순서대로 계속.
- ✅ P3-4 완료·커밋: `get_portfolio`(국내+해외 원화 합산·비중)·`get_sellable_quantity`·`get_order`, `/api/portfolio`, 화면 "보유 종목". ⚠ 환율 `rate` 의미 실호출 확인 필요.
- ✅ P3-5 완료·커밋: `diagnose`(`--offline`), 민감정보·사용자 이름 미출력 테스트.
- ✅ P4-1 완료·커밋: 일일 카운터 `cache/daily-counters.json`(serve·web 공유, 손상 시 미리보기 거부).
- ✅ P9-8 완료·커밋(👤 2026-11-27 "P9-8 승인·진행" — 이어서 P4-5/6 도 진행 지시): 웹 주문 미리보기→[주문 확정], 주문 기능 꺼짐(기본)이면 엔드포인트 404·카드 숨김.
- ✅ P4-5/P4-6 완료·커밋: MCP 취소·정정(preview_cancel/cancel_order, preview_modify/modify_order). **HANDOFF 의 개발 순서(P9→P3-4→P3-5→P4-1→P9-8→P4-5/6) 완료.**
- 👤 **일괄 테스트** (사용자 지시): P9-7 web 실사용, P3-6 Claude Code 조회, P4-7 DRY_RUN 주문 대화, diagnose 출력, 환율 rate 값 크기 확인. 토스 "API 주문 일시 정지"는 DRY_RUN 테스트에는 영향 없음(토스로 안 보냄).
- 🤖 남은 후보(👤 우선순위 결정): P4-3 상하한가 범위 검사·수수료, P3-3 캔들·환율·장 상태 도구, P1-1/2/6/7 기반 테스트·US 원화 환산(P1-7 — 미국 주문 차단 해제 조건), 푸시 전 `/qa`. 푸시는 👤 결정(공개 전 `/qa` 권장). 사용자 지시(2026-11-27): 단계마다 묻지 말고 P9-4→P9-5→P9-6 계속 진행·단계별 커밋(푸시 X).

### 2026-11-27 P9-2 완료
- RC-002 승인(D1~D5 추천안), WEB_SECURITY.md 승인 상태. P9-2 + D5(`/api/status`·첫 화면·콘솔 URL·브라우저 열기·Enter 재발급) 구현.
- 👤 `./gradlew bootJar` → `java -jar build/libs/tossinvest-mcp-0.1.0-SNAPSHOT.jar web` → 자동으로 열린 브라우저에서 상태 화면 확인(키 설정됨·주문 꺼짐·DRY_RUN 배지). 방화벽 창이 뜨면 허용하지 말고 알리기.
- 👤 diff 리뷰(🔍 P9-1·P9-2). 커밋 여부 결정.
- 🤖 다음: **P9-3** 조회 서비스 계층 추출(`service/`) → P9-4 조회 REST → P9-5 조회 화면.
- 참고: 404 등 에러는 `SilentErrorController` 가 본문 없이 상태만 반환(Tomcat 기본 에러 페이지 미노출). 콘솔 Enter → 새 접속 코드.

### 2026-11-27 web 보안 정책 초안
- 사용자 요청: "localhost 로 바로 확인할 수 있게" → 먼저 보안 정책 md 작성. [`docs/design/WEB_SECURITY.md`](../design/WEB_SECURITY.md) 초안 + CHANGES **RC-002(제안)**.
- 👤 WEB_SECURITY.md 검토 + §7 결정 D1~D5 승인 → 🤖 ARCHITECTURE §12.2·TASKS 반영 → **P9-2**(+ `/api/status`·최소 첫 화면·콘솔 URL·브라우저 열기) 테스트부터.

### 2026-11-27 P9-1 완료
- 👤 P9-1 diff 리뷰(🔍): `build.gradle`(webmvc + webmvc-test), `gradle.lockfile`·`verification-metadata.xml`(런타임 +11: Tomcat 11.0.27·spring-webmvc 7.1.0·boot web 모듈), `application.yml`(server.*, `spring.web.error.*`), `TossMcpApplication`·`cli/WebLauncher`·`web/security/LoopbackGuard`.
- 🤖 다음: **P9-2** 보안 필터(Host·Origin·Sec-Fetch-Site·일회용 코드·세션 쿠키·보안 헤더). `web` 실제 실행은 👤 만(키가 저장돼 있으면 실서버가 뜸). 404 응답 본문의 Tomcat 기본 에러 페이지(서버 버전 노출 가능)는 P9-2 에서 "본문 없이 상태 코드만"으로 정리.

### (이전) 2026-11-27 RC-001 직후
- 요구사항 변경 **RC-001**: EXE 대신 **localhost(127.0.0.1) 웹 모드** + 브라우저 UI, MCP 유지, 조회 먼저, Spring MVC 스타터 승인, EXE·.mcpb 보류.
  반영 문서: CHANGES(RC-001)·DECISIONS(2026-11-27)·PRINCIPLES(데이터 흐름·장치 표)·ARCHITECTURE(§1·2·3·4·10·11·**12**)·TASKS(**P9** 신설, P6-7/8 보류)·CLAUDE.md(원칙 8, 스택, 명령, 금지).
- 👤 문서 검토 → OK 면 🤖 **P9-1**(의존성 + `web` 모드 뼈대) 계획 제시부터. 순서 P9-1→2(보안 필터)→3(service 추출)→4(REST)→5(UI)→6→👤P9-7.
- 👤 커밋 여부 결정 (문서 재구성 + RC-001. P8 파서 초안은 계속 미커밋).

### (참고) 2026-11-26 문서 재구성
- 문서가 역할별 폴더로 이동함 — 지도: `docs/README.md` (governance / requirements / design / process / qa / api).
  CLAUDE.md `@` import·필수 문서 표, 두 훅, QA 에이전트·스킬, README, Java 주석의 경로를 모두 새 경로로 교체(옛 경로 잔존 0, 훅 동작 확인).
- 요구사항 변경 절차: 🤖 `docs/requirements/CHANGES.md` 에 `RC-NNN` 등록 → 👤 승인 → TASKS·설계 반영 → 코드 작업.

### (이전) 2026-11-25 종료 시점 할 일 — 여전히 유효
1. 👤 **UAT-01**: `build/jpackage/tossinvest-mcp/tossinvest-mcp.exe`(또는 jar) 더블클릭 → 빈 입력으로 [저장하고 연결 테스트] →
   "입력하세요" 안내만 나오고 등록·"완료" 없음 확인 → 결과 문구 공유 → 🤖 DEF-001 "종료" 처리.
   (exe 가 없으면 `./gradlew mcpb` 먼저)
2. 👤 허용 IP 등록 → 실제 키로 UAT-04 → Claude Code `/mcp` → "삼성전자 현재가 알려줘" (UAT-05/06).
3. 🤖 개발 순서(RC-001 반영): **P9-1~6** → P3-4 `get_portfolio` → P3-5 `diagnose` → P4-1 일일 카운터 파일 → P9-8(주문 REST, 승인 후) → P4-5/6. 커밋 전 `/qa` 사용.

### 👤 사람이 먼저 해야 하는 것 (막혀 있음)
1. **토스증권 WTS > 설정 > Open API > 허용 IP** 에 현재 PC 의 공인 IP 등록 (브라우저에서 "내 아이피" 검색. 공인 IP 는 바뀔 수 있으며 **이 문서에 적지 않는다**).
2. `build\jpackage\tossinvest-mcp\tossinvest-mcp.exe` 더블클릭 → 키 붙여넣기 → [저장하고 연결 테스트].
   (빌드 산출물이 없으면 `./gradlew mcpb` 먼저.) 결과 문구를 Claude 에게 알려주기 — **키 값은 절대 채팅에 붙여넣지 말 것.**
3. 새 Claude Code 세션에서 `/mcp` 로 `tossinvest` 연결 확인 → "005930 현재가 알려줘", "매수 가능 금액 알려줘".
4. (선택) Claude Desktop 에 `.mcpb` 설치 → 키 입력 화면, 콘솔 창 깜빡임 여부 확인 (P6-8).

### 🤖 Claude Code 가 할 작업 (권장 순서, 작업 ID 는 `docs/requirements/TASKS.md`)
완료: P1-8, P2-1, P3-2, P0-4, P0-5, P1-4, P2-2, P2-3, P4-1(장 시간 부분). P0-6 은 표 작성 완료·사람 승인 완료(2026-11-25).
1. **P3-4 `get_portfolio`** (`/holdings`, ASSET), **P3-5 `diagnose`**.
2. **P4-1 잔여** — 일일 카운터 파일 영속화(`cache/daily-counters.json` + 파일 잠금).
3. **P4-5/P4-6** 취소·정정 (정정 바디: KR 은 quantity 필수, US 는 quantity 불가 — TOSS_API_NOTES "스펙 확인 결과").
4. **P1-1/P1-2/P1-6/P1-7** 남은 기반 테스트(AppPaths·TossProperties·에러 계층·Money).
5. **P6-7 잔여** — jlink 모듈 축소, `packaging/mcpb/icon.png`, macOS 빌드.
6. **P8 디스코드 — ⏸ 보류 (2026-11-25 사용자 결정).** 당분간 로컬 PC 에서 본인만 키를 넣고 Claude 로 쓰는 것에 집중.
   P8-1/P8-2 파서 초안(`command/`, `docs/requirements/discord/DISCORD_COMMANDS.md`)은 미커밋으로 남겨 둠. 재개 시 P8-0 결정 게이트부터.
   재개 시 반영할 보안 검토(2026-11-25): 확정 시 TOTP 필수(D7), 디스코드 전용 한도·허용 종목 필수·매도 차단(D8),
   OTP 3회 실패 잠금(PC 에서만 해제), `/잠금` 명령, 봇 토큰 마스킹, P4-1 일일 카운터 영속화 선행.

### 🧪 QA 체계 (2026-11-25 추가)
- `/qa <범위>` 스킬(QA 리드) → `.claude/agents/qa-*.md` 역할 에이전트 7개 병렬 검증 → `docs/qa/DEFECTS.md` 등록 → Go/No-Go.
- 기준: [`docs/qa/QA_PLAN.md`](../qa/QA_PLAN.md). QA 에이전트는 운영 코드를 고치지 않는다(개발·검증 분리).
- DEF-001 (S2) setup 실패 후 "완료" 표시 → **수정완료, 👤 UAT-01/02 재검증 대기** (`SetupFlow`).
- **HANDOFF 갱신 훅(Stop)**: 응답 종료 시 HANDOFF.md 보다 나중에 바뀐 파일이 있으면 갱신을 요구(`.claude/hooks/require-handoff.mjs`). 작업마다 이 문서를 갱신할 것.
- **필수 문서 훅**: `src/main` 등 편집 전 CLAUDE.md "코드 영역별 필수 문서" 표의 문서를 Read 해야 함(`.claude/hooks/require-docs.mjs`, `.claude/settings.json`). 막히면 안내된 문서를 읽고 재시도.

각 파일의 `TODO(Px-y)` 주석이 해당 작업의 구체적 잔여 항목이다: `grep -rn "TODO(" src`.

## 4. 코드 지도

```
src/main/java/io/github/psung616/tossmcp/
├─ TossMcpApplication   main: 인자 없음→Cli.noArgs, serve, web, setup[--gui], reset-keys, diagnose[--offline]
│                       serve 는 spring.config.location 고정(외부 설정 차단), stdio=true, tossinvest.logs 설정
├─ cli/        Cli(모드 진입), Usage, DiagnoseCommand(diagnose 출력), WebLauncher(web: 키 확인·127.0.0.1 고정·실패 안내), WebConsole(접속 URL·Enter 재발급), BrowserOpener, SetupSteps(저장·연결테스트·등록 공통), ConsoleSetup, SetupWindow(Swing),
│              ClaudeCodeRegistrar(claude mcp add --scope user, .cmd 는 cmd.exe /d /s /c "...")
├─ config/     TossProperties(tossinvest.*, 1억 미만 클램프), AppPaths(OS 사용자 폴더), KillSwitch, AppConfig(Clock KST)
├─ secret/     CredentialStore ← Env(우선) / Keyring, Composite(@Primary, SecretConfig)
│              OsKeyring.detect() → WindowsCredentialManager(JNA CredWriteW/ReadW/DeleteW) | MacKeychain | LinuxSecretTool
├─ logging/    Masking, MaskingConverter(%maskedMsg), MaskingThrowableConverter(%maskedEx)
├─ stock/      StockMasterCache(cache/stock-master.json 24h), StockSearch
├─ command/    (미커밋) OrderIntentParser — P8-1/2 초안
├─ client/     TossApi(엔드포인트 메서드 + 레이트리밋 그룹 명시, accountSeq 캐시: BROKERAGE)
│  ├─ ratelimit/ RateLimitGroup, RateLimiter(GCRA), Sleeper, RateLimitConfig
│  ├─ http/    TossRestClientConfig(유일한 RestClient, Redirect.NEVER, 5s/10s), AllowedHostInterceptor,
│  │           TossHttpClient(리미터·토큰·계좌헤더·봉투해제·401 재발급·429 재시도), RetryPolicy, TokenEndpoint(+standalone()), ErrorDecoder(Retry-After)
│  ├─ auth/    TokenManager + TokenCacheFile(cache/token.json, 프로세스 간 공유·파일 잠금)
│  ├─ error/   TossApiException(status, code, requestId), UserMessages(코드→한국어)
│  └─ dto/     Envelope, ErrorEnvelope, TokenResponse, PriceDto, AccountDto, BuyingPowerDto, OrderListDto, OrderCreate*
├─ order/      GuardrailService(위반 전부 수집), DailyCounterStore(cache/daily-counters.json, 파일 잠금 — P4-1), PreviewStore(1회용 previewId, TTL 5분),
│              OrderService(preview→place), OrderChangeService(취소·정정 미리보기→실행, P4-5/6), AuditLogger(audit.jsonl), OrderCommand/Side/Type
├─ service/    StockSearchService·QuoteService·AccountQueryService(조회 + 매도 가능·주문 상세)·PortfolioService(원화 합산·비중, P3-4) — tool·web 공유,
│              AppStatusService(버전·키 설정/환경변수 여부·주문/DRY_RUN 플래그), DiagnosticsService(diagnose 수집), KeySetupService(화면 키 저장·검증·1분 5회·연결 확인 — RC-003)
├─ tool/       MarketTools, AccountTools(service 위임, 문장 변환만), OrderTools(@ConditionalOnProperty enable-orders), ToolErrors
└─ web/        (servlet 일 때만 빈 생성) api/StatusController(/api/status), api/KeysController(POST /api/keys), api/OrderController(POST /api/orders/preview·place, enable-orders 일 때만),
                api/QueryController(/api/stocks/search·quote·buying-power·orders) + ApiErrorAdvice(에러 정규화)
   └─ security/ LoopbackGuard, LocalAccessFilter(검사 6단계 + POST 검사 + 보안 헤더), LaunchCodeStore(1회용 2분 코드), SessionStore(1세션·8h/24h),
                LoginController(/login → 303 + tim_session), SilentErrorController(본문 없는 에러), SecurityHeaders
   resources/static/  index.html·app.js·app.css — 상태·검색→현재가·매수 가능 금액·주문 내역·키 입력 (외부 리소스·인라인 스크립트 없음)
packaging/mcpb/manifest.json   MCPB v0.3, binary, user_config(sensitive 키 2개 + 주문 설정 4개) → 환경변수
```
- 사용자 폴더(Windows): 설정 `%APPDATA%\tossinvest-mcp\`(KILL 파일), 로그 `%LOCALAPPDATA%\tossinvest-mcp\logs\server.log`, `audit.jsonl`
- 키: Windows 자격 증명 관리자 > Windows 자격 증명 > `tossinvest-mcp` (UserName=Client ID, Blob=Secret UTF-8)

## 5. 빌드·실행·검증 명령
```bash
./gradlew spotlessApply check      # 포맷 후 테스트 (Spotless 가 파일을 고치므로 편집 직후 "file changed" 알림은 정상)
./gradlew bootJar                  # build/libs/tossinvest-mcp-0.1.0-SNAPSHOT.jar
./gradlew mcpb                     # build/jpackage/tossinvest-mcp/tossinvest-mcp.exe + build/mcpb/*.mcpb
./gradlew fetchSpecs               # docs/api/openapi.json, asyncapi.json (gitignore, 공개 문서라 키 불필요)
java -jar build/libs/tossinvest-mcp-0.1.0-SNAPSHOT.jar --help
```
**MCP 수동 검증 (키 없이 가능)** — stdin 으로 JSON-RPC 를 넣고 stdout 이 전부 JSON 인지 본다:
```bash
( printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'; sleep 12 ) \
 | java -Dtossinvest.home=<임시폴더> -jar build/libs/tossinvest-mcp-0.1.0-SNAPSHOT.jar serve
```
(`-Dtossinvest.home` 으로 로그·설정 폴더를 임시 위치로 돌릴 수 있다.)

## 6. 개발 환경 사실 (이 PC, 2026-11-23)
- OS: Windows 11, 셸: Claude Code 는 Git Bash 사용 → `./gradlew` 사용
- JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot` (winget `EclipseAdoptium.Temurin.21.JDK`, JAVA_HOME·PATH 시스템 등록).
  이미 열려 있던 셸에는 PATH 가 반영 안 될 수 있음 → `export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.10002-hotspot"`
- Claude Code CLI: npm 설치, `%APPDATA%\npm\claude.cmd` (`where claude` 로 확인)
- git 작성자: 저장소 local 설정에 이미 있음 (`git config user.name` / `user.email`) — 다시 설정하지 말 것
- Python 3.12 있음(스크래치 분석용), gh CLI 없음
- `claude mcp list` 에 `tossinvest` (user scope) 등록됨·✔ Connected (2026-11-26 확인 — 실제 키 조회 결과는 아직 미공유)
- **2026-11-27: 사람이 토스증권 WTS 에서 "API 주문 일시 정지"를 켜 둠** — 토스 쪽에서 API 주문이 막힌 상태(앱의 ENABLE_ORDERS=false·DRY_RUN=true 와 별개의 이중 안전장치). 조회는 정상. 주문 실호출 확인(P4·P9-8) 전에는 사람이 직접 해제해야 함
- **2026-11-27 갱신: 사람이 web 화면으로 키 저장 + 연결 성공**(아래 줄은 그 이전 상태). 이전: **이 PC 에 저장된 키가 없었다** (자격 증명 관리자에 `tossinvest-mcp` 항목 없음, `TOSSINVEST_CLIENT_*` 환경변수 없음). 사람이 `web` 실행 → "저장된 키가 없습니다" 안내로 정상 종료. 👤 `setup` 먼저 필요.

## 7. 확인된 외부 사실 (추측 아님)
- 토스 REST 호스트 `https://openapi.tossinvest.com` 접근 가능. 토큰 없이 호출 시 `401 {"error":{"referenceId","code":"edge-blocked","message"}}` — **`requestId` 대신 `referenceId`** 를 줌 → ErrorDecoder 가 둘 다 처리.
- 잘못된 키로 `POST /oauth2/token` → `401`, code `invalid_client`, message `Client authentication failed: client_id`.
- openapi.json v1.2.28 확인 항목: `GET /api/v1/prices?symbols=` (result 배열: symbol, timestamp, lastPrice(string), currency),
  `GET /api/v1/accounts` (accountNo, accountSeq(int), accountType), `GET /api/v1/buying-power?currency=` (cashBuyingPower),
  `GET /api/v1/orders?status=OPEN|CLOSED`, `POST /api/v1/orders` (quantity·price 는 **문자열**, `clientOrderId` 선택, `confirmHighValueOrder`),
  토큰 응답 `access_token, token_type, expires_in` 필수.
- Spring AI 2.1.0: `org.springframework.ai.mcp.annotation.McpTool` / `McpToolParam` / `McpTool.McpAnnotations(readOnlyHint, destructiveHint, idempotentHint, openWorldHint)`. Spring AI 2.1.0 ↔ Boot 4.2.0. Jackson 3 (`tools.jackson.databind.json.JsonMapper`), 애너테이션은 `com.fasterxml.jackson.annotation`.
- MCPB manifest v0.3: `server.type: "binary"`, `user_config.*.sensitive: true`, `${__dirname}`, `${user_config.key}` → env.
- java-keyring 1.0.4: 2023-08 이후 릴리스/커밋 없음 → 사용 안 함. jna-platform 에는 CredRead 바인딩이 없어 직접 작성함.

## 8. 함정·주의 (다음 세션이 반복하지 않도록)
- **키를 채팅으로 받지 말 것.** 실제 연결 확인은 사람이 setup 으로 하고 결과 문구만 공유받는다.
- **`claude mcp add --scope project` 금지** — 저장소에 `.mcp.json` 이 생겨 커밋 위험. 등록은 user scope, 키 미포함.
- `serve` / `setup` / `liveTest` 를 실제 키로 Claude 가 실행하지 않는다 (CLAUDE.md "하지 말 것").
- Windows 에서 `.cmd` 를 ProcessBuilder 로 직접 실행하지 말고 `cmd.exe /d /s /c "<전체 명령>"` 사용 (공백 경로 검증됨).
- jpackage exe 는 `--win-console` 이라 더블클릭 시 콘솔 창이 함께 뜸. 인자 없이 실행하면 `Cli.isPackagedLauncher()` 로 설정 창을 띄움.
- Windows 콘솔(Git Bash 파이프)에서 한글이 깨져 보일 수 있음 — 출력 문제일 뿐. 콘솔 마법사는 이모지 대신 `[OK]`/`[!]` 사용.
- `Map.of` 는 10쌍 제한 — `UserMessages` 에 코드를 더 넣으려면 `Map.ofEntries` 로 바꿀 것.
- **의존성을 바꾸면** `./gradlew dependencies --write-locks` 후 `./gradlew --write-verification-metadata sha256 check bootJar` 로 잠금·체크섬을 다시 만들고, 바뀐 항목을 확인한 뒤 커밋한다.
- logback 패턴에서 `%maskedEx` 를 빼면 logback 이 마스킹 없는 예외 출력을 자동으로 붙인다 — 반드시 유지.
- `setup` 의 연결 테스트 토큰 무효화 문제는 P2-1(토큰 캐시 공유)로 해결됨.
- 같은 폴더에서 다른 Claude 세션이 동시에 작업한 적이 있음(2026-11-23). 덮어쓰기 전에 `git status` 와 파일 수정 시각을 확인.
- `docs/api/` (openapi.json 등 공식 스펙)는 gitignore 대상 — 커밋하지 않는다.
- **저장소가 public 이다.** 문서·코드·커밋 메시지에 공인 IP, 개인 폴더 경로(`C:\Users\<이름>\...`), 이메일, 키, 계좌 정보를 쓰지 않는다.
  경로는 `%APPDATA%`, `%LOCALAPPDATA%`, `<프로젝트 루트>` 처럼 일반화해서 적는다.

## 9. 작업 로그
| 날짜 | 커밋 | 내용 |
|---|---|---|
| 2026-11-23 | `2d6a20c` | P0-3 뼈대: Boot 4.2 + Spring AI MCP, 패키지 구조, 허용 호스트, 토큰, 가드레일, preview/place, audit, 문서 일체 |
| 2026-11-23 | `7e0b800` | P1-3 setup 마법사(콘솔+창, 연결 테스트, Claude Code 자동 등록), JNA 키 저장, ErrorDecoder 보강, P6-7 jpackage + `.mcpb` |
| 2026-11-23 | `b5859e1` | HANDOFF.md 추가, CLAUDE.md 에서 자동 로드, public 저장소 개인정보 규칙 |
| 2026-11-24 | `9034b46` | P8 디스코드 채팅 주문 세부 계획(`docs/requirements/discord/DISCORD_PLAN.md`), TASKS 에 P8-0~12 추가 |
| 2026-11-24 | `fd83698` `c69e22b` `4858bd8` | P1-8 ArchUnit, P2-1 토큰 공유 캐시, P3-2 search_stock + P4-1 장 시간 |
| 2026-11-25 | `1814448` | P0-4 잠금·체크섬, P0-5 fetchSpecs, P0-6 스펙 확인 표, P1-4 마스킹, P2-2 레이트리미터, P2-3 429 재시도 |
| 2026-11-25 | `2b63b47` `e32fb7a` | QA 체계(역할 에이전트 7개·/qa·결함 대장)·필수 문서 훅, DEF-001 setup 실패 흐름 수정(SetupFlow), 디스코드 보류 |
| 2026-11-25 | `4f8713a` `7cdc3b8` | Stop 훅으로 작업 종료 시 HANDOFF 갱신 강제, CLAUDE.md 작업 방식 5번 보강 |
| 2026-11-26 | (미커밋) | 이전 작업 재점검: check 통과(173, 커밋 기준 103), TASKS 체크·TODO 12개·DEF-001 상태 대조, MCP 등록 확인 → HANDOFF 사실 갱신 |
| 2026-11-26 | (미커밋) | md 문서를 `docs/` 역할별 하위폴더로 재구성(git mv), 문서 지도 `docs/README.md`·요구사항 변경 이력 `docs/requirements/CHANGES.md` 신설, 참조 경로 일괄 갱신 |
| 2026-11-27 | (미커밋) | RC-001 localhost 웹 모드: 보안 판단(조건부 OK)·사람 결정 4건 기록, 원칙·결정·설계 §12·TASKS P9·CLAUDE.md 재정의, EXE·.mcpb 보류 |
| 2026-11-27 | (미커밋) | P9-1: spring-boot-starter-webmvc(+test) 추가·잠금/체크섬 재생성, `web` 모드(WebLauncher·LoopbackGuard), MCP 스타터 servlet 동작 jar 확인→ARCHITECTURE §12.6, 테스트 18개 |
| 2026-11-27 | (미커밋) | web 보안 정책 초안 `docs/design/WEB_SECURITY.md`(위협 모델·규칙 ID·검증 표·결정 D1~D5), RC-002 제안 등록 |
| 2026-11-27 | — | 👤 web 화면에서 실제 키 저장 → "키를 저장했고 토스증권 연결에 성공했습니다" (토큰 발급 성공 = 허용 IP 등록 OK). **실제 키 연결 첫 확인** |
| 2026-11-27 | (P4-5/6 커밋) | P4-5/P4-6: 주문 취소·정정(OrderChangeService·TossApi cancel/modify·도구 4개·ArchUnit 7 확장·감사로그 change 이벤트), 테스트 14개(341). 개발 순서 완료 |
| 2026-11-27 | (P9-8 커밋) | P9-8(👤 승인): 웹 주문 REST·화면 확정 버튼(같은 OrderService), OrderServiceTest 안전장치, 조건 변이 확인, 테스트 15개(327) |
| 2026-11-27 | (P4-1 커밋) | P4-1: 일일 카운터 파일 영속화(파일 잠금·원자적 교체·손상 시 닫힌 쪽), 테스트 8개(312). P9-8 착수 승인 대기 |
| 2026-11-27 | (P3-5 커밋) | P3-5: diagnose(DiagnosticsService·DiagnoseCommand, --offline, 로그에서 에러 코드·requestId 만, 경로 ~ 치환, web 포트), 테스트 10개(304) |
| 2026-11-27 | (P3-4 커밋) | P3-4: 보유 종목(원화 합산·비중)·매도 가능 수량·주문 상세 — DTO·TossApi·서비스·도구 3개·`/api/portfolio`·화면 카드, 테스트 13개(294) |
| 2026-11-27 | (P9-6 커밋) | P9-6: ArchUnit 9~12·돈 규칙 확장(위반 클래스로 동작 확인), README web 사용법, 테스트 4개(281) |
| 2026-11-27 | (P9-5 커밋) | P9-5: 브라우저 조회 화면(검색→현재가·매수 가능 금액·주문 내역), 금액 문자열 포맷, StaticUiTest 2개(277) |
| 2026-11-27 | (P9-4 커밋) | P9-4: 조회 REST 4개·입력 검증·에러 정규화(ApiErrorAdvice), 테스트 11개(275) |
| 2026-11-27 | (P9-3 커밋) | P9-3: 조회 서비스 추출(StockSearch/Quote/AccountQuery), 도구는 위임만·문구 고정 테스트, 테스트 17개(264) |
| 2026-11-27 | `58b6cf7` `91907d9` | 👤 요청으로 커밋: 문서 재구성·RC-001~003 / P9-1·P9-2·P9-9 코드(깨끗한 worktree 에서 check 통과, 177개). 푸시 안 함 → 🤖 P9-3 계획 제시 |
| 2026-11-27 | (커밋됨) | RC-003 승인 → P9-9: 키 없이 web 기동, 화면 키 입력(`KeySetupService`·`KeysController`·POST 검사·토큰 재발급), WS-KEY-1~9, 테스트 17개(247) |
| 2026-11-27 | — | 👤 첫 `web` 실행: 키 없음 안내로 종료(정상 동작). 자격 증명 관리자에 키 없음 확인(값 미출력) → setup 먼저 안내 |
| 2026-11-27 | (미커밋) | RC-002 승인 → P9-2: 요청 검사 필터·일회용 접속 코드·세션 쿠키·보안 헤더·본문 없는 에러·마스킹, `/api/status`·상태 화면·콘솔 URL·브라우저 열기(D5), 테스트 39개(230) |

## 10. 사용자 선호 (대화에서 확인됨)
- 한국어로 소통. 바이브코딩 방식 — Claude Code 가 작업 ID 단위로 진행.
- UX 는 "키만 붙여넣으면 바로 사용" 수준으로 쉬워야 함.
- 작업이 끝나면 커밋·푸시까지 요청하는 편 (단, 매번 요청을 확인하고 진행).
- 최종 목표 중 하나: **디스코드 채팅창에 "삼성전자 3주 매수" → 자동 인식 → 매수** (P8) — 2026-11-25 보류, 우선 로컬 PC 단독 사용.

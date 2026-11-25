# HANDOFF — 세션 인수인계

> 새 Claude Code 세션은 이 문서부터 읽는다 (`CLAUDE.md` 에서 `@` import 로 자동 로드됨).
> 작업을 마칠 때마다 **"현재 상태"·"다음 할 일"·"작업 로그"를 갱신**하고 커밋한다.
> 이 문서와 코드가 다르면 코드가 사실이다. 원칙 충돌 시 `docs/PRINCIPLES.md` 가 우선한다.

마지막 갱신: 2026-11-25 · 기준 커밋: P0-4~P2-3 커밋(아래 작업 로그) · P8-1/2 파서 초안은 미커밋

---

## 1. 한눈에 보기
- **무엇**: 토스증권 Open API 를 감싸는 비공식 로컬 MCP 서버. Claude 에서 자연어로 시세 조회·주문.
- **스택**: Java 21 (Temurin) · Spring Boot 4.2.0 · Spring AI 2.1.0 MCP Server(STDIO, SYNC) · Gradle 9.9.2 wrapper · Spotless(palantir) · JNA 5.19.1
- **패키지 루트**: `io.github.psung616.tossmcp`
- **저장소**: https://github.com/psung616/toss_invest_api — **이미 public** (2026-11-23 확인). 모든 커밋이 즉시 공개되므로
  키·계좌·실데이터·개인 경로가 들어가지 않게 커밋 전 반드시 확인. (P6-6 의 "public 전환"은 사실상 완료, 태그·보호 설정은 남음)
- **배포 모델**: BYOK. 소스만 배포, 각 사용자가 자기 키로 자기 PC 에서 실행. 키는 저장소에 절대 두지 않음.

## 2. 현재 상태 (무엇이 동작하는가)

| 영역 | 상태 | 검증 방법 |
|---|---|---|
| 빌드·테스트 | `./gradlew check` 통과, 테스트 163개 | 로컬 실행 |
| 공급망 | `gradle.lockfile` + `gradle/verification-metadata.xml`(sha256). 의존성 변경 시 재생성 필요(아래 §8) | 체크섬 변조 시 빌드 실패 확인 |
| 레이트리밋·재시도 | 그룹별 토큰버킷(피크 ORDER_INFO 3), 429 Retry-After·백오프 3회, 헤더로 한도 하향 | `RateLimiterTest`, `TossHttpClientTest` |
| 로그 마스킹 | server.log·stderr 에 키·토큰·계좌·금액 `***` (audit.jsonl 제외) | `MaskingTest`, `LogbackConfigTest` |
| `serve` (MCP stdio) | 동작. stdout 은 JSON-RPC 만, 로그는 사용자 폴더 + stderr | jar·exe 를 서브프로세스로 띄워 `initialize`/`tools/list`/`tools/call` 확인 |
| 도구 | 조회 4개 항상: `get_quote`, `search_stock`, `get_buying_power`, `list_orders` / 주문 2개는 `TOSSINVEST_ENABLE_ORDERS=true` 일 때만: `preview_order`, `place_order` | tools/list |
| 토스 API 연동 | **실제 키로는 아직 미검증.** 가짜 키로 토큰 요청 → `401 invalid_client` 수신·안내문 변환 확인 | 실호출 |
| 키 저장 (Windows) | JNA → 자격 증명 관리자 쓰기/읽기/삭제 동작 | `WindowsCredentialManagerTest` (실제 OS 사용, 테스트 전용 이름) |
| 키 저장 (macOS/Linux) | 코드만 있음, **실기기 미검증** | — |
| setup 마법사 | 콘솔(`setup`) + 창(인자 없이 더블클릭 / `setup --gui`). 저장 → 연결 테스트 → Claude Code 자동 등록 | 창 실행 확인, 자동 등록은 테스트 이름으로 등록 → `claude mcp get` 에서 ✔ Connected → 삭제 |
| `.mcpb` | `./gradlew mcpb` → `build/mcpb/tossinvest-mcp-0.1.0-SNAPSHOT-win32.mcpb` (~80MB). JDK 없이 exe 실행·env 키 전달 확인 | **Claude Desktop 실제 설치는 미검증** (P6-8) |
| `diagnose` | 미구현 ("not implemented") | — |

## 3. 다음 할 일 (우선순위 순)

### 👤 사람이 먼저 해야 하는 것 (막혀 있음)
1. **토스증권 WTS > 설정 > Open API > 허용 IP** 에 현재 PC 의 공인 IP 등록 (브라우저에서 "내 아이피" 검색. 공인 IP 는 바뀔 수 있으며 **이 문서에 적지 않는다**).
2. `build\jpackage\tossinvest-mcp\tossinvest-mcp.exe` 더블클릭 → 키 붙여넣기 → [저장하고 연결 테스트].
   (빌드 산출물이 없으면 `./gradlew mcpb` 먼저.) 결과 문구를 Claude 에게 알려주기 — **키 값은 절대 채팅에 붙여넣지 말 것.**
3. 새 Claude Code 세션에서 `/mcp` 로 `tossinvest` 연결 확인 → "005930 현재가 알려줘", "매수 가능 금액 알려줘".
4. (선택) Claude Desktop 에 `.mcpb` 설치 → 키 입력 화면, 콘솔 창 깜빡임 여부 확인 (P6-8).

### 🤖 Claude Code 가 할 작업 (권장 순서, 작업 ID 는 `docs/TASKS.md`)
완료: P1-8, P2-1, P3-2, P0-4, P0-5, P1-4, P2-2, P2-3, P4-1(장 시간 부분). P0-6 은 표 작성 완료·사람 승인 완료(2026-11-25).
1. **P3-4 `get_portfolio`** (`/holdings`, ASSET), **P3-5 `diagnose`**.
2. **P4-1 잔여** — 일일 카운터 파일 영속화(`cache/daily-counters.json` + 파일 잠금).
3. **P4-5/P4-6** 취소·정정 (정정 바디: KR 은 quantity 필수, US 는 quantity 불가 — TOSS_API_NOTES "스펙 확인 결과").
4. **P1-1/P1-2/P1-6/P1-7** 남은 기반 테스트(AppPaths·TossProperties·에러 계층·Money).
5. **P6-7 잔여** — jlink 모듈 축소, `packaging/mcpb/icon.png`, macOS 빌드.
6. **P8 디스코드** — P8-1/P8-2 파서 초안(`command/`, `docs/DISCORD_COMMANDS.md`)이 **미커밋 상태로 존재**.
   **P8-0 결정 게이트(사람 승인)가 아직 없음** → 승인 후 커밋·P8-3 진행. 상세: [`docs/DISCORD_PLAN.md`](DISCORD_PLAN.md).

각 파일의 `TODO(Px-y)` 주석이 해당 작업의 구체적 잔여 항목이다: `grep -rn "TODO(" src`.

## 4. 코드 지도

```
src/main/java/io/github/psung616/tossmcp/
├─ TossMcpApplication   main: 인자 없음→Cli.noArgs, serve, setup[--gui], reset-keys, diagnose(미구현)
│                       serve 는 spring.config.location 고정(외부 설정 차단), stdio=true, tossinvest.logs 설정
├─ cli/        Cli(모드 진입), Usage, SetupSteps(저장·연결테스트·등록 공통), ConsoleSetup, SetupWindow(Swing),
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
├─ order/      GuardrailService(위반 전부 수집), DailyCounterStore(메모리), PreviewStore(1회용 previewId, TTL 5분),
│              OrderService(preview→place), AuditLogger(audit.jsonl), OrderCommand/Side/Type
└─ tool/       MarketTools, AccountTools, OrderTools(@ConditionalOnProperty enable-orders), ToolErrors
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
- 현재 `claude mcp list` 에 tossinvest 등록 없음 (테스트 등록은 삭제함)

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
| 2026-11-24 | `9034b46` | P8 디스코드 채팅 주문 세부 계획(`docs/DISCORD_PLAN.md`), TASKS 에 P8-0~12 추가 |
| 2026-11-24 | `fd83698` `c69e22b` `4858bd8` | P1-8 ArchUnit, P2-1 토큰 공유 캐시, P3-2 search_stock + P4-1 장 시간 |
| 2026-11-25 | (이 커밋) | P0-4 잠금·체크섬, P0-5 fetchSpecs, P0-6 스펙 확인 표, P1-4 마스킹, P2-2 레이트리미터, P2-3 429 재시도 |

## 10. 사용자 선호 (대화에서 확인됨)
- 한국어로 소통. 바이브코딩 방식 — Claude Code 가 작업 ID 단위로 진행.
- UX 는 "키만 붙여넣으면 바로 사용" 수준으로 쉬워야 함.
- 작업이 끝나면 커밋·푸시까지 요청하는 편 (단, 매번 요청을 확인하고 진행).
- 최종 목표 중 하나: **디스코드 채팅창에 "삼성전자 3주 매수" → 자동 인식 → 매수** (P8).

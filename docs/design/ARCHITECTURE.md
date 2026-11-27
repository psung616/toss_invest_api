# 아키텍처 (Java / Spring Boot)

## 1. 전체 구조
```
[Claude Desktop / Claude Code]          [내 PC 브라우저]  ← http://127.0.0.1:8765
        │ MCP (stdio)                           │ HTTP (루프백 전용, 세션 쿠키)
        │ java -jar ... serve                   │ java -jar ... web      (RC-001, 2026-11-27)
        ▼                                       ▼
┌─ tossinvest-mcp (Spring Boot) ───────────────────────────────┐
│ tool/      @McpTool 빈 (serve 모드)   web/  REST·UI (web 모드)  │
│ service/   조회 로직 공유 (tool·web 이 같은 서비스 사용)          │
│ order/     가드레일 · 미리보기 저장소 · 일일 카운터 · 감사로그   │
│ stock/     종목 마스터 캐시 + 검색                            │
│ client/    토큰 · 레이트리밋 · 허용목록 RestClient · 에러 · DTO  │
│ config/ secret/ logging/ money/ cli/                         │
└──────────────────────────────────────────────────────────────┘
        │ HTTPS (허용 호스트만)
        ▼
[토스증권 Open API]
```

## 2. 실행 모드
`TossMcpApplication.main(args)`가 첫 인자로 모드를 결정한다.

| 모드 | Spring 컨텍스트 | MCP 서버 | stdout |
|---|---|---|---|
| `serve` | 전체, `web-application-type=none` (**포트 안 엶**) | 켬(`spring.ai.mcp.server.stdio=true`) | **MCP 전용** |
| `web` (RC-001) | 전체, `web-application-type=servlet`, `server.address=127.0.0.1` 고정 | 끔 | 접속 안내(URL·일회용 코드)만. 로그는 파일+stderr |
| `setup` / `reset-keys` | 없음 (Spring 없이 실행, 연결 테스트는 `TokenEndpoint.standalone()`) | 끔 | 안내 문구만 |
| `setup --gui` | 없음 | 끔 | 없음 (설정 창) |
| `diagnose` | MCP 끔 | 끔 | 진단 결과(민감정보 없음) |
| 인자 없음 | 없음 | 끔 | 더블클릭(콘솔 없음) 또는 설치형 exe → 설정 창, 터미널의 `java -jar` → 사용법 |

- 모든 모드에서 `main`이 `SpringApplication` 시작 전에 `spring.config.location=classpath:/application.yml`을
  설정해 **작업 폴더의 `application.yml`·`config/` 같은 외부 설정 파일을 읽지 않게** 한다
  (stdio 서버는 실행 위치가 일정하지 않고, 외부 파일로 설정이 바뀌면 안 되기 때문).
- 설정 값은 번들된 `application.yml` + 환경변수만 사용.

## 3. 프로젝트 구조
```
toss_invest_api/
├─ CLAUDE.md / README.md(P6) / LICENSE(P6) / SECURITY.md(P6)
├─ build.gradle / settings.gradle / gradle.properties
├─ gradlew / gradlew.bat / gradle/wrapper/
├─ gradle/verification-metadata.xml      # 의존성 체크섬 (P0-4)
├─ gradle.lockfile                       # 의존성 잠금 (P0-4)
├─ .gitignore / .pre-commit-config.yaml(선택)
├─ .claude/settings.json                 # 필수 문서 훅(커밋됨). 권한 규칙까지 쓰려면 docs/process/setup/claude-settings.json(훅 포함)을 사람이 복사
├─ .claude/hooks/require-docs.mjs        # PreToolUse 훅: CLAUDE.md "코드 영역별 필수 문서" 표 기준
├─ .claude/hooks/require-handoff.mjs     # Stop 훅: HANDOFF.md 보다 나중에 바뀐 파일이 있으면 갱신 요구
├─ .claude/agents/qa-*.md, skills/qa/    # QA 역할 에이전트 7개 + /qa 오케스트레이터 (docs/qa/QA_PLAN.md)
├─ .github/workflows/ci.yml, release.yml  # P6
├─ .github/ISSUE_TEMPLATE/bug_report.md   # P6
├─ docs/  governance/(원칙·결정) requirements/(TASKS·CHANGES·discord/) design/(ARCHITECTURE·TOSS_API_NOTES) process/(HANDOFF·VIBE_CODING_GUIDE·setup/) qa/ api/ ← gitignore — docs/README.md
└─ src/
   ├─ main/java/io/github/<깃허브ID>/tossmcp/
   │  ├─ TossMcpApplication.java          # main: 모드 분기, 외부 설정 차단
   │  ├─ cli/        SetupCommand, ResetKeysCommand, DiagnoseCommand, Usage
   │  ├─ config/     TossProperties(@ConfigurationProperties record), AppPaths, KillSwitch, ClockConfig
   │  ├─ secret/     CredentialStore, EnvCredentialStore, KeyringCredentialStore, CompositeCredentialStore, SecretConfig,
   │  │              OsKeyring → WindowsCredentialManager(JNA) / MacKeychain(security) / LinuxSecretTool(secret-tool)
   │  ├─ logging/    MaskingConverter (logback 메시지 마스킹)
   │  ├─ money/      Money (BigDecimal 헬퍼, KRW/USD 포맷·환산)
   │  ├─ client/
   │  │  ├─ http/    AllowedHostInterceptor, TossHttpClient(요청 파이프라인), RetryPolicy
   │  │  ├─ auth/    TokenManager, TokenCacheFile
   │  │  ├─ ratelimit/ RateLimitGroup(enum), EndpointGroups, TokenBucketRateLimiter
   │  │  ├─ error/   TossApiException(+하위), ErrorCode, UserMessages
   │  │  ├─ dto/     record들 (BigDecimal)
   │  │  └─ TossApi.java                  # 엔드포인트별 메서드 (도구가 쓰는 유일한 진입점)
   │  ├─ stock/      StockMasterCache, StockSearch
   │  ├─ order/      GuardrailService, GuardrailResult, DailyCounterStore, PreviewStore, AuditLogger
   │  ├─ service/    QuoteService, AccountQueryService … (조회 로직, tool·web 공유 — RC-001)
   │  ├─ tool/       MarketTools, AccountTools, OrderTools  (@Component + @McpTool, service 위임)
   │  └─ web/        (web 모드 전용, RC-001)
   │     ├─ api/      QueryController (REST, service 위임), ApiErrors
   │     └─ security/ LoopbackGuard(바인딩 검사), HostOriginFilter, LaunchCodeStore, SessionAuthFilter, SecurityHeaders
   ├─ main/resources/ application.yml, logback-spring.xml, static/ (로컬 웹 UI — 외부 리소스 없음)
   └─ test/java/.../ (단위 테스트, ArchitectureTest, ServeStdoutIT, live/ ← @Tag("live"))
       test/resources/fixtures/   # 손으로 만든 가짜 응답만
```
- 패키지 루트 `io.github.<깃허브ID>.tossmcp`는 P0-3에서 사람에게 확인 후 확정

## 4. 설정 (`application.yml` + 환경변수)
`.env`는 지원하지 않는다. 각 값은 `application.yml`에서 환경변수를 명시적으로 매핑한다:
```yaml
tossinvest:
  enable-orders: ${TOSSINVEST_ENABLE_ORDERS:false}
  dry-run: ${TOSSINVEST_DRY_RUN:true}
  max-order-krw: ${TOSSINVEST_MAX_ORDER_KRW:100000}
  # ... 아래 표 전부
```

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `TOSSINVEST_CLIENT_ID` / `TOSSINVEST_CLIENT_SECRET` | (없음) | 지정 시 자격 증명 저장소보다 우선. 일반 사용자는 `setup` 사용 |
| `TOSSINVEST_ENABLE_ORDERS` | `false` | false면 주문 도구 빈 자체를 등록하지 않음 |
| `TOSSINVEST_DRY_RUN` | `true` | true면 주문 API 호출 없이 감사로그에만 기록 |
| `TOSSINVEST_MAX_ORDER_KRW` | `100000` | 1회 주문 최대 금액(원화 환산) |
| `TOSSINVEST_MAX_DAILY_KRW` | `300000` | 하루 누적 주문 최대 금액 |
| `TOSSINVEST_MAX_DAILY_ORDERS` | `10` | 하루 주문 횟수 |
| `TOSSINVEST_ALLOWED_SYMBOLS` | `""` | 쉼표 구분. **비어 있으면 주문 불가**(조회는 가능) |
| `TOSSINVEST_ALLOW_MARKET_ORDERS` | `false` | 시장가 허용 |
| `TOSSINVEST_ALLOW_SELL` | `false` | 매도 허용 |
| `TOSSINVEST_ALLOW_US` | `false` | 미국 주식 주문 허용 |
| `TOSSINVEST_MIN_ORDER_INTERVAL_SEC` | `5` | 연속 주문 최소 간격(남용 방지) |
| `TOSSINVEST_LOG_LEVEL` | `INFO` | |
| `TOSSINVEST_KILL` | (없음) | `1`이면 모든 주문 거부 |
| `TOSSINVEST_WEB_PORT` | `8765` | `web` 모드 포트(1024~65535). **주소는 설정 불가 — 항상 `127.0.0.1`** |
| `TOSSINVEST_WEB_OPEN_BROWSER` | `true` | `web` 기동 시 기본 브라우저로 접속 URL 자동 열기 |

- 하드 상한: `max-order-krw`는 설정과 무관하게 **1억원 미만**으로 강제(시작 시 클램프 + 경고 로그)
- 킬 스위치: 사용자 config 폴더에 `KILL` 파일이 있거나 `TOSSINVEST_KILL=1`

### `serve` 모드 필수 Spring 설정 (P0-6에서 공식 문서·예제와 대조해 확정)
```yaml
spring:
  main:
    banner-mode: off
    web-application-type: none
  ai:
    mcp:
      server:
        name: tossinvest-mcp
        version: ${버전}
        type: SYNC
        stdio: true          # serve 모드에서만 true (다른 모드는 MCP 끔)
logging:
  pattern:
    console: ""              # 콘솔(stdout) 로그 끔 → 파일/stderr는 logback-spring.xml에서
```

## 5. 사용자 폴더 (`AppPaths`, 표준 라이브러리로 직접 구현)
| 용도 | Windows | macOS | Linux |
|---|---|---|---|
| config | `%APPDATA%\tossinvest-mcp\` | `~/Library/Application Support/tossinvest-mcp/` | `$XDG_CONFIG_HOME/tossinvest-mcp/` |
| cache | `%LOCALAPPDATA%\tossinvest-mcp\cache\` | `~/Library/Caches/tossinvest-mcp/` | `$XDG_CACHE_HOME/tossinvest-mcp/` |
| logs | `%LOCALAPPDATA%\tossinvest-mcp\logs\` | `~/Library/Logs/tossinvest-mcp/` | `$XDG_STATE_HOME/tossinvest-mcp/logs/` |
- config: `KILL` / cache: `token.json`(+`.lock`), `stock-master.json`, `daily-counters.json` / logs: `server.log`, `audit.jsonl`
- 테스트용 오버라이드: 시스템 프로퍼티 `tossinvest.home`
- POSIX에서는 토큰 캐시 권한 600, Windows는 사용자 프로필 폴더 기본 ACL에 의존

## 6. 클라이언트 계층 핵심 동작
**허용목록 HTTP (`client/http`)**
- `ALLOWED_HOSTS = Set.of("openapi.tossinvest.com", "openapi-ws.tossinvest.com")` 상수
- `AllowedHostInterceptor`(ClientHttpRequestInterceptor)에서 scheme=https, host 정확 일치 검사 → 위반 시 `DisallowedHostException`
- JDK HttpClient `Redirect.NEVER`, 연결 5초·읽기 10초 타임아웃
- 요청 파이프라인(`TossHttpClient.execute(...)`): 레이트리미터 대기 → 토큰 첨부 → (필요 시) `X-Tossinvest-Account`
  → 전송 → 봉투(`result`/`error`) 해제 → `X-Request-Id` 보존
- 401 `expired-token`/`token-revoked` → 토큰 1회 재발급 후 재시도(무한 루프 금지)
- 429 → `Retry-After` 존중 + 지수 백오프·지터, 최대 3회. 500 `maintenance` → 재시도 없이 즉시 안내

**토큰 (`client/auth/TokenManager`)**
- 싱글톤 빈, `ReentrantLock` + 캐시 파일 `FileChannel.lock()`(프로세스 간 잠금)
- 시작 시 캐시 토큰 재사용 → 401 시에만 재발급. 응답에 만료 정보가 있으면 60초 전 선제 갱신(P0-6에서 확인)
- 발급 실패 `access_denied` → "허용 IP 등록을 확인하세요"

**레이트리밋 (`client/ratelimit`)**
- `RateLimitGroup` enum(문서의 21개 그룹 + 기본 한도), `EndpointGroups`(경로→그룹, P0-6 매핑 기준)
- 그룹별 토큰버킷, 주입된 `Clock`으로 KST 09:00~09:10 ORDER_INFO=3 적용
- 응답 헤더 `X-RateLimit-Limit`이 더 낮으면 해당 그룹 한도를 낮춤

**에러 (`client/error`)**
- `TossApiException(status, code, message, requestId, data)` + 상태별 하위 클래스
- `UserMessages.of(code)`: 코드별 한국어 안내 + 다음 행동. 예) `insufficient-buying-power` →
  "매수 가능 금액이 부족합니다. get_buying_power로 가능 금액을 확인한 뒤 수량을 줄여 다시 미리보기 하세요."

## 7. MCP 도구 명세 (`tool/`, `@McpTool`)
공통 규칙
- 이름은 snake_case(`name` 속성 명시), description 첫 문장에 "언제 쓰는지", 이어서 입력 제약
- 파라미터는 `@McpToolParam(description=..., required=...)`
- 종목 입력은 항상 `symbol`. 이름만 알면 **먼저 `search_stock`** 하도록 description에 명시
- 반환은 record(금액은 `BigDecimal` → JSON 문자열 직렬화 + 통화 필드). `generateOutputSchema = true`
- 예외는 도구 경계에서 잡아 `UserMessages` 문장 + `requestId`를 담은 결과로 변환(스택트레이스 노출 금지)
- 외부 텍스트(종목명 등)는 데이터 필드로만 반환

### 조회 (P3) — `annotations = @McpTool.McpAnnotations(readOnlyHint = true)`
| 도구 | 입력 | 출력 요지 | 사용 API |
|---|---|---|---|
| `search_stock` | `query`, `market?`(KR/US), `limit=10` | symbol, 이름, 시장 | 종목 마스터 캐시 |
| `get_quote` | `symbol` | 현재가, 등락률, 상하한가, 최우선 호가 | prices, price-limits, orderbook |
| `get_candles` | `symbol`, `interval`, `count≤200` | OHLCV 배열 | candles |
| `get_exchange_rate` | – | USD/KRW | exchange-rate |
| `get_market_status` | `market` | 오늘 개장 여부, 장 시간, 현재 상태 | market-calendar |
| `get_portfolio` | `currency="KRW"` | 종목별 수량·평가금·손익·비중, 합계 | accounts, holdings, exchange-rate |
| `get_buying_power` | `market?` | 매수 가능 금액 | buying-power |
| `get_sellable_quantity` | `symbol` | 매도 가능 수량 | sellable-quantity |
| `list_orders` | `status?` | 주문 목록 | orders |
| `get_order` | `orderId` | 주문 상세 | orders/{id} |

### 주문 (P4) — `OrderTools`는 `@ConditionalOnProperty(tossinvest.enable-orders=true)`일 때만 빈 등록
| 도구 | hints | 동작 |
|---|---|---|
| `preview_order` | readOnly | 입력 검증 → 시세·수수료·예상금액 → 가드레일 → `previewId`(5분, 1회용) |
| `place_order` | destructive | `previewId`만 입력. 재검증(만료·사용여부·킬스위치·가드레일) → DRY_RUN이면 기록만 → 아니면 `clientOrderId`로 주문 |
| `preview_cancel` / `cancel_order` | readOnly / destructive | 취소도 동일 패턴 |
| `preview_modify` / `modify_order` | readOnly / destructive | 정정도 동일 패턴 (P4-6) |
- `place_order` 결과에는 항상 `dryRun`, `requestId`, 최종 주문 요약 포함

## 8. 가드레일 (`order/GuardrailService`)
검사 순서: 1 킬 스위치 → 2 `enable-orders` → 3 허용 종목 → 4 시장(US는 `allow-us`) → 5 주문유형(시장가) →
6 방향(매도) → 7 장 운영 시간 → 8 1회 금액(≤ max, < 1억 하드상한) → 9 일일 누적 금액·횟수 → 10 최소 주문 간격
- `GuardrailResult(boolean allowed, List<Violation> violations)` — 첫 위반에서 멈추지 않고 전부 수집
- 일일 카운터: KST 날짜 기준, `daily-counters.json`(파일 잠금), DRY_RUN 주문은 별도 집계

## 9. 감사로그 (`order/AuditLogger`)
- `audit.jsonl` 한 줄 = 한 이벤트: `ts, event(preview|place|cancel|modify|reject), dryRun, symbol, side, qty, price, estAmountKrw, guardrail, requestId, resultCode`
- 키·토큰·accountSeq는 절대 기록하지 않음. 금액은 **로컬 파일에만** 존재. `diagnose`는 건수만 출력

## 10. 아키텍처 규칙 테스트 (`ArchitectureTest`, ArchUnit) — P1-8
- `cli` 패키지 외에는 `System.out`/`System.err` 직접 접근 금지 (로깅 사용)
- `RestClient`/`java.net.http.HttpClient`/`URL.openConnection`/`Socket` 사용은 `client.http` 패키지만
- `tool` 패키지는 `client`의 `TossApi`, `dto`, `error`만 의존 (http·auth 내부 직접 접근 금지)
- `dto`, `money`, `order`, `tool`, `service`, `web` 패키지에 `double`/`float`/`Double`/`Float` 필드·파라미터·반환 금지
- (P9-3) `tool` 은 조회를 `service`, 주문을 `order` 로 — `TossApi`·`client.dto`·`stock`·`secret` 직접 의존 금지
- `System.getenv` 접근은 `config`, `secret` 패키지만
- `spring-boot-actuator`·텔레메트리 관련 클래스 의존 금지
- (RC-001) `web` 패키지는 `service`, `order`(주문 단계), `client.dto`·`client.error`, `config` 만 의존 — `client.http`·`client.auth`·`secret` 직접 접근 금지
- (RC-001) `tool` ↔ `web` 상호 의존 금지 (둘 다 `service` 를 거친다)
- (RC-001) `@CrossOrigin`·`CorsRegistry`·`CorsConfiguration` 사용 금지
- 위 RC-001·P9-3 규칙은 P9-6 에서 `ArchitectureTest` 9~12 로 구현(2026-11-27)

## 11. 설치·키 입력 UX (P1-3, P6-7)
사용자가 할 일은 "토스증권에서 키 발급·허용 IP 등록" + "키 붙여넣기" 두 가지뿐이어야 한다.

| 사용자 | 설치 | 키 보관 | 서버에 전달 |
|---|---|---|---|
| Claude Desktop | `.mcpb` 더블클릭 → 키 입력 화면 | Claude Desktop 이 OS 저장소에 보관(`sensitive: true`) | 환경변수 `TOSSINVEST_CLIENT_ID/SECRET` |
| Claude Code | jar/exe 더블클릭(설정 창) 또는 `setup` | `secret` 패키지가 OS 저장소에 저장 | `KeyringCredentialStore` 가 직접 읽음 |

- 키 조회 순서: `EnvCredentialStore` → `KeyringCredentialStore` (`SecretConfig`, `@Primary CompositeCredentialStore`)
- Claude Code 등록: `claude mcp add --transport stdio --scope user tossinvest -- <java -jar jar | exe> serve`.
  **키는 등록 명령에 넣지 않는다** (키체인에 있으므로). `--scope project`(저장소 `.mcp.json`) 사용 금지
- `.mcpb` 빌드: `./gradlew mcpb` → `build/mcpb/tossinvest-mcp-<버전>-win32.mcpb` (jpackage 크로스 빌드 불가 → OS별 빌드)
- (RC-001) **EXE(jpackage)·`.mcpb` 배포는 보류**(2026-11-27). 코드·`./gradlew mcpb` 태스크는 남겨 두지만 안내·검증 대상이 아니다.
  당분간 `java -jar tossinvest-mcp-<버전>.jar web`(브라우저) 또는 `serve`(Claude Code 등록)로 쓴다. 키 입력은 `setup`(콘솔/창) 그대로.

## 12. localhost 웹 모드 (`web`, RC-001)
> 보안 세부 규칙은 **[WEB_SECURITY.md](WEB_SECURITY.md)** 가 기준이다(RC-002·RC-003 승인). 아래는 요약이며, 다르면 WEB_SECURITY.md 를 따른다.

내 PC 브라우저에서만 쓰는 조회 화면 + REST API. **같은 네트워크·인터넷에서 접근할 수 없어야 하고, 내가 연 다른 웹사이트가 이 서버를 조작할 수 없어야 한다.**

### 12.1 기동 흐름
1. `java -jar ... web` → 키 존재 확인. 없어도 기동하고 "브라우저 화면에서 키를 입력하세요" 안내(RC-003 — 로그인 후 화면에서 키 입력)
2. `server.address=127.0.0.1`, `server.port=${TOSSINVEST_WEB_PORT:8765}` 로 기동. 번들 설정에 고정하며 외부 설정 차단(§2)으로 바꿀 수 없다
3. `LoopbackGuard`: 실제 바인딩 주소가 루프백이 아니면 **즉시 종료**. 포트 사용 중이면 안내 후 종료(다른 포트 자동 선택 안 함)
4. 일회용 접속 코드(256비트 `SecureRandom`, Base64URL) 생성 → 콘솔에 `http://127.0.0.1:8765/login?code=…` 출력(+ 기본 브라우저 자동 열기)
5. 브라우저가 `/login?code=` 접속 → 코드 검증(**1회용, 2분 만료**) → 세션 쿠키 발급 → `/` 로 리다이렉트(코드가 주소창·기록에 남지 않게)
6. 종료는 콘솔에서 Ctrl+C. 세션은 메모리에만 있으므로 재시작하면 다시 접속 코드가 필요하다
- 서버를 다시 열어 둔 채 브라우저를 닫았다가 열면: 쿠키(세션 쿠키, 만료 지정 없음)가 남아 있으면 그대로, 없으면 콘솔의 새 코드 URL 사용(`/login/new` 같은 재발급 엔드포인트는 두지 않음 — 콘솔에서 Enter 로 새 코드 출력)

### 12.2 보안 필터 (모든 요청, 순서대로) — 하나라도 실패하면 거부, 본문 없이 상태 코드만
| 순서 | 검사 | 실패 시 | 막는 공격 |
|---|---|---|---|
| 1 | 원격 주소가 루프백(`127.0.0.1`/`::1`) | 403 | 바인딩 실수 대비 이중 방어 |
| 2 | `Host` 가 정확히 `127.0.0.1:<port>` (`localhost` 불허 — RC-002 D1) | 421 | **DNS rebinding** |
| 3 | `Origin` 이 있으면 `http://127.0.0.1:<port>` 만(`null` 거부). `Sec-Fetch-Site` 가 있으면 `same-origin`·`none` 만. 메서드는 GET·HEAD + `POST /api/keys`(RC-003) 만(405) | 403 | 다른 사이트발 요청(CSRF) |
| 4 | `/login` 외 경로는 유효한 세션 쿠키 필요 | 401 (UI 는 안내 페이지) | 다른 프로그램·다른 탭의 무단 호출 |
| 5 | POST(현재 `/api/keys` 하나, 주문은 P9-8)는 `Content-Type: application/json` + 커스텀 헤더 `X-Tossinvest-Local: 1` + 바디 길이 명시·4KB 이하 | 415/403/411/413 | 폼 기반 CSRF, 남용 |
- 세션 쿠키: 이름 `tim_session`, 값 256비트 랜덤, `HttpOnly; SameSite=Strict; Path=/`(HTTP 루프백이라 `Secure` 없음). 서버 메모리에만 저장, 동시 세션 1개(새 로그인 시 이전 세션 폐기), 유휴 8시간·절대 24시간 만료(RC-002 D2). 틀린 코드 10회면 코드 무효(D4)
- **CORS 를 허용하지 않는다**: `Access-Control-Allow-*` 헤더를 절대 보내지 않음(ArchUnit §10 으로 강제)
- 응답 공통 헤더: `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`,
  `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`
- 에러 응답에 스택트레이스·내부 경로 금지(Spring 기본 에러 페이지 끔, `server.error.include-*=never`)
- 접근 로그(Tomcat access log) 끔. 앱 로그에 쿠키·접속 코드·쿼리스트링 원문을 남기지 않음(마스킹 대상 추가)

### 12.3 REST API (조회) — 경로 접두 `/api`, JSON, 금액은 문자열 decimal + `currency`
| 메서드·경로 | 입력 | 출력 요지 | 공유 서비스 (MCP 도구) |
|---|---|---|---|
| `GET /api/status` | – | 버전, 키 설정 여부·환경변수 키 여부(값 X), 주문 기능 켜짐 여부, DRY_RUN 여부 | – |
| `POST /api/keys` (RC-003) | `{clientId, clientSecret}` | `{saved, connected, message}` — 키 값은 되돌려 주지 않음. 400 입력 오류·409 환경변수 키 사용 중·429 1분 5회 초과 | `service.KeySetupService` (setup 과 같은 자격 증명 저장소) |
| `GET /api/stocks/search` | `query`, `market?`, `limit≤10` | symbol, 이름, 시장 | `search_stock` |
| `GET /api/quote` | `symbol` | 현재가 등 (§7 `get_quote` 와 동일 필드) | `get_quote` |
| `GET /api/buying-power` | `currency?` | 매수 가능 금액 | `get_buying_power` |
| `GET /api/orders` | `status=OPEN\|CLOSED` | 주문 목록 | `list_orders` |
| `GET /api/portfolio` | – (원화 합산만) | 보유 종목·평가·손익·비중, 통화별 합계 + 원화 합산(표준 환율, 실패 시 null) (P3-4) | `get_portfolio` |
- 에러 형식: `{"error":{"code":"…","message":"<UserMessages 한국어>","requestId":"…"}}`, HTTP 상태는 토스 상태를 그대로 쓰지 않고 정규화(`web.api.ApiErrorAdvice`): 토스 401·403·빈 응답 → 502(화면의 401=세션 만료와 구분), 429 → 429, 404 → 404, 400·422 → 400, 5xx → 503, 네트워크 → 503, 키 없음 → 409
- 입력 검증: `symbol` 은 영숫자·`.`·`-` 1~20자(쉼표로 10개까지), `query` 1~50자, `limit` 1~10, `market` KR·US, `currency` KRW·USD, `status` OPEN·CLOSED. 잘못된 입력은 400(입력값을 응답에 되돌려 주지 않음). 금액은 문자열 decimal

### 12.4 주문 REST (P9-8, **조회 완료·P4-1 영속화 후 별도 승인**)
- `POST /api/orders/preview` → `previewId`, `POST /api/orders/place` `{previewId}` — MCP 와 같은 `OrderService`·가드레일·감사로그·DRY_RUN
- `enable-orders=false` 면 엔드포인트 자체를 등록하지 않음(404). UI 는 미리보기 내용을 보여 주고 사용자가 [주문 확정]을 눌러야만 place 호출
- 1억 이상 `confirmHighValueOrder` 는 코드가 자동 true 로 설정하지 않음(절대 원칙 7)

### 12.5 로컬 웹 UI (`src/main/resources/static/`)
- 정적 HTML + 순수 JS + CSS, **빌드 도구·npm·CDN·웹폰트·외부 이미지 없음**(CSP `default-src 'self'` 로 강제). 인라인 스크립트 금지
- 화면: 상태 표시줄(주문 OFF/DRY_RUN 배지) · 종목 검색 → 현재가 · 매수 가능 금액 · 주문 내역(미체결/체결)
- 금액·종목명은 `textContent` 로만 렌더링(HTML 삽입 금지)

### 12.6 테스트
- MockMvc: 잘못된 Host → 421, 교차 Origin/`Sec-Fetch-Site: cross-site` → 403, 쿠키 없음 → 401, 코드 재사용·만료 → 거부, 보안 헤더 존재, CORS 헤더 없음
- 기동 테스트: `web` 모드가 127.0.0.1 에만 리슨(비루프백 주소로 연결 실패), `serve`·`setup` 모드는 어떤 포트도 열지 않음
- 마스킹: 로그에 `tim_session` 값·접속 코드가 남지 않음
- **확인 결과 (2026-11-27, P9-1, 실제 jar)**: `spring-ai-starter-mcp-server` 2.1.0 은 `spring-ai-autoconfigure-mcp-server-common` + `spring-ai-mcp` + `spring-ai-mcp-annotations` 만 끌어오며,
  HTTP 전송(SSE·Streamable HTTP)은 별도 아티팩트(`spring-ai-starter-mcp-server-webmvc` 등)라 클래스패스에 없다.
  다만 `McpServerAutoConfiguration` 에 servlet 환경 전용 빈(`servletMcpSyncServerCustomizer`)이 있으므로, web 모드는
  `spring.ai.mcp.server.enabled=false`(클래스 수준 `@ConditionalOnProperty`, 기본 true)로 MCP 자동 구성 전체를 끈다.
  `WebModeStartupTest` 가 `McpSyncServer` 빈 없음과 `/mcp`·`/sse` 404 를 검증한다.
- Boot 4 에서 에러 응답 설정은 `server.error.*` → **`spring.web.error.*`** 로 바뀌었다(옛 키는 error 수준 deprecated). `application.yml` 은 새 키를 쓴다.
- Boot 4.2 의 MVC 스타터 이름은 `spring-boot-starter-webmvc`(추가된 런타임 아티팩트: Tomcat embed core·websocket 11.0.27, spring-webmvc 7.1.0, spring-boot-{webmvc,web-server,servlet,tomcat,http-converter}).

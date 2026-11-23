# 아키텍처 (Java / Spring Boot)

## 1. 전체 구조
```
[Claude Desktop / Claude Code]  ← "삼성전자 3주 7만원에 사줘"
        │ MCP (stdio, 로컬 프로세스: java -jar ... serve)
        ▼
┌─ tossinvest-mcp (Spring Boot, web-application-type=none) ──┐
│ tool/      @McpTool 빈 (조회 / 미리보기 / 주문)               │
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
| `serve` | 전체 | 켬(`spring.ai.mcp.server.stdio=true`) | **MCP 전용** |
| `setup` / `reset-keys` | 최소(MCP 끔) 또는 Spring 없이 실행 | 끔 | 안내 문구만 |
| `diagnose` | MCP 끔 | 끔 | 진단 결과(민감정보 없음) |
| (인자 없음/`--help`) | 없음 | 끔 | 사용법 |

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
├─ .claude/settings.json                 # docs/setup/claude-settings.json을 사람이 복사
├─ .github/workflows/ci.yml, release.yml  # P6
├─ .github/ISSUE_TEMPLATE/bug_report.md   # P6
├─ docs/  (PRINCIPLES, ARCHITECTURE, TASKS, TOSS_API_NOTES, VIBE_CODING_GUIDE, setup/, api/ ← gitignore)
└─ src/
   ├─ main/java/io/github/<깃허브ID>/tossmcp/
   │  ├─ TossMcpApplication.java          # main: 모드 분기, 외부 설정 차단
   │  ├─ cli/        SetupCommand, ResetKeysCommand, DiagnoseCommand, Usage
   │  ├─ config/     TossProperties(@ConfigurationProperties record), AppPaths, KillSwitch, ClockConfig
   │  ├─ secret/     CredentialStore(인터페이스), KeyringCredentialStore, EnvCredentialStore, CompositeCredentialStore
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
   │  └─ tool/       MarketTools, AccountTools, OrderTools  (@Component + @McpTool)
   ├─ main/resources/ application.yml, logback-spring.xml
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
- `dto`, `money`, `order`, `tool` 패키지에 `double`/`float`/`Double`/`Float` 필드·파라미터·반환 금지
- `System.getenv` 접근은 `config`, `secret` 패키지만
- `spring-boot-actuator`·텔레메트리 관련 클래스 의존 금지

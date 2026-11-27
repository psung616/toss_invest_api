# 작업 목록 (TASKS) — Java / Spring Boot

> 사용법: Claude Code에 "**P1-3 진행해줘**"처럼 작업 ID 하나씩 지시한다.
> 표시: 👤 사람이 직접 · 🤖 Claude Code · 🔍 사람이 diff를 한 줄씩 직접 리뷰
> 공통 완료 기준(모든 🤖 작업): `./gradlew check` 통과 + 이 문서 체크박스 체크
> 요구사항 변경은 [CHANGES.md](CHANGES.md) 에 `RC-NNN` 으로 먼저 기록하고, 바뀐 항목에 `(RC-NNN)` 표시. 현재 우선순위: **P9(RC-001 localhost 웹 모드)**

## 선행 구현 현황 (2026-11-23 초기 뼈대 커밋)
초기 뼈대에서 아래 작업의 **초안 코드**가 먼저 들어갔다. 체크박스는 각 작업의 요구사항·테스트를 모두 채운 뒤에 체크한다.
코드 안의 `TODO(Px-y)` 주석이 남은 일이다.

| 작업 | 상태 |
|---|---|
| P0-3 | 🤖 완료 — 패키지 `io.github.psung616.tossmcp`, `./gradlew check` 통과, `--help` 모드 4개 표시. 👤 확인 대기 (Windows 콘솔에서 한글이 깨지면 `chcp 65001`) |
| P0-6 | 일부 — Spring AI 2.1.0 기준 `org.springframework.ai.mcp.annotation.McpTool` / `McpToolParam` / `McpTool.McpAnnotations` 확인. openapi.json v1.2.28 기준 prices·accounts·buying-power·orders 경로/필드 반영 |
| P1-1 | 초안 — `config/AppPaths` (OS별 경로, `tossinvest.home`). 테스트 없음 |
| P1-2 | 초안 — `TossProperties`, `KillSwitch`, Clock(KST), 1억 클램프, 외부 설정 차단(`main`). 클램프 외 테스트 없음 |
| P1-3 | 🤖 완료 — JNA 직접 바인딩(Windows), `security`(macOS)/`secret-tool`(Linux), setup 마법사(콘솔+창), 연결 테스트, Claude Code 자동 등록, `reset-keys`. 테스트 9개(실제 자격 증명 관리자 왕복 포함). macOS/Linux 실기기 미검증, 토큰 캐시 삭제는 P2-1 이후. 👤 setup 직접 실행 대기 |
| P6-7 | 🤖 완료 — `./gradlew mcpb` → jpackage 앱 이미지 + `.mcpb`(win32). JDK 없이 실행·환경변수 키 전달·도구 호출 확인. 👤 Claude Desktop 설치 확인 대기 |
| P1-4 | 초안 — 파일(사용자 폴더) + stderr appender, stdout appender 없음. 마스킹 미구현 |
| P1-5 | 초안 — `AllowedHostInterceptor` + 테스트 8개, `Redirect.NEVER`, 타임아웃 |
| P1-6 | 초안 — 봉투 파싱, `UserMessages` 일부 코드만 |
| P2-1 | ✅ 완료 (2026-11-24) |
| P2-3 | 초안 — 토큰·계좌 헤더·봉투 해제·401 재시도. 레이트리밋·429 재시도 미구현 |
| P2-4~6 | 초안 — 일부 DTO·`TossApi` 메서드(getPrices, getAccounts, getBuyingPower, listOrders, createOrder) |
| P3-3/P3-4 | 초안 — `get_quote`, `get_buying_power`, `list_orders` (문자열 반환) |
| P4-1~4 | 초안 — `GuardrailService`(장 시간 제외), 메모리 `DailyCounterStore`, `PreviewStore`, `AuditLogger`, `preview_order`/`place_order`. 테스트 9개 |

---

## P0. 준비

- [ ] **P0-1 👤 토스증권 Open API 신청과 키 발급**
  - 토스증권 WTS 로그인 → 설정 > Open API → 신청, client_id/secret 발급
  - 허용 IP에 현재 PC 공인 IP 등록 (PowerShell: `(Invoke-WebRequest -UseBasicParsing https://api.ipify.org).Content`)
  - 키는 **아직 어디에도 저장하지 말 것** (P1-3 이후 `setup` 명령으로 저장)

- [ ] **P0-2 👤 개발 도구 설치**
  - JDK 21 이상(예: Temurin), Git(Git Bash 포함), VS Code + "Extension Pack for Java", Claude Code
  - Node.js(LTS) — MCP Inspector(`npx`) 실행용
  - 확인: `java -version`, `git --version`, `claude --version`, `node --version`
  - 👤 `.claude/settings.json` 배치 (VIBE_CODING_GUIDE §0)

- [ ] **P0-3 🤖 프로젝트 스캐폴딩**
  - 👤에게 패키지 루트(`io.github.<깃허브ID>.tossmcp`) 확인 후 시작
  - Gradle Wrapper 포함 Spring Boot 4.x + Java 21 toolchain, `spring-ai-bom`으로 Spring AI 2.x 최신 안정 버전 관리
  - 의존성: `spring-ai-starter-mcp-server`, `RestClient`·JSON에 필요한 최소 Spring Boot 스타터(Boot 4 모듈 구조에 맞게 확인),
    테스트: `spring-boot-starter-test`, ArchUnit. Spotless(palantir-java-format) 플러그인
  - `bootJar` 산출물 이름 `tossinvest-mcp-<버전>.jar`
  - `TossMcpApplication.main`: 모드 분기(`serve`/`setup`/`reset-keys`/`diagnose`/사용법) 뼈대, 아직 "not implemented"
  - 완료 기준: `./gradlew check` 통과, `java -jar build/libs/... --help`가 모드 4개 표시(👤 확인)

- [x] **P0-4 🤖🔍 빌드 공급망·Git 보안** — 2026-11-25 완료: `gradle.lockfile`(전 configuration), `verification-metadata.xml`(sha256, 플러그인·Spotless 포함), 체크섬 변조 시 빌드 실패 확인. gitleaks 훅은 미적용(선택)
  - `dependencyLocking { lockAllConfigurations() }` + `./gradlew dependencies --write-locks`
  - `./gradlew --write-verification-metadata sha256 help`로 `gradle/verification-metadata.xml` 생성
  - `.gitignore` 확인(이미 존재), (선택) gitleaks pre-commit 훅
  - 완료 기준: 잠금 파일·검증 메타데이터 생성, `./gradlew check` 통과
  - 👤 GitHub 저장소 생성 시 Secret scanning + Push protection 켜기

- [x] **P0-5 🤖 공식 스펙 다운로드 태스크** — 2026-11-25 완료: `fetchSpecs`(JDK HttpClient, 리다이렉트 금지, 호스트 검사), docs/api/ 는 git 에 안 잡힘 확인
  - Gradle 태스크 `fetchSpecs`: openapi.json, asyncapi.json → `docs/api/` (토스증권 문서 호스트만)
  - 👤 실행: `./gradlew fetchSpecs` → `docs/api/openapi.json` 존재, `git status`에 안 잡힘

- [x] **P0-6 🤖 스펙·프레임워크 확인 노트** — 2026-11-25 완료·👤 승인: TOSS_API_NOTES "스펙 확인 결과"(43개 operation 그룹 표, 토큰 expires_in, 주문 바디, clientOrderId, confirmHighValueOrder, 금액=문자열 decimal, Spring AI 패키지)
  - openapi.json을 읽고 `docs/design/TOSS_API_NOTES.md` 하단에 "스펙 확인 결과" 추가: 엔드포인트→레이트리밋 그룹 표,
    토큰 응답 필드(만료 정보), 주문 요청 바디, `clientOrderId` 형식, `confirmHighValueOrder` 위치,
    종목 마스터 응답 구조, 금액 필드 타입(문자열/숫자)
  - 실제 의존성 jar 기준으로 `@McpTool`/`@McpToolParam`/`@McpTool.McpAnnotations`의 **정확한 패키지명**,
    STDIO 서버 필수 프로퍼티(ARCHITECTURE §4 초안과 대조)를 확인해 기록
  - 스펙·문서에 없는 항목은 "없음"으로 명시(추측 금지)
  - 완료 기준: 👤 표를 읽고 승인

---

## P1. 기반 계층 (네트워크 없이 테스트 가능)

- [ ] **P1-1 🤖 사용자 폴더 (`config/AppPaths`)**
  - ARCHITECTURE §5 표대로 OS별 경로, 디렉터리 자동 생성, `tossinvest.home` 시스템 프로퍼티 오버라이드
  - 테스트: OS별 경로 계산(환경값 주입), 저장소 경로를 반환하지 않음

- [ ] **P1-2 🤖 설정 (`config/TossProperties`, `application.yml`, `KillSwitch`, `ClockConfig`)**
  - ARCHITECTURE §4 표의 모든 키를 `application.yml`에서 환경변수로 명시 매핑, 금액 `BigDecimal`
  - `main`에서 외부 설정 파일 로딩 차단(`spring.config.location=classpath:/application.yml`)
  - 1억 하드 상한 클램프 + 경고, `KillSwitch.isActive()`, `Clock` 빈(Asia/Seoul)
  - 테스트: 기본값(`enableOrders=false`, `dryRun=true`, `allowedSymbols` 비어 있음), 환경변수 반영, 클램프,
    작업 폴더에 `application.yml`을 둬도 무시됨

- [ ] **P1-3 🤖🔍 자격 증명 저장소 (`secret/`) + `setup` 마법사 / `reset-keys`**
  - (2026-11-23 결정) java-keyring 은 유지보수 중단 → JNA 직접 바인딩 승인됨 (docs/governance/DECISIONS.md)
  - setup 마법사: 키 입력 → OS 저장소 저장 → 토큰 발급으로 연결 테스트(허용 IP 안내) → Claude Code 자동 등록(user scope, 키 미포함)
  - 인자 없이 더블클릭(jar 또는 설치형 exe)하면 같은 내용의 설정 창(Swing)
  - `CredentialStore` 인터페이스, `EnvCredentialStore`(우선) → `KeyringCredentialStore` 조합. 서비스명 `tossinvest-mcp`
  - 둘 다 없으면 "`setup`을 실행하세요" 안내 예외
  - `setup`: `System.console().readPassword()`로 입력(화면 표시 안 됨, console 없으면 안내 후 종료), 저장 후 "저장됨"만 출력.
    `char[]`는 사용 후 즉시 0으로 덮어쓰기. `reset-keys`: 삭제 + 토큰 캐시 삭제
  - 테스트: 메모리 가짜 저장소로 우선순위·에러 검증, 어떤 출력·예외 메시지에도 키 값 없음
  - 👤 완료 후 직접 `java -jar ... setup` 실행해서 키 저장 (Claude Code에 시키지 말 것)

- [x] **P1-4 🤖🔍 로깅과 마스킹 (`logback-spring.xml`, `logging/MaskingConverter`)** — 2026-11-25 완료: `Masking`(JSON 키·key=value·Bearer·계좌 헤더·`tsck_` 접두어), `%maskedMsg`/`%maskedEx`(예외 메시지도 마스킹), 실제 설정 파일로 stdout 무출력 확인. audit.jsonl 은 마스킹 제외(P4-2). 테스트 15개
  - appender: 회전 파일(`server.log`, 5MB×3) + stderr(`ConsoleAppender` target=`System.err`). **stdout appender 금지**
  - 마스킹: Bearer 토큰, client_secret/id, `X-Tossinvest-Account` 값, JSON 키
    `access_token|client_secret|accountSeq|quantity|amount|price|balance` 계열 값 → `***`
  - 테스트: 패턴별 마스킹, 로그 출력 시 stdout으로 아무것도 안 나감

- [ ] **P1-5 🤖🔍 허용목록 HTTP (`client/http/AllowedHostInterceptor`, RestClient 팩토리)**
  - https + 정확한 호스트 일치만 통과, JDK HttpClient `Redirect.NEVER`, 타임아웃
  - 테스트: 허용 호스트 통과, `example.com`·`openapi.tossinvest.com.evil.io`·IP 직접·http 스킴 거부

- [ ] **P1-6 🤖 에러 계층 (`client/error/`)**
  - 봉투 파싱 → `TossApiException` 하위 클래스, `requestId` 보존, `UserMessages` 한국어 매핑(TOSS_API_NOTES 전체 코드 + 기본 문구)
  - 테스트: 코드별 매핑, 봉투 아닌 응답(HTML·빈 바디) 처리

- [ ] **P1-7 🤖 금액 (`money/Money`)**
  - `BigDecimal` 생성은 `String`/`long`만 허용, KRW 반올림 규칙, USD→KRW 환산, 표시 포맷("1,234,500원", "$12.34")
  - 테스트: 환산 정확도, 경계값, `new BigDecimal(double)` 미사용

- [x] **P1-8 🤖🔍 아키텍처 규칙 테스트 (`ArchitectureTest`)** — 2026-11-24 완료: §10 6개 + createOrder 호출 제한 + command 파서 순수성 = 8개, 위반 코드로 실패 확인
  - ARCHITECTURE §10 규칙 6개를 ArchUnit으로 작성 (이후 모든 작업에서 자동 검사됨)
  - 완료 기준: 일부러 규칙을 어기는 임시 코드로 테스트가 실패하는 것을 확인한 뒤 원복

---

## P2. API 클라이언트

- [x] **P2-1 🤖🔍 토큰 매니저 (`client/auth/`)** — 2026-11-24 완료: `TokenCacheFile`(cache/token.json, JVM 락 + FileLock, Client ID 는 SHA-256 해시만), 실패 토큰만 무효화(다른 프로세스가 갱신한 토큰은 재사용), setup 연결 테스트도 캐시에 기록, reset-keys 가 캐시 삭제. 테스트 8개
  - `POST /oauth2/token`(form-encoded), 싱글톤 + `ReentrantLock` + 캐시 파일 `FileChannel.lock()`
  - 401 `expired-token`/`token-revoked` → `invalidate()` 후 1회 재발급. 만료 정보가 스펙에 있으면 60초 전 선제 갱신
  - `access_denied` → 허용 IP 안내
  - 테스트(`MockRestServiceServer`): 캐시 재사용, 동시 10개 스레드에서도 발급 1회, 401 후 재발급 1회만, 재발급 실패 시 예외

- [x] **P2-2 🤖 레이트리미터 (`client/ratelimit/`)** — 2026-11-25 완료: `RateLimitGroup`(21개 그룹·피크 한도), `RateLimiter`(GCRA=토큰버킷, 정수 마이크로초, 대기 방식), 09:00~09:10 ORDER_INFO 3, `X-RateLimit-Limit` 은 낮출 때만·60초 유효. 그룹은 `EndpointGroups` 대신 `TossApi` 메서드마다 명시. 토큰 발급(AUTH)은 리미터 미적용. 테스트 8개
  - `RateLimitGroup` enum, `EndpointGroups`(P0-6 표), 그룹별 토큰버킷, 주입 `Clock`으로 09:00~09:10 규칙, `X-RateLimit-Limit` 반영
  - 테스트: 초당 한도 준수(가짜 Clock), 시간대 전환, 헤더 반영

- [x] **P2-3 🤖 요청 파이프라인 (`client/http/TossHttpClient`, `RetryPolicy`)** — 2026-11-25 완료: 429 재시도 max(Retry-After, 1·2·4s)+지터 최대 3회(상한 30초), 401 재발급과 결합, 정상·오류 응답 헤더로 한도 반영, StockMasterCache 수동 sleep 제거, 스프링 컨텍스트 연결 테스트 추가. 테스트 14개
  - `execute(method, path, group, accountScoped, query, body, responseType)`: 리미터 → 토큰 → 계좌 헤더 → 전송 → 봉투 해제
  - 429 재시도(Retry-After, 백오프·지터, 최대 3회), 401 재발급 연동, `X-Request-Id` 보존
  - 테스트: 성공 봉투, 실패 봉투→예외, 429 후 성공, 401 후 성공, 계좌 헤더 누락 방지

- [ ] **P2-4 🤖 DTO (`client/dto/`)**
  - P0-6 분석 기준으로 사용하는 응답만 record 작성(금액 `BigDecimal`), 알 수 없는 필드 무시
  - (선택) openapi-generator로 초안 생성 후 정리 — 생성기 도입은 👤 승인 후, 생성 코드를 그대로 커밋하지 않음
  - 테스트: 가짜 픽스처 역직렬화

- [ ] **P2-5 🤖 시세·종목·시장 (`TossApi` 1부)**
  - `getPrices`, `getOrderbook`, `getPriceLimits`, `getCandles`, `listStocks`, `getStockWarnings`, `getExchangeRate`, `getMarketCalendar`
  - 테스트: 경로·쿼리·그룹 정확성

- [ ] **P2-6 🤖 계좌·주문 조회 (`TossApi` 2부)**
  - `getAccounts`(accountSeq 캐시), `getHoldings`, `getBuyingPower`, `getSellableQuantity`, `listOrders`, `getOrder`, `getCommissions`
  - 테스트: `/accounts`는 1회만 호출, 계좌 헤더 첨부

- [ ] **P2-7 🤖 실 API 조회 점검 (`liveTest`)**
  - `src/test/java/.../live/ReadOnlyLiveTest` `@Tag("live")`, 기본 `test`에서 제외, 별도 Gradle 태스크 `liveTest`
  - 토큰 → 계좌 수 → 보유 종목 수 → 005930 현재가 존재 여부 → 환율 → 장 운영 상태. **출력은 개수·성공 여부만**
  - 👤 직접 실행: `./gradlew liveTest`

---

## P3. 조회용 MCP 서버

- [ ] **P3-1 🤖🔍 `serve` 모드와 stdout 무결성**
  - ARCHITECTURE §4의 serve 전용 설정, 시작 시 키 존재 확인(없으면 stderr 안내 후 종료 코드 1)
  - `ServeStdoutIT`: jar를 서브프로세스로 실행 → stdin에 MCP `initialize` 요청 → **stdout의 모든 줄이 JSON-RPC로 파싱되는지** 검증
    (키 없이도 initialize까지는 동작하도록 테스트용 가짜 자격 증명 주입)
  - 기동 시간 측정 기록(목표: 일반 PC에서 수 초 이내)
  - 👤 MCP Inspector 확인: `npx @modelcontextprotocol/inspector java -jar build/libs/tossinvest-mcp-<버전>.jar serve`

- [x] **P3-2 🤖 종목 마스터 + `search_stock`** — 2026-11-24 완료: `GET /api/v1/stocks/all`(KOSPI·KOSDAQ·NASDAQ·NYSE·AMEX, 마켓 간 1.1초 대기) → cache/stock-master.json 24시간, `StockSearch`(심볼>이름 정확>접두>부분, 보통주·국내 우선, 공백·대소문자 무시), 테스트 7개
  - `StockMasterCache`: `/stocks` 결과를 cache 폴더에 저장(TTL 24시간, STOCK_ALL 1회/초 고려)
  - `StockSearch`: 심볼 정확 일치 > 이름 접두 > 부분 일치, 한글·영문·공백 정규화, 최대 10개
  - 테스트: 가짜 마스터로 "삼성", "samsung", "005930", "NVDA" 검색

- [ ] **P3-3 🤖 시세 도구 (`tool/MarketTools`)**
  - `get_quote`, `get_candles`, `get_exchange_rate`, `get_market_status` (ARCHITECTURE §7), readOnlyHint
  - description에 "종목명만 알면 먼저 search_stock" 명시, 예외 → `UserMessages`
  - 테스트: `TossApi`를 Mockito로 대체한 단위 테스트

- [ ] **P3-4 🤖 계좌 도구 (`tool/AccountTools`)**
  - `get_portfolio`(KR+US 합산, 환율 환산, 종목별 비중·손익, `BigDecimal`), `get_buying_power`, `get_sellable_quantity`, `list_orders`, `get_order`
  - 테스트: 혼합 포트폴리오 환산 정확도, 빈 계좌

- [ ] **P3-5 🤖 `diagnose` 모드**
  - 출력: 버전, OS, Java 버전, 키 설정 여부(값 X), 토큰 캐시 존재, 연결 테스트 결과(성공/에러 코드만),
    최근 에러 코드 5개와 `X-Request-Id`, 주문 설정 플래그, 감사로그 건수, KILL 파일 경로
  - 테스트: 출력에 키·토큰·accountSeq·금액·보유 정보가 절대 없음

- [ ] **P3-6 👤 Claude Code에 등록하고 직접 써보기**
  - `./gradlew bootJar` 후
    `claude mcp add --transport stdio tossinvest -- java -jar "<프로젝트 절대경로>\build\libs\tossinvest-mcp-<버전>.jar" serve`
  - `/mcp`로 연결 확인 → "내 포트폴리오 요약해줘", "엔비디아 지금 얼마야?", "오늘 미국장 열려?"
  - 어색한 응답·잘못된 도구 선택 메모 → P3-7 입력

- [ ] **P3-7 🤖 도구 설명·출력 튜닝** — P3-6 메모 기반으로 description·파라미터 설명·출력 필드 정리

---

## P4. 주문 (DRY_RUN)

- [ ] **P4-1 🤖🔍 가드레일 (`order/GuardrailService`, `DailyCounterStore`)** — 2026-11-24: 장 운영 시간 검사 완료(`TossMarketHours`, market-calendar/KR 통합 세션, 날짜별 캐시, 조회 실패 시 거부, 다음 개장 시각 안내, 테스트 7개). **남음: 일일 카운터 파일 영속화**
  - ARCHITECTURE §8 10개 검사, 위반 전부 수집, 일일 카운터 파일(KST, 파일 잠금)
  - 테스트: 검사별 통과/위반, 날짜 변경 리셋(가짜 Clock), 1억 하드상한

- [ ] **P4-2 🤖 감사로그 (`order/AuditLogger`)** — ARCHITECTURE §9 스키마, 마스킹 대상 미기록 테스트

- [ ] **P4-3 🤖🔍 `PreviewStore` + `preview_order`**
  - `previewId`(UUID) → (정규화 파라미터, 해시, 만료 5분, 사용 여부), `ConcurrentHashMap`
  - 입력 검증 → 종목 확인 → 현재가·상하한가(범위 밖 지정가 거부) → 예상금액(KRW)·수수료 → 가드레일 → 결과(요약 문장, `allowed`, `violations`, `previewId`)
  - 테스트: 정상, 가격 범위 밖, 가드레일 위반, 만료

- [ ] **P4-4 🤖🔍 `place_order`**
  - `OrderTools`는 `enable-orders=true`일 때만 빈 등록
  - `previewId`만 입력 → 재검증(존재·만료·미사용·킬스위치·가드레일 재실행) → 사용 처리 → DRY_RUN이면 감사로그 + `dryRun=true`
    → 아니면 `clientOrderId`(P0-6 형식)로 주문. 409/422 → 사용자 메시지, 결과에 `requestId`
  - 테스트: 같은 previewId 두 번 → 두 번째 거부, DRY_RUN에서 주문 API 호출 0회(`MockRestServiceServer` 검증), 킬스위치,
    `enable-orders=false`면 `place_order` 도구 미등록

- [ ] **P4-5 🤖 취소 (`preview_cancel` / `cancel_order`)** — 이미 체결·`cancel-restricted`·없는 주문 처리
- [ ] **P4-6 🤖 정정 (`preview_modify` / `modify_order`)** — 정정 후 금액으로 가드레일 재검사

- [ ] **P4-7 👤 DRY_RUN 대화 테스트**
  - `claude mcp remove tossinvest` 후 `--env TOSSINVEST_ENABLE_ORDERS=true --env TOSSINVEST_ALLOWED_SYMBOLS=005930`을 붙여 재등록 (DRY_RUN은 기본 true)
  - "삼성전자 1주 지정가 OO원에 사줘" → 미리보기 → 확정 → `audit.jsonl`에 dryRun 기록 확인
  - 한도 초과·비허용 종목·매도·시장가 요청이 각각 거부되는지 확인

---

## P5. 실주문 (소액, 사람 주도)
- [ ] **P5-1 👤 첫 실주문** — `TOSSINVEST_DRY_RUN=false`, 허용 종목 1개, `MAX_ORDER_KRW`=1주 가격 수준.
  **체결되지 않을 지정가**(현재가보다 충분히 낮지만 하한가 이상)로 1주 매수 → `list_orders` 확인 → 취소 → 토스증권 앱에서 대조
- [ ] **P5-2 👤 실제 체결 1회** — `get_portfolio` 반영 확인
- [ ] **P5-3 🤖 발견된 문제 수정** — 이슈를 작업 단위로 이 문서에 추가 후 진행

---

## P6. 공개 배포 준비
- [ ] **P6-1 🤖 README.md** — 비공식·면책 문구, JDK 21 설치 안내, 설치 방법 2가지:
  (A) 태그 소스 직접 빌드 `git clone --branch vX.Y.Z ...` → `./gradlew bootJar` (가장 신뢰할 수 있는 방법)
  (B) GitHub Release의 jar + SHA-256 체크섬·아티팩트 증명 확인.
  토스증권 Open API 신청·허용 IP·`setup` 단계별 가이드, Claude Desktop(`"command": "java", "args": ["-jar", "<jar 경로>", "serve"]`)·Claude Code 등록 예시,
  설정 표, "외부 통신은 토스증권뿐", "도구 결과는 사용자 본인의 AI 클라이언트로 전달됨", 문제 시 `diagnose`만 공유
- [ ] **P6-2 🤖 LICENSE(MIT), SECURITY.md, 이슈 템플릿** — "로그·스크린샷·키 붙여넣기 금지, diagnose 결과만"
- [ ] **P6-3 🤖 GitHub Actions** — `ci.yml`(`./gradlew check`, Gradle Wrapper 검증 액션, 실 API 호출 없음),
  `release.yml`(태그 푸시 시 bootJar 빌드 → Release 업로드 + SHA-256 + 아티팩트 증명)
- [ ] **P6-4 🤖🔍 공개 전 감사** — 실데이터·키·개인 경로·계좌 흔적 검색, gitleaks 전체 히스토리 스캔,
  런타임 의존성 목록 검토, 허용 호스트 외 URL 문자열 검색
- [ ] **P6-7 🤖 원클릭 설치 패키지 (`jpackageImage`, `mcpb` Gradle 태스크)** — ⏸ 보류(2026-11-27, RC-001: EXE 대신 `web` 모드). 코드·태스크는 유지
  - jpackage app-image(Java 런타임 포함, `--win-console`) + `packaging/mcpb/manifest.json`(MCPB v0.3, `server.type=binary`)
  - `user_config`: client_id/client_secret(`sensitive`), enable_orders, dry_run, allowed_symbols, max_order_krw → 환경변수로 전달
  - 남은 일: jlink 모듈 축소(현재 앱 이미지 ~180MB, .mcpb ~80MB), 아이콘(`packaging/mcpb/icon.png`), macOS 빌드(`darwin`), 코드 서명 검토
- [ ] **P6-8 👤 Claude Desktop 에서 .mcpb 설치 확인** — ⏸ 보류(RC-001) — 더블클릭 → 키 입력 화면 → 설치 → "삼성전자 현재가" 대화
  - 확인: 키 입력이 가려지는지, 콘솔 창이 뜨지 않는지(`--win-console` 영향), 설정 변경 후 재시작 반영

- [ ] **P6-5 👤 깨끗한 환경 설치 테스트** — 다른 PC(또는 새 Windows 사용자 계정)에서 README만 보고 설치·실행
- [ ] **P6-6 👤 public 전환 + `v0.1.0` 태그** — main 보호, 2FA, Dependabot

---

## P7. 고도화 (선택)
- [ ] **P7-1** 조건주문 도구(SINGLE/OCO/OTO) — preview 패턴, "OCO/OTO는 종목당 1개" 안내
- [ ] **P7-2** MCP Prompts — "오늘 포트폴리오 점검", "목표 비중 대비 리밸런싱 계산"(주문 없이 계산만)
- [ ] **P7-3** WebSocket 백그라운드 — 체결 알림, 60초 PING, 재연결 후 재동기화, 연결 2개 제한 준수
- [ ] **P7-4** 기동 시간 최적화 — Spring Boot CDS(Class Data Sharing) 적용 검토, (심화) GraalVM 네이티브 이미지

---

## P9. localhost 웹 모드 (RC-001, 2026-11-27) — **다음 개발 우선순위**
> 근거: [CHANGES.md RC-001](CHANGES.md) · 설계: ARCHITECTURE §12 · 원칙: PRINCIPLES "localhost 웹 보호"
> 순서: P9-1 → 2 → **9**(RC-003) → 3 → 4 → 5 → 6 → 7(👤) → (P3-4·P4-1 영속화 후) P9-8. 각 단계 완료 기준에 `./gradlew check` + 보안 테스트 포함

- [x] **P9-1 🤖🔍 의존성 추가 + `web` 실행 모드 뼈대** — 완료 2026-11-27 (`cli/WebLauncher`, `web/security/LoopbackGuard`, 테스트 18개. 👤 diff 리뷰 대기)
  - Spring MVC 스타터 추가(Boot 4.2 의 정확한 아티팩트명은 실제 BOM 에서 확인, 승인 2026-11-27) → 잠금·체크섬 재생성(HANDOFF §8 절차), 추가된 전이 의존성 목록을 👤 에게 보고
  - `TossMcpApplication`: `web` 분기, `Usage` 에 모드 추가. web 전용 설정: `web-application-type=servlet`, `server.address=127.0.0.1`, `server.port`, 배너 끔, 에러 상세 끔, access log 끔
  - `serve`·`setup`·`reset-keys`·`diagnose` 는 계속 `web-application-type=none`
  - `LoopbackGuard`: 바인딩 주소가 루프백이 아니면 종료, 포트 사용 중 안내
  - Spring AI MCP 스타터가 servlet 환경에서 HTTP 엔드포인트를 등록하지 않는지 의존성 jar 로 확인·기록(TOSS_API_NOTES 또는 ARCHITECTURE §12.6)
  - 테스트: web 모드는 127.0.0.1 에만 리슨, serve 모드는 포트 없음(기존 `ServeStdoutIT` 류 확장), 키 없으면 종료 코드 1

- [x] **P9-2 🤖🔍 localhost 보안 필터 (`web/security`)** — 완료 2026-11-27 (`LocalAccessFilter`·`LaunchCodeStore`·`SessionStore`·`LoginController`·`SilentErrorController`, `/api/status`, `static/`, `cli/WebConsole`·`BrowserOpener`, 테스트 39개 추가. 👤 diff 리뷰·실행 확인 대기. 남은 것: WS-REQ-8 바디 크기 제한은 P9-4/P9-8) (RC-002 — 규칙 기준은 [WEB_SECURITY.md](../design/WEB_SECURITY.md) §4·§6)
  - RC-002 D5: 이 작업에서 `GET /api/status`(P9-4 일부), 최소 첫 화면·콘솔 접속 URL·브라우저 자동 열기·Enter 재발급(P9-5 일부)을 함께 한다
  - ARCHITECTURE §12.2 의 검사 5단계, 일회용 접속 코드(1회·2분) → `tim_session` 쿠키(HttpOnly·SameSite=Strict, 1세션, 유휴 8시간), 보안 헤더, CORS 없음
  - 로그 마스킹에 세션 쿠키·접속 코드 추가(`Masking`)
  - 테스트(MockMvc): 잘못된 Host 421, 교차 Origin·`Sec-Fetch-Site: cross-site` 403, 쿠키 없음 401, 코드 재사용·만료 거부, 새 로그인 시 이전 세션 폐기, 헤더 존재, `Access-Control-Allow-*` 없음

- [x] **P9-9 🤖🔍 web 화면에서 키 입력 (RC-003)** — 완료 2026-11-27 (`service/KeySetupService`, `web/api/KeysController`, `LocalAccessFilter` POST 검사, `TokenManager.reissueAfterKeyChange`, 화면 키 입력 카드, 테스트 17개 추가. 👤 실행 확인 대기)
  - 키 없이 `web` 기동 → 로그인 → 화면에서 Client ID·Secret 입력 → OS 자격 증명 저장소 저장 → 토큰 발급으로 연결 확인 → 결과 문구. 교체 허용(E1), 삭제·Claude Code 등록은 화면에서 안 함(E2)
  - 규칙: WEB_SECURITY §4.8a WS-KEY-1~9

- [x] **P9-3 🤖 조회 서비스 계층 추출 (`service/`)** — 완료 2026-11-27 (`StockSearchService`·`QuoteService`·`AccountQueryService`, 도구는 위임·문구 그대로. 문구 고정 테스트 10개 + 서비스 테스트 6개 + 연결 테스트 1개)
  - `MarketTools`·`AccountTools` 의 조회 로직을 `service`(예: `QuoteService`, `AccountQueryService`, `StockSearch` 재사용)로 옮기고 tool 은 위임만
  - 반환은 record(금액 `BigDecimal`). MCP 도구의 동작·문구는 바뀌지 않아야 함
  - 테스트: 서비스 단위 테스트(Mockito `TossApi`), 기존 도구 테스트 그대로 통과

- [x] **P9-4 🤖 조회 REST API (`web/api`)** — 완료 2026-11-27 (`QueryController`·`ApiErrorAdvice`·`InvalidInputException`, 테스트 11개 + 로그인 전 401 경로 추가. `/api/portfolio` 는 P3-4 후)
  - ARCHITECTURE §12.3 엔드포인트(`/api/status`, `/api/stocks/search`, `/api/quote`, `/api/buying-power`, `/api/orders`; `/api/portfolio` 는 P3-4 후), 에러 형식·상태 정규화, 입력 검증
  - 테스트(MockMvc + 가짜 서비스): 정상 응답 JSON 형태(금액 문자열), 잘못된 입력 400, `TossApiException` → 에러 형식 + `requestId`, `/api/status` 에 키 값 없음

- [ ] **P9-5 🤖 로컬 웹 UI (`static/`) + 접속 안내**
  - ARCHITECTURE §12.5: 정적 HTML/JS/CSS(외부 리소스·인라인 스크립트 없음), 검색→현재가, 매수 가능 금액, 주문 내역, 주문 OFF/DRY_RUN 배지
  - 기동 시 콘솔에 접속 URL 출력 + `TOSSINVEST_WEB_OPEN_BROWSER` 면 기본 브라우저 열기(`java.awt.Desktop`, 실패해도 계속)
  - 테스트: 정적 파일에 `http://`·`https://` 외부 URL 없음(허용: 없음), `innerHTML` 미사용 검사, 로그인 → `/` 리다이렉트

- [ ] **P9-6 🤖 아키텍처 규칙·문서 마무리**
  - ArchUnit: ARCHITECTURE §10 의 RC-001 규칙 3개(web 의존 범위, tool↔web 금지, CORS API 금지)
  - `diagnose`(P3-5)에 web 포트 설정·포트 사용 여부만 추가(세션·코드 제외)
  - README·HANDOFF 에 `web` 사용법(“`setup` → `web` → 브라우저”)

- [ ] **P9-7 👤 실사용 확인**
  - `./gradlew bootJar` → `java -jar build/libs/tossinvest-mcp-<버전>.jar web` → 자동으로 열린 브라우저에서 "삼성전자" 검색 → 현재가·매수 가능 금액·주문 내역
  - 확인: Windows 방화벽 허용 창이 **뜨지 않아야 정상**(뜨면 허용하지 말고 알려 주기), 다른 기기에서 `http://<PC IP>:8765` 접속 불가, 코드 없이 `http://127.0.0.1:8765/api/quote?symbol=005930` 직접 열면 401

- [ ] **P9-8 🤖🔍 주문 REST + UI 확정 버튼** — ⏸ 선행: P9-1~7, P3-4, P4-1 일일 카운터 영속화, 👤 착수 승인
  - ARCHITECTURE §12.4: `POST /api/orders/preview`·`/place`, JSON + `X-Tossinvest-Local` 필수, `enable-orders=false` 면 미등록, 같은 `OrderService`·가드레일·감사로그·DRY_RUN
  - 테스트: 커스텀 헤더 없음 403, 폼 Content-Type 415, previewId 재사용 거부, DRY_RUN 에서 주문 API 0회, 교차 Origin 403

---

## P8. 디스코드 채팅 주문 — 세부 내용은 [DISCORD_PLAN.md](discord/DISCORD_PLAN.md)
> 선행: P1-8, P2-1(토큰 공유 — serve 와 동시 실행 대비), P3-2(종목명 검색), P4-1(장 운영 시간). 권장: P2-2, P4-5, P1-4
> 순서: P8-0 → 선행 → P8-1 → 2 → 3 → 4 → 5 → 6 → 9 → 11 → 7 → 8 → 10

- ⏸ **P8 전체 보류 (2026-11-25)** — 로컬 PC 단독 사용 우선. 재개 시 HANDOFF §3 의 보안 검토(D7 TOTP, D8 전용 한도) 반영
- [ ] **P8-0 👤🤖 결정 게이트** — D1(디스코드 호스트 허용, discord 모드 한정)·D2(본인 DM 만)·D3(규칙 파서 기본)·D4(각자 봇)·D5(JDA 6.7.0)·D6(봇 토큰 OS 저장소) 승인 → DECISIONS/PRINCIPLES/ARCHITECTURE 반영. **승인 전 P8-1 이후 착수 금지**
- [ ] **P8-1 🤖 명령 문법 명세 + 파서 테스트 50개+** — `docs/requirements/discord/DISCORD_COMMANDS.md`, `command/OrderIntent`, `ParseResult`
- [ ] **P8-2 🤖🔍 규칙 기반 파서** — `command/OrderIntentParser` (정규식, 한국어 금액 단위, 입력 정제). 네트워크·Spring 의존 없음
- [ ] **P8-3 🤖 의도 해석기** — `command/IntentResolver`: 종목명→코드(여러 개면 되묻기), 가격 생략 시 현재가 지정가 → `OrderCommand`
- [ ] **P8-4 🤖 JDA 의존성 + `discord` 실행 모드** — `JDABuilder.createLight`, 최소 intent, 토큰 없으면 안내 후 종료, JDA 연결 호스트 실측·문서화
- [ ] **P8-5 🤖🔍 소유자 인증** — `OwnerGuard`: 내 user ID 외 무응답, DM 기본·길드는 명시 허용 시만, 버튼도 재검증
- [ ] **P8-6 🤖 미리보기 → 버튼 → 주문** — Embed + [확정]/[취소], 확정 시 `OrderService.place`, 메시지 수정으로 버튼 제거, 만료 처리
- [ ] **P8-7 🤖 슬래시 명령** — `/매수 /매도 /현재가 /잔고 /미체결 /취소 /도움말`, 종목 자동완성
- [ ] **P8-8 🤖 조회 명령 렌더링** — 잔고는 요약만, 금액 표시는 `TOSSINVEST_DISCORD_SHOW_AMOUNTS`(기본 false)
- [ ] **P8-9 🤖🔍 setup 에 디스코드 단계** — 봇 토큰·내 user ID 를 OS 저장소(`tossinvest-mcp-discord`)에, 연결 테스트, 초대 링크 생성, reset-keys 포함
- [ ] **P8-10 🤖 상시 실행 편의** — 로그인 시 자동 실행(작업 스케줄러, 사람 확인 후), 트레이 아이콘(선택), diagnose 에 디스코드 상태
- [ ] **P8-11 👤 실사용 테스트 (DRY RUN)** — DISCORD_PLAN §5 P8-11 체크리스트
- [ ] **P8-12 (선택) 🤖 LLM 자연어 해석** — D3 승인 시만. 규칙 파서 실패 시 보조, OrderIntent JSON 만 출력, 같은 미리보기·버튼 경유

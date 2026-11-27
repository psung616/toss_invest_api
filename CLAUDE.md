# CLAUDE.md — tossinvest-mcp (Java / Spring Boot)

토스증권 Open API를 감싸는 **비공식 로컬 서버**. Java + Spring Boot + Spring AI MCP(STDIO).
사용자가 Claude에게 자연어로(`serve`, MCP) 또는 내 PC 브라우저에서(`web`, 127.0.0.1 전용 — RC-001) 시세 조회·잔고 확인·주문을
요청하면, 사용자 PC에서 도는 이 서버가 토스증권 API를 호출한다. 소스코드는 GitHub에 공개되며, 각 사용자는 자기 키로 자기 PC에서만 실행한다.

## 반드시 먼저 읽을 문서
문서는 역할별 폴더로 나뉜다 — 지도: `docs/README.md` (governance / requirements / design / process / qa / api).
아래 세 문서는 `@` import로 매 세션 자동 로드된다 (Claude Code CLAUDE.md import 문법).

@docs/governance/PRINCIPLES.md
@docs/governance/DECISIONS.md
@docs/process/HANDOFF.md

- `docs/process/HANDOFF.md` — **세션 인수인계: 현재 상태·다음 할 일·함정.** 새 세션은 여기서 시작하고, 작업을 마치면 갱신한다
- `docs/governance/PRINCIPLES.md` — 절대 원칙 (아래 요약보다 우선)
- `docs/governance/DECISIONS.md` — 확정된 결정(언어·프레임워크)과 공개 배포·개인정보 운영 규칙, 근거 메모
- `docs/requirements/TASKS.md` — 작업 목록. **항상 여기 적힌 작업 ID 단위로만** 진행
- `docs/requirements/CHANGES.md` — 요구사항 변경 이력. **요구사항을 바꾸는 요청은 먼저 여기에 `RC-NNN` 으로 등록**하고
  사람 승인 후 TASKS·설계 문서에 반영한다 (절차: `docs/README.md`)
- `docs/design/ARCHITECTURE.md` — 패키지 구조, 설정 키, 도구(Tool) 명세
- `docs/requirements/discord/DISCORD_PLAN.md` — 디스코드 채팅 주문(P8) 세부 계획. P8 작업 시 반드시 먼저 읽기
- `docs/design/TOSS_API_NOTES.md` — 토스증권 API 사실 정리. API 세부(필드명·경로·그룹)는 추측하지 말고
  `docs/api/openapi.json`(로컬 전용, `./gradlew fetchSpecs`로 받음)에서 확인

## 코드 영역별 필수 문서 (훅으로 강제)
아래 경로의 파일을 **편집하기 전에 이번 세션에서 해당 문서를 Read 도구로 읽어야** 한다.
읽지 않았으면 `.claude/hooks/require-docs.mjs`(PreToolUse 훅)가 편집을 막는다. 이 표가 훅의 유일한 설정이다
(형식: 첫 칸 경로 glob 은 쉼표로 여러 개, 둘째 칸 문서 경로는 쉼표로 여러 개, 모두 백틱).

| 편집 대상 (glob) | 먼저 읽을 문서 |
|---|---|
| `src/main/**` | `docs/requirements/TASKS.md`, `docs/design/ARCHITECTURE.md` |
| `src/main/**/order/**`, `src/main/**/tool/**`, `src/main/**/client/**`, `src/main/**/stock/**` | `docs/design/TOSS_API_NOTES.md` |
| `src/main/**/cli/**`, `packaging/**` | `docs/qa/DEFECTS.md` |
| `src/main/**/command/**` | `docs/requirements/discord/DISCORD_PLAN.md` |
| `build.gradle`, `gradle.lockfile`, `gradle/verification-metadata.xml` | `docs/design/ARCHITECTURE.md` |

커밋 전에는 `docs/qa/QA_PLAN.md` §7 종료 기준을 확인한다 (`/qa` 스킬).

## 절대 원칙 (위반하는 코드는 작성 금지 — 필요해 보이면 멈추고 사람에게 질문)
1. **외부 통신은 토스증권 호스트만.** `openapi.tossinvest.com`, `openapi-ws.tossinvest.com` 외 어떤 호스트로도
   요청하지 않는다. 텔레메트리·에러 리포팅·업데이트 확인·분석 SDK 금지. Spring Boot Actuator 금지.
   모든 HTTP는 `client` 패키지의 허용목록 `RestClient`를 통해서만 한다 (ArchUnit 테스트로 강제).
2. **비밀정보를 저장소·로그·stdout에 남기지 않는다.** 키는 OS 자격 증명 저장소(JNA 직접 바인딩, `secret` 패키지) 또는
   환경변수에서만 읽는다. `.env`·외부 `application.yml`에 키를 두지 않으며, 외부 설정 파일 로딩을 막는다.
   토큰 캐시·로그·종목 캐시는 OS 사용자 폴더(`AppPaths`)에만 쓴다(저장소 폴더 금지).
3. **실제 API 응답 데이터를 저장소에 넣지 않는다.** 테스트 픽스처는 손으로 지어낸 가짜 값만 사용.
4. **`serve` 모드에서 stdout은 MCP 프로토콜 전용.** `System.out` 금지, Spring 배너 끔, 콘솔 로그 끔.
   로그는 파일 + stderr로만. (`setup`/`diagnose` 등 CLI 모드에서만 `cli` 패키지가 stdout 사용 가능)
5. **돈은 `BigDecimal`.** 금액·가격·수량에 `double`/`float` 금지 (ArchUnit 테스트로 강제).
6. **주문은 안전한 기본값.** `ENABLE_ORDERS=false`, `DRY_RUN=true`가 기본. 주문 경로는 반드시
   `preview_order → place_order(preview_id)` 2단계 + 가드레일 통과 후에만 실행.
7. **1억원 이상 주문용 `confirmHighValueOrder`를 코드가 자동으로 true로 설정하지 않는다.**
8. **`web` 모드는 내 PC 전용(RC-001).** `127.0.0.1` 에만 바인딩(비루프백이면 기동 거부), Host·Origin·`Sec-Fetch-Site` 검사,
   일회용 접속 코드 → `HttpOnly`·`SameSite=Strict` 세션 쿠키, CORS 허용 금지, CSP·`X-Frame-Options: DENY`.
   웹 UI 는 jar 내장 정적 파일만(CDN·웹폰트·외부 스크립트 금지). 세부: `docs/design/ARCHITECTURE.md` §12

## 기술 스택
- Java 21 LTS 이상, Spring Boot 4.x, Spring AI 2.x 최신 안정 버전(`spring-ai-starter-mcp-server`, STDIO, SYNC)
- 빌드: Gradle(Groovy DSL) + Gradle Wrapper, 의존성 잠금(`dependencyLocking`) + 체크섬 검증(`verification-metadata.xml`)
- HTTP: Spring `RestClient`(JDK HttpClient 기반, 리다이렉트 비활성). JSON: Spring Boot 기본 Jackson
- 레이트리밋·재시도: **직접 구현**(작은 범위, Retry-After 제어 필요). Bucket4j·Resilience4j 추가 금지
- 키 저장: JNA(`net.java.dev.jna:jna`)로 Windows 자격 증명 관리자 직접 호출, macOS `security` / Linux `secret-tool` 명령
  (java-keyring 은 2023년 이후 유지보수 중단이라 사용하지 않음 — docs/governance/DECISIONS.md)
- 웹(`web` 모드 전용, RC-001 승인 2026-11-27): Spring MVC 스타터(내장 Tomcat), 정적 HTML/JS UI(빌드 도구·npm 없음)
- 배포: 당분간 `java -jar`(`serve` 또는 `web`). jpackage EXE·`.mcpb`(`./gradlew mcpb`)는 **보류**(RC-001) — 코드만 유지
- DTO: Java `record`, 시간: `java.time` + 주입 가능한 `Clock`(Asia/Seoul)
- 테스트: JUnit 5, AssertJ, Mockito, `MockRestServiceServer`, ArchUnit. 포맷: Spotless(palantir-java-format)
- 런타임 의존성 추가는 반드시 사람 승인. Lombok 사용 안 함(record로 충분)

## 명령어 (Claude Code는 Windows에서도 Git Bash로 실행하므로 `./gradlew` 사용)
```
./gradlew test                 # 단위 테스트 (실 API 호출 없음)
./gradlew check                # 테스트 + ArchUnit + Spotless 검사
./gradlew spotlessApply        # 포맷 적용
./gradlew bootJar              # build/libs/tossinvest-mcp-<버전>.jar 생성
./gradlew fetchSpecs           # 공식 openapi.json / asyncapi.json → docs/api/
java -jar build/libs/tossinvest-mcp-<버전>.jar serve      # MCP 서버(stdio) — 사람만 실행
java -jar build/libs/tossinvest-mcp-<버전>.jar web        # localhost 웹(127.0.0.1:8765) + 브라우저 UI — 사람만 실행 (P9)
java -jar build/libs/tossinvest-mcp-<버전>.jar setup      # 키 입력 → 자격 증명 저장소 — 사람만 실행
java -jar build/libs/tossinvest-mcp-<버전>.jar diagnose   # 민감정보 없는 진단 출력
./gradlew liveTest             # 실 API 조회 점검(@Tag("live")) — 사람만 실행
```

## 작업 방식
1. 사람이 "P1-3 진행해줘"처럼 작업 ID를 주면, `docs/requirements/TASKS.md`에서 해당 작업의 요구사항·완료 기준을 읽는다.
2. **구현 전에 계획(만들/수정할 클래스, 테스트 목록, 확인할 스펙 항목)을 먼저 제시**하고 승인받는다.
3. 테스트를 먼저 쓰고 → 구현 → `./gradlew check` 통과.
4. 해당 작업 범위 밖의 파일은 건드리지 않는다. 범위 밖 개선점은 마지막에 제안만 한다.
5. 완료 시 `docs/requirements/TASKS.md`의 체크박스를 체크하고, 변경 요약 + 사람이 직접 봐야 할 부분을 알려준다.
   **작업 단위가 끝날 때마다(작업 ID 가 없는 요청 포함) `docs/process/HANDOFF.md` 를 갱신한다**: 마지막 갱신 날짜·기준 커밋,
   현재 상태, 다음 할 일(🤖/👤), 작업 로그 한 줄 — "어디까지 했는지"가 다음 세션에서 바로 보이게.
   Stop 훅(`.claude/hooks/require-handoff.mjs`)이 HANDOFF 보다 나중에 바뀐 파일이 있으면 응답 종료 전에 갱신을 요구한다.
6. 커밋은 사람이 요청할 때만. 메시지 형식: `P1-3: 자격 증명 저장소와 setup 마법사`

## 하지 말 것
- 테스트에서 실제 토스증권 API 호출 (`MockRestServiceServer` 또는 가짜 구현 사용, 실호출은 `@Tag("live")`만)
- 사람의 키·계좌번호·accountSeq를 채팅에 요구하거나 출력
- `serve`, `web`, `setup`, `liveTest` 실행 (사람이 직접 실행)
- `web` 모드에서 `0.0.0.0`·외부 인터페이스 바인딩, CORS 허용, 외부 CDN 리소스 사용
- 새 런타임 의존성 추가, 새 외부 호스트 추가 — 반드시 먼저 질문
- API 필드명·엔드포인트·레이트리밋 그룹, Spring AI 클래스/패키지명을 추측해서 작성 —
  openapi.json·공식 문서·실제 의존성 jar에서 확인하고, 없으면 질문

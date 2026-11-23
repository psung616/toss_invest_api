# VS Code + Claude Code 바이브코딩 진행 가이드 (Java / Spring Boot)

## 0. 처음 한 번
1. VS Code에서 이 폴더(`toss_invest_api`)를 연다. Java 확장("Extension Pack for Java")이 JDK 21을 인식하는지 확인.
2. **Claude Code 권한 설정 파일 배치 (사람이 직접):** `docs/setup/claude-settings.json`을 `.claude/settings.json`으로 복사.
   PowerShell:
   ```
   New-Item -ItemType Directory -Force .claude ; Copy-Item docs\setup\claude-settings.json .claude\settings.json
   ```
   (`.env` 읽기 금지, `java -jar`·`bootRun`·`liveTest` 실행 금지, `./gradlew test/check/build` 등 자동 허용. 커밋해도 되는 파일)
3. Claude Code를 연다(VS Code 확장 패널 또는 통합 터미널에서 `claude`). `/status`의 `Setting sources`에
   Shared project settings가 보이면 적용된 것.
4. 첫 메시지:
   ```
   CLAUDE.md와 docs/ 폴더의 문서를 모두 읽고, 프로젝트 원칙과 작업 방식을 5줄로 요약해줘.
   아직 코드는 작성하지 마.
   ```
   요약에 "토스증권 호스트만, 키는 자격 증명 저장소, 실데이터 커밋 금지, serve 모드 stdout 금지, BigDecimal,
   안전한 기본값"이 빠져 있으면 다시 읽게 한다.

## 1. 작업 하나를 진행하는 표준 루프
```
① /clear                       ← 이전 작업 대화를 비워 컨텍스트를 깨끗하게
② "P1-3 진행해줘. 먼저 계획만 보여줘."
③ 계획 검토 → 수정 요청 또는 "좋아, 진행해"
④ Claude: 테스트 작성 → 구현 → ./gradlew check
⑤ diff 검토 (🔍 작업은 한 줄씩)
⑥ "커밋해줘" → "P1-3: java-keyring 기반 자격 증명 저장소와 setup 명령"
```
- 큰 작업(P0-3, P2-1, P3-1, P4-3, P4-4)은 Plan 모드(Shift+Tab)에서 계획을 먼저 확정한다.
- **작업 하나 = 커밋 하나.** 문제가 생기면 `git revert`로 해당 작업만 되돌린다.
- P1-8(ArchUnit) 이후에는 원칙 위반 상당수가 `./gradlew check`에서 자동으로 걸린다. 그 전 작업은 더 꼼꼼히 리뷰.

## 2. 상황별 프롬프트
**작업 시작**
```
P2-1 진행해줘. docs/TASKS.md의 요구사항·완료 기준과 docs/ARCHITECTURE.md §6을 따르고,
먼저 만들 클래스·테스트 목록·확인이 필요한 API 스펙 항목을 정리해서 보여줘.
```
**스펙·프레임워크가 애매할 때**
```
추측하지 말고 docs/api/openapi.json(또는 실제 의존성 jar/공식 문서)에서 근거를 찾아서 보여줘.
없으면 없다고 말해줘.
```
**테스트가 실패할 때**
```
실패 원인을 먼저 설명해줘. 테스트를 고칠지 구현을 고칠지 근거와 함께 판단해줘.
테스트를 약하게 만들거나 @Disabled로 통과시키는 건 금지.
```
**작업 후 원칙 점검 (🔍 작업마다)**
```
방금 변경한 코드를 docs/PRINCIPLES.md와 CLAUDE.md의 절대 원칙 7개 기준으로 하나씩 점검해서
위반 가능성이 있는 줄을 파일:줄번호로 알려줘.
```
**범위가 커질 때**
```
그 개선은 이번 작업 범위 밖이야. docs/TASKS.md의 알맞은 단계에 새 작업으로 추가만 해줘.
```

## 3. diff에서 바로 잡아야 할 신호 (발견하면 즉시 중단)
| 신호 | 이유 |
|---|---|
| `System.out` (cli 패키지 외) | serve 모드 stdout 오염 → MCP 프로토콜 깨짐 |
| `spring.main.banner-mode`가 off가 아님, 콘솔 로그 appender가 stdout | 동일 |
| `WebClient`, `RestTemplate`, 새 `HttpClient`, 새 URL 문자열 | 허용목록 우회 가능성 |
| `double`/`float` (금액·가격·수량), `new BigDecimal(0.1)` | 금액 오차 |
| `System.getenv` (config·secret 외), `.env`, `spring.config.import` | 키·설정 관리 원칙 위반 |
| 저장소 경로에 파일 쓰기(`Paths.get("cache")` 등) | 사용자 폴더 원칙 위반 |
| 실제처럼 보이는 계좌·잔고 숫자가 들어간 픽스처 | 실데이터 유입 의심 → 출처 확인 |
| `confirmHighValueOrder = true` | 자동 고액 주문 확인 금지 |
| 기본값 `enable-orders: true` / `dry-run: false` | 안전한 기본값 위반 |
| `spring-boot-starter-actuator`, Sentry, 분석 SDK | 외부 통신·노출 위험 |
| `build.gradle` 런타임 의존성 추가, Lombok | 공급망 위험 → 먼저 승인 |
| `@Disabled`, ArchUnit 규칙 완화 | 안전장치 무력화 |

## 4. 실제 API를 쓰는 순간의 규칙
- 키 입력(`java -jar ... setup`)은 **항상 내가 터미널에서 직접.** 채팅에 키를 붙여넣지 않는다.
- `./gradlew liveTest`는 내가 별도 터미널에서 실행한다. 결과를 Claude에게 공유할 때는 에러 코드와 `X-Request-Id`만.
- 주문 실험은 P4-7(DRY_RUN) → P5-1(체결 안 되는 지정가 + 즉시 취소) 순서를 건너뛰지 않는다.
- 이상 동작 시 즉시 킬 스위치: 사용자 config 폴더에 `KILL` 파일 생성(경로는 `diagnose`에 표시).

## 5. 내가 만든 서버를 Claude Code에서 직접 써보기 (P3-6부터)
```
./gradlew bootJar
claude mcp add --transport stdio tossinvest -- java -jar "C:\Users\psung\OneDrive\Desktop\toss_invest_api\toss_invest_api\build\libs\tossinvest-mcp-<버전>.jar" serve
```
- 기본 scope(local)는 `~/.claude.json`에 저장되어 저장소에 커밋되지 않는다.
- `--env`로 키를 넘기지 않는다. 키는 자격 증명 저장소에서 읽는다.
- 주문 설정 변경: `claude mcp remove tossinvest` 후 `--env TOSSINVEST_ENABLE_ORDERS=true` 등을 붙여 재등록.
- 코드를 고친 뒤에는 `./gradlew bootJar`로 jar를 다시 만들고 `/mcp`에서 Reconnect.
- 상태 확인: Claude Code 안에서 `/mcp`, 셸에서 `claude mcp list`.

## 6. 진행 순서 요약
```
P0 준비 ─► P1 기반(네트워크 없음, ArchUnit까지) ─► P2 API 클라이언트 ─► P3 조회 MCP ─► [직접 써보기]
   ─► P4 주문 DRY_RUN ─► P5 소액 실주문 ─► P6 공개 배포 ─► P7 고도화
```
P3까지 끝나면 이미 "내 포트폴리오 요약해줘"가 동작한다. 여기서 충분히 써보고 P4로 넘어가는 걸 권장한다.

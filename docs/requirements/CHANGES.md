# 요구사항 변경 이력 (CHANGES)

> 요구사항(TASKS.md 의 범위·완료 기준, 도구 명세, 가드레일 규칙 등)을 바꿀 때마다 여기에 먼저 한 건씩 기록한다.
> 절차는 [docs/README.md](../README.md) "요구사항을 바꿀 때". 원칙(`governance/`)과 충돌하면 사람의 명시 승인이 필요하다.
> 상태: `제안` → `승인` → `반영` / `철회`

| ID | 날짜 | 상태 | 요약 | 영향 (작업 ID · 문서 · 코드) |
|---|---|---|---|---|
| RC-003 | 2026-11-27 | 승인 · 반영 | web 화면에서 키 입력·저장·연결 테스트 (키 없이도 `web` 기동, 첫 POST 엔드포인트) | 신규 P9-9 · WEB_SECURITY §4.2·§4.6·§4.7·§5 · ARCHITECTURE §12 · `cli/WebLauncher`, `web/`, `service/` |
| RC-002 | 2026-11-27 | 승인 · 반영(문서) · 코드 진행 | web 모드 보안 정책 문서화([WEB_SECURITY.md](../design/WEB_SECURITY.md)) — §12 보안 조건을 규칙 ID 로 구체화·강화, P9-4·P9-5 일부 앞당김 | P9-2·P9-4·P9-5 완료 기준 · ARCHITECTURE §12.2 · `web/security`, `Masking` |
| RC-001 | 2026-11-27 | 반영(문서) · 코드 대기 | EXE 배포 대신 **localhost(127.0.0.1) 웹 모드**로 내 PC 브라우저에서 REST API 호출. MCP(stdio) 유지 | P9 신설, P6-7·P6-8 보류 · PRINCIPLES·DECISIONS·ARCHITECTURE §1·§2·§4·§10·§12·CLAUDE.md · 신규 `web/`·`service/`, 의존성 추가 |

## RC-003 — web 화면에서 키 입력
- **요청 (2026-11-27)**: `web` 첫 실행이 "저장된 키가 없습니다" 로 끝남 → "웹서버를 켜서 그 화면에 키를 입력한 다음 연동이 되게 해줘"
- **변경 전 → 후**: "키가 없으면 `web` 기동 거부, 키 입력은 `setup`(콘솔·Swing 창)만, web 은 GET·HEAD 만" →
  "키가 없어도 `web` 기동 → 콘솔 코드로 로그인 → 화면에서 키 입력 → 자격 증명 저장소에 저장 → 연결 테스트 결과 표시"
- **설계 (보안 규칙, 승인 시 WEB_SECURITY 에 WS-KEY-* 로 추가)**:
  1. 저장 위치는 `setup` 과 **같은** `CredentialStore`(Windows 자격 증명 관리자 등). 파일·브라우저 저장소·로그에 남기지 않음. 공통 로직은 `SetupSteps` 재사용(→ `service/` 로 옮김)
  2. 키 입력 화면도 **로그인(콘솔 일회용 코드) 후에만** 열림 — 기존 WS-REQ-1~6 그대로
  3. 첫 상태 변경 엔드포인트 `POST /api/keys` 하나만 허용(그 외 POST 는 계속 405). WS-REQ-7 을 앞당겨 적용:
     `Content-Type: application/json` + `X-Tossinvest-Local: 1` 필수, 바디 ≤ 4KB(WS-REQ-8 일부), 값 형식·길이 검증
  4. 응답·화면에 키 값을 **절대 되돌려 주지 않음**(마스킹 ID 포함 안 함). 결과는 `저장됨 + 연결 테스트 결과(UserMessages 한국어)` 만
  5. 키 저장·교체 시 토큰 캐시(`cache/token.json`)를 지워 이전 키의 토큰을 쓰지 않게 함
  6. 입력칸: Secret 은 `type=password`, 두 칸 모두 `autocomplete=off`, `spellcheck=false`. 저장 후 입력칸 즉시 비움. GET 쿼리·URL 에 키를 싣지 않음
  7. 요청 바디를 로그에 남기지 않음(접근 로그 꺼짐 유지). 실패 로그는 예외 종류·토스 에러 코드만 → 마스킹 테스트 추가
  8. 저장 시도 제한: 1분 5회(연결 테스트가 토스 토큰 API 를 부르므로 남용·레이트리밋 방지)
- **남는 위험 (§5 에 추가)**: 키가 브라우저 메모리·JS 문자열로 잠시 존재(새로고침·저장 후 비움). 평문 HTTP 지만 루프백이라 PC 밖으로 나가지 않음. 내 계정 악성 브라우저 확장은 입력을 볼 수 있음(setup 창의 키보드 후킹과 같은 범위)
- **결정 필요 (👤)**:
  - E1 키가 이미 있을 때 화면에서 **교체**도 허용? — 추천: **허용**(로그인한 본인만 가능, setup 과 동일). 삭제는 콘솔 `reset-keys` 만
  - E2 Claude Code 자동 등록(setup 의 3단계)도 화면에서? — 추천: **안 함**(web 은 Claude 와 무관, 외부 프로세스 실행 경로를 늘리지 않음)
  - E3 작업 순서 — 추천: **P9-9 를 P9-3 보다 먼저**(지금 키가 없어 web 확인이 막혀 있음)
- **원칙 충돌**: 없음(키는 여전히 OS 자격 증명 저장소에만, 외부 송신 없음). 단 WEB_SECURITY 의 "P9-8 전까지 GET·HEAD 만" 을 `/api/keys` 한 경로에 한해 완화
- **사람 결정 (2026-11-27)**: "OK" → E1 교체 허용 · E2 Claude Code 등록은 화면에서 안 함 · E3 P9-9 를 P9-3 보다 먼저
- **반영**: 2026-11-27 — WEB_SECURITY §4.8a(WS-KEY-1~9)·§4.2·§4.6·§5·§6, ARCHITECTURE §12.1~12.3, TASKS P9-9. 코드 P9-9 완료

## RC-002 — web 모드 보안 정책
- **요청 (2026-11-27)**: "localhost 로 바로 접근해서 확인할 수 있게 서버를 만들어 달라" → "그럼 보안정책을 확실하게 세워서 md 파일로 먼저 만들어줘"
- **내용**: [docs/design/WEB_SECURITY.md](../design/WEB_SECURITY.md) — 목표·자산·위협 모델(T1~T7)·규칙(WS-NET/REQ/AUTH/SES/RES/DATA/UI/LOG/ORD/SUP/OPS)·남는 위험·검증 표·결정 항목(D1~D5)
- **ARCHITECTURE §12 대비 바뀌는 점**:
  - Host 허용을 `127.0.0.1:<포트>` 하나로 축소(D1, 기존: localhost 도 허용)
  - `Origin: null` 거부, `Sec-Fetch-Site: same-site` 거부 명시
  - 메서드 허용목록(P9-8 전까지 GET·HEAD, 그 외 405), 정적 파일도 세션 필요 명시
  - 세션 절대 만료 24시간(D2), 틀린 코드 10회 시 코드 무효(D4), 상수 시간 비교, `JSESSIONID` 미사용
  - 보안 헤더 추가: CSP `default-src 'none'` 기반 세분화, COOP·CORP·Permissions-Policy, `Server` 헤더·Tomcat 에러 페이지 서버 정보 제거
  - 남는 위험 명시: 쿠키는 포트를 구분하지 않음(같은 PC 의 다른 127.0.0.1 서버가 쿠키를 받음)
  - 진행 순서: P9-2 와 함께 `/api/status`·최소 첫 화면·콘솔 URL 출력·브라우저 자동 열기를 앞당김(D5)
- **원칙 충돌**: 없음(원칙 8 을 더 엄격하게 구체화)
- **사람 결정 (2026-11-27)**: "질문 준 부분에 대해 모두 추천으로 알려준 부분 모두 오케이" → D1 127.0.0.1 만 · D2 절대 만료 24h · D3 브라우저 자동 열기 켬 · D4 10회 실패 시 코드 무효 · D5 앞당김
- **반영**: 문서 2026-11-27 (WEB_SECURITY 승인, ARCHITECTURE §12.1·§12.2, TASKS P9-2/4/5). 코드는 P9-2

## RC-001 — localhost 웹 모드 (EXE 대신)
- **요청**: "EXE로 안 하고, 내 로컬 PC에서만 열 수 있는 localhost로 열어서 접근하여 REST API를 호출하고 싶다. 보안적으로 큰 문제 없지 않나?"
- **보안 판단 (2026-11-27)**: 대체로 맞음 — **조건부**.
  - 맞는 점: `127.0.0.1` 에만 바인딩하면 같은 네트워크·인터넷에서 접근 불가. 키 보관·외부 통신(토스 호스트만)·개발자 비경유 구조는 그대로.
  - 남는 위험과 대책 (모두 필수, ARCHITECTURE §12):
    1. 브라우저 경유 공격 — 사용자가 연 아무 웹페이지가 `http://127.0.0.1:포트` 로 요청을 보낼 수 있음(CSRF), DNS rebinding 으로 응답까지 읽힐 수 있음
       → Host 헤더 검사, Origin/`Sec-Fetch-Site` 검사, 실행별 일회용 코드 → `SameSite=Strict`·`HttpOnly` 세션 쿠키, CORS 미허용
    2. 바인딩 실수(Spring 기본 `0.0.0.0`) → `127.0.0.1` 고정 + 루프백이 아니면 기동 거부 + 테스트
    3. 같은 PC 의 다른 프로그램 → 세션 쿠키 없으면 401 (악성코드가 이미 PC 에 있으면 어떤 방식도 완전 방어 불가 — 기존과 동일)
    4. 클릭재킹·외부 리소스 → `X-Frame-Options: DENY`, CSP `default-src 'self'`, UI 는 jar 내장 정적 파일만(CDN·웹폰트 금지 — 원칙 1)
- **사람 결정 (2026-11-27)**:
  - MCP(stdio, Claude 연동) **유지** — 같은 조회 로직을 REST 와 MCP 가 공유
  - 호출 주체: **브라우저 화면(로컬 웹 UI)**
  - 범위: **조회 먼저**, 주문 REST 는 다음 단계(P9-8, 별도 착수 승인)
  - 웹 서버: **Spring MVC 스타터(`spring-boot-starter-web` 계열) 런타임 의존성 추가 승인** — Boot 4.2 정확한 아티팩트명은 구현 시 확인
  - EXE(jpackage)·`.mcpb` 배포는 **보류**(코드·태스크는 남겨 둠)
- **변경 전 → 후**: "100% 로컬 stdio, `web-application-type=none`, 웹 서버 없음" → "MCP stdio(`serve`) + localhost 웹(`web`) 두 모드. `web` 모드만 루프백 웹 서버를 연다"
- **원칙 충돌**: 있음 — PRINCIPLES "웹 서버·원격 전송 코드 없음" → 사람 승인으로 개정, DECISIONS 2026-11-27 에 기록
- **반영**: 문서 2026-11-27 (이 RC). 코드는 TASKS P9 순서대로

# web 모드 보안 정책 (WEB_SECURITY)

> 상태: **승인** (2026-11-27, RC-002 — §7 결정 D1~D5 모두 추천안으로 확정).
> 적용 범위: `java -jar … web` 으로 띄우는 127.0.0.1 전용 웹 서버(RC-001)와 그 REST API·브라우저 UI.
> 우선순위: `governance/PRINCIPLES.md` > **이 문서** > `ARCHITECTURE.md` §12(요약). web 보안 세부는 이 문서가 기준이다.
> 규칙마다 ID(`WS-…`)를 붙인다. 코드·테스트·결함 보고에서 이 ID로 참조한다.

---

## 1. 목표 (한 줄씩)
1. **내 PC 의 나만** 쓸 수 있다. 같은 와이파이·인터넷의 누구도 접속할 수 없다.
2. 내가 브라우저로 연 **다른 웹사이트가 이 서버를 조작하거나 응답을 읽을 수 없다.**
3. **키는 브라우저로 절대 가지 않는다.** 브라우저가 받는 것은 화면에 필요한 조회 결과뿐이다.
4. 로그·콘솔·브라우저 기록·캐시에 **키·토큰·세션·접속 코드가 남지 않는다.**
5. 주문(P9-8)은 MCP 와 **같은 가드레일**을 거치고, 사람이 화면에서 직접 확정해야만 나간다.

## 2. 지켜야 할 것 (자산)
| 자산 | 위치 | 브라우저로 나가는가 |
|---|---|---|
| Client ID / Secret | OS 자격 증명 저장소·환경변수 | **절대 안 나감** |
| 액세스 토큰 | 메모리 + `cache/token.json` | **절대 안 나감** |
| 계좌번호·`accountSeq` | 서버 메모리 | **안 나감** (필요하면 끝 4자리만) |
| 잔고·보유·주문 내역·시세 | 토스 API 응답 | 나감 (로그인된 내 브라우저에만) |
| 주문 실행 권한 (P9-8) | `OrderService` | 미리보기→확정 2단계로만 |
| 일회용 접속 코드 | 서버 메모리, 콘솔에 1회 표시 | URL 로 1회 |
| 세션 쿠키 `tim_session` | 서버 메모리 + 브라우저 쿠키 | 쿠키로만 (JS 로 못 읽음) |

## 3. 위협 모델
| ID | 공격자 | 예시 | 대응 규칙 |
|---|---|---|---|
| T1 | 같은 네트워크·인터넷 | 카페 와이파이의 다른 사람이 `http://<내 IP>:8765` 접속 | WS-NET |
| T2 | 내가 연 악성·해킹된 웹사이트 | 페이지 JS 가 `fetch("http://127.0.0.1:8765/api/…")`(CSRF), **DNS rebinding** 으로 응답 읽기, iframe 클릭재킹, 포트 스캔 | WS-REQ, WS-AUTH, WS-RES |
| T3 | 같은 PC 의 다른 프로그램·다른 OS 사용자 계정 | 다른 계정이 `curl 127.0.0.1:8765` (루프백은 PC 전체가 공유) | WS-AUTH(세션 필수) |
| T4 | 화면의 외부 텍스트 | 종목명에 `<script>` 를 넣은 XSS | WS-UI |
| T5 | 흔적 유출 | 로그·브라우저 기록·Referer·캐시·스크린샷에 코드·쿠키·데이터가 남음 | WS-LOG, WS-RES |
| T6 | 공급망 | Tomcat·Spring 취약점, 의존성 변조 | WS-SUP |
| T7 | 실수 | 설정 실수로 `0.0.0.0` 바인딩, ngrok·포트포워딩으로 외부 공개 | WS-NET, WS-OPS |

**범위 밖 (막을 수 없음을 명시)**: 내 계정 권한으로 이미 돌고 있는 악성코드(키체인·메모리를 직접 읽을 수 있음), 관리자 권한 공격자,
악성 브라우저 확장 프로그램, PC 물리 접근. 이 경우는 `serve`(MCP) 모드도 똑같이 뚫린다 — 새로 생기는 위험이 아니다.

---

## 4. 규칙

### 4.1 네트워크 (WS-NET)
| ID | 규칙 | 상태 |
|---|---|---|
| WS-NET-1 | `127.0.0.1` 에만 바인딩한다. 주소는 설정으로 바꿀 수 없다. `-Dserver.address` 를 줘도 `WebLauncher` 가 127.0.0.1 로 덮어쓴다 | ✅ P9-1 |
| WS-NET-2 | 기동 전(팩토리 주소)·기동 후(Tomcat 커넥터 실제 주소) 두 번 검사하고, 루프백이 아니면 기동하지 않는다(`LoopbackGuard`) | ✅ P9-1 |
| WS-NET-3 | 포트는 1024~65535(기본 8765, `TOSSINVEST_WEB_PORT`). 사용 중이면 안내 후 종료하고 다른 포트를 자동으로 고르지 않는다 | ✅ P9-1 |
| WS-NET-4 | 모든 요청에서 원격 주소가 루프백(`127.0.0.1`·`::1`)인지 다시 확인한다. 아니면 **403** (이중 방어) | ✅ P9-2 |
| WS-NET-5 | web 모드는 **수신만** 한다. 외부 송신은 기존처럼 토스 허용 호스트 2곳만(`AllowedHostInterceptor`, 원칙 1) | ✅ 기존 |
| WS-NET-6 | HTTPS 를 쓰지 않는다. 루프백 트래픽은 PC 밖으로 나가지 않고, 자체 서명 인증서는 경고 창 때문에 사용자가 "무시"에 익숙해지게 만든다. 그래서 쿠키에 `Secure` 를 붙이지 않는다 | 결정 |
| WS-NET-7 | Windows 방화벽 허용 창이 **뜨지 않아야 정상**(루프백만 열기 때문). 뜨면 결함으로 본다 | 👤 P9-7 |

### 4.2 요청 검사 (WS-REQ) — 모든 요청, 아래 순서대로. 하나라도 실패하면 즉시 거부
| 순서 | ID | 검사 | 실패 시 | 막는 것 |
|---|---|---|---|---|
| 1 | WS-REQ-1 | 원격 주소 루프백 (= WS-NET-4) | 403 | 바인딩 실수 |
| 2 | WS-REQ-2 | `Host` 헤더가 **정확히** `127.0.0.1:<포트>` (D1 참고) | **421** | **DNS rebinding** (공격자 도메인이 127.0.0.1 로 바뀌어도 Host 는 공격자 도메인) |
| 3 | WS-REQ-3 | `Origin` 이 있으면 정확히 `http://127.0.0.1:<포트>`. `Origin: null` 도 거부 | 403 | 다른 사이트발 요청, sandbox iframe |
| 4 | WS-REQ-4 | `Sec-Fetch-Site` 가 있으면 `same-origin` 또는 `none`(주소창 직접 입력·외부 프로그램이 연 링크)만 | 403 | CSRF(`cross-site`·`same-site`) |
| 5 | WS-REQ-5 | 메서드 허용목록: **GET·HEAD** + **`POST /api/keys`**(RC-003) 만(주문 POST 는 P9-8). 그 외(다른 경로 POST·PUT·DELETE·OPTIONS·TRACE 등) | 405 | 상태 변경·프리플라이트 |
| 6 | WS-REQ-6 | `/login` 을 뺀 **모든 경로**(정적 파일 포함)는 유효한 세션 쿠키 필요 | 401 | T3, 다른 탭·프로그램 |
| 7 | WS-REQ-7 | (RC-003 부터) POST 는 `Content-Type: application/json` + `X-Tossinvest-Local: 1` 필수 | 415 / 403 | 폼·`<img>` 기반 CSRF (이 헤더는 다른 출처에서 붙이면 프리플라이트가 필요한데, 프리플라이트는 WS-REQ-5·WS-RES-2 로 막힘) |
| 8 | WS-REQ-8 | 입력 검증: `symbol` 영숫자·`.`·`-` 1~20자, `query` ≤ 50자, `limit` 1~10, 열거형 값은 허용목록. 요청 줄·헤더·바디 크기 제한(바디 ≤ 16KB) | 400 / 413 | 잘못된 입력, 남용 |

- `Origin`·`Sec-Fetch-*` 헤더가 **없는** 요청(오래된 브라우저, curl)은 3·4 단계를 통과시키되 2·6 단계는 그대로 적용한다. 쿠키가 없으면 결국 401 이다.
- 거부 응답은 **본문 없이 상태 코드만** 보낸다(WS-RES-4). 예외: 브라우저가 페이지를 직접 연 경우(`Sec-Fetch-Mode: navigate`)의 401 은
  jar 에 들어 있는 고정 안내 페이지 하나("콘솔에 표시된 접속 주소로 다시 여세요"), 동적 내용 없음.

### 4.3 접속 코드 (WS-AUTH) — 로그인 대신 "콘솔을 볼 수 있는 사람 = 나"
| ID | 규칙 |
|---|---|
| WS-AUTH-1 | 서버가 리슨을 시작한 **뒤에** 코드를 만든다. 256비트 `SecureRandom`, Base64URL(패딩 없음) |
| WS-AUTH-2 | 코드는 **콘솔(stdout)에 접속 URL 로 한 번만** 출력한다. 로그 파일·stderr 에는 절대 쓰지 않는다 |
| WS-AUTH-3 | **1회용, 2분 만료.** 유효한 코드는 동시에 하나뿐 — 새 코드를 발급하면 이전 코드는 즉시 무효 |
| WS-AUTH-4 | 코드는 상수 시간 비교(`MessageDigest.isEqual`)로 검증한다 |
| WS-AUTH-5 | 틀린 코드가 **10번** 들어오면 현재 코드를 무효로 하고 콘솔에 경고한다(D4). 새 코드는 콘솔에서 Enter 로만 발급 |
| WS-AUTH-6 | 코드 재발급용 웹 엔드포인트는 두지 않는다. **콘솔에서 Enter** 를 눌러야만 새 코드가 나온다 |
| WS-AUTH-7 | `GET /login?code=…` 성공 → 세션 쿠키 발급 + `303` 으로 `/` 이동(주소창에 코드가 남지 않게). 실패 → 본문 없는 401 |
| WS-AUTH-8 | 브라우저 자동 열기(`TOSSINVEST_WEB_OPEN_BROWSER`, 기본 true — D3)는 OS 기본 브라우저에 URL 을 넘긴다. 실패해도 서버는 계속 돌고 콘솔 URL 을 쓰면 된다 |

### 4.4 세션 (WS-SES)
| ID | 규칙 |
|---|---|
| WS-SES-1 | 쿠키: 이름 `tim_session`, 값 256비트 랜덤, `HttpOnly; SameSite=Strict; Path=/`. `Domain`·`Max-Age`·`Expires` 없음(브라우저 종료 시 삭제) |
| WS-SES-2 | 세션은 **서버 메모리에만** 둔다. 파일·DB 저장 없음. 서버를 끄면 모든 세션 소멸 |
| WS-SES-3 | 동시 세션 **1개**. 새로 로그인하면 이전 세션은 즉시 무효 |
| WS-SES-4 | 유휴 **8시간**, 절대 **24시간**(D2) 이 지나면 만료 → 다시 콘솔 코드로 로그인 |
| WS-SES-5 | Spring 의 `HttpSession`(JSESSIONID)·URL 재작성 세션을 쓰지 않는다. 우리 쿠키 하나만 쓴다 |

### 4.5 응답 (WS-RES)
| ID | 규칙 |
|---|---|
| WS-RES-1 | 모든 응답(에러 포함)에 붙이는 헤더: `Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; font-src 'none'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'` · `X-Frame-Options: DENY` · `X-Content-Type-Options: nosniff` · `Referrer-Policy: no-referrer` · `Cache-Control: no-store` · `Cross-Origin-Opener-Policy: same-origin` · `Cross-Origin-Resource-Policy: same-origin` · `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()` |
| WS-RES-2 | **CORS 를 절대 허용하지 않는다.** `Access-Control-Allow-*` 헤더를 어떤 경우에도 보내지 않는다. `@CrossOrigin`·`CorsRegistry`·`CorsConfiguration` 사용 금지(ArchUnit) |
| WS-RES-3 | JSON 응답은 `application/json; charset=UTF-8` 만. JSONP·HTML 안에 데이터 삽입 없음. 금액은 문자열 decimal + `currency` |
| WS-RES-4 | 에러 응답에 스택트레이스·예외 이름·내부 경로·서버 버전을 넣지 않는다: Spring `spring.web.error.*=never`(✅ P9-1) + Tomcat 기본 에러 페이지의 서버 정보·보고서 끄기 + `Server` 헤더 없음. 인증·검사 실패는 본문 없이 상태 코드만 |
| WS-RES-5 | API 에러 형식: `{"error":{"code","message"(UserMessages 한국어),"requestId"}}`. 상태는 400/401/403/404/405/413/415/421/429/502/503 으로 정규화(토스 상태를 그대로 쓰지 않음) |

### 4.6 데이터 최소화 (WS-DATA)
| ID | 규칙 |
|---|---|
| WS-DATA-1 | 키·토큰·`accountSeq` 는 어떤 응답에도 넣지 않는다. 계좌번호가 필요하면 끝 4자리만 |
| WS-DATA-2 | `/api/status` 는 버전, 키 **설정 여부(true/false)**·환경변수 키 사용 여부(true/false), 주문 기능 켜짐 여부, DRY_RUN 여부만 |
| WS-DATA-3 | 화면은 받은 데이터를 `localStorage`·`sessionStorage`·IndexedDB 에 저장하지 않는다(새로고침하면 다시 조회) |

### 4.7 웹 UI (WS-UI)
| ID | 규칙 |
|---|---|
| WS-UI-1 | jar 내장 정적 파일(`src/main/resources/static/`)만. **CDN·웹폰트·외부 이미지·분석 스크립트 금지**(원칙 1·8). 빌드 도구·npm 없음 |
| WS-UI-2 | 인라인 `<script>`·`<style>`·`on*=` 속성 금지(CSP 로 차단됨). JS·CSS 는 별도 파일 |
| WS-UI-3 | 외부에서 온 텍스트(종목명·에러 메시지 등)는 **`textContent` 로만** 넣는다. `innerHTML`·`outerHTML`·`insertAdjacentHTML`·`document.write`·`eval`·`new Function` 금지 |
| WS-UI-4 | `fetch` 는 같은 출처 상대 경로만, `credentials: "same-origin"`. P9-8 의 POST 는 `X-Tossinvest-Local: 1` 헤더를 붙인다 |
| WS-UI-5 | 정적 파일 안에 `http://`·`https://` 로 시작하는 외부 URL 이 하나도 없어야 한다(테스트로 검사) |

### 4.8 로그·콘솔 (WS-LOG)
| ID | 규칙 |
|---|---|
| WS-LOG-1 | Tomcat 접근 로그를 끈다(✅ P9-1). 앱 로그에 요청 URL 을 쓸 때는 쿼리스트링을 빼고 경로만 |
| WS-LOG-2 | `Masking` 에 추가: `tim_session=<값>`, `code=<값>`, `Cookie:`·`Set-Cookie:` 헤더 값 → `***` |
| WS-LOG-3 | 콘솔(stdout)에는 접속 URL·안내 문구만. 조회 결과·금액은 콘솔에 출력하지 않는다 |
| WS-LOG-4 | 콘솔에 "이 주소는 비밀번호와 같습니다. 화면 공유·스크린샷에 주의하세요" 를 함께 출력한다 |

### 4.8a 화면 키 입력 (WS-KEY) — RC-003 (2026-11-27 승인)
| ID | 규칙 |
|---|---|
| WS-KEY-1 | 키가 없어도 `web` 은 기동한다. 키 입력 화면도 콘솔 일회용 코드로 로그인한 뒤에만 열린다(WS-REQ-1~6 그대로) |
| WS-KEY-2 | 저장 위치는 `setup` 과 **같은** OS 자격 증명 저장소(`KeyringCredentialStore`)뿐. 파일·브라우저 저장소·로그 금지 |
| WS-KEY-3 | `POST /api/keys` 하나만. JSON + `X-Tossinvest-Local: 1` + `Content-Length` 필수(411)·4KB 이하(413) — WS-REQ-7·8 |
| WS-KEY-4 | 응답·화면에 키 값을 되돌려 주지 않는다(마스킹 ID 도 없음). 결과는 `saved`·`connected`·한국어 `message` 만. 오류는 `{"error":{"code","message"}}` — 400(입력)·409(환경변수 키 사용 중)·429(시도 초과)·500(저장 실패) |
| WS-KEY-5 | 저장·교체 후 메모리·`token.json` 의 이전 토큰을 버리고 새 키로 발급한다(`TokenManager.reissueAfterKeyChange`). 발급 성공 = 연결 테스트 |
| WS-KEY-6 | 입력칸: Secret `type=password`·`autocomplete=new-password`, ID `autocomplete=off`, 둘 다 `spellcheck=false`. 보내는 즉시 입력칸을 비운다. URL·쿼리에 키를 싣지 않는다 |
| WS-KEY-7 | 요청 바디를 로그에 남기지 않는다. 깨진 JSON 은 컨트롤러에서 고정 문구 400 으로 끝낸다(파서 메시지에 입력 일부가 들어갈 수 있어 기본 경고 로그로 넘기지 않음). 실패 로그는 예외 종류·결과 종류만 |
| WS-KEY-8 | 저장 시도 1분 5회. 환경변수 키가 있으면 거부(그쪽이 우선이라 저장해도 안 쓰임). 앞뒤 공백은 지우고, 안쪽 공백·비 ASCII·ID 200자/Secret 500자 초과는 400 |
| WS-KEY-9 | 화면에서는 저장·교체만. 삭제는 콘솔 `reset-keys`, Claude Code 등록은 `setup`(외부 프로세스 실행 경로를 웹에 두지 않음 — RC-003 E2) |

### 4.9 주문 (WS-ORD) — P9-8, 조회 완료·P4-1 영속화·👤 착수 승인 후
| ID | 규칙 |
|---|---|
| WS-ORD-1 | `TOSSINVEST_ENABLE_ORDERS=false` 면 주문 엔드포인트를 **등록하지 않는다**(404) |
| WS-ORD-2 | `POST /api/orders/preview` → `previewId`(1회용·5분) → `POST /api/orders/place {previewId}`. MCP 와 **같은** `OrderService`·`GuardrailService`·`AuditLogger`·DRY_RUN |
| WS-ORD-3 | UI 는 미리보기 내용을 보여 주고 사용자가 [주문 확정]을 눌러야만 place 를 부른다. 자동 확정·일괄 주문 없음 |
| WS-ORD-4 | 1억원 이상 주문의 `confirmHighValueOrder` 를 코드가 자동으로 true 로 만들지 않는다(절대 원칙 7) |
| WS-ORD-5 | WS-REQ-7(JSON + 커스텀 헤더) 필수 |

### 4.10 공급망 (WS-SUP)
| ID | 규칙 |
|---|---|
| WS-SUP-1 | 웹 의존성은 `spring-boot-starter-webmvc` 하나뿐(승인 2026-11-27). Actuator·DevTools·H2 콘솔·Spring Security·템플릿 엔진 추가 금지(추가하려면 👤 승인) |
| WS-SUP-2 | 버전은 `gradle.lockfile` + sha256 `verification-metadata.xml` 로 고정. Boot 버전을 올릴 때 Tomcat 보안 공지를 확인 |

### 4.11 운영 규칙 (WS-OPS) — 사용자에게 README 로 안내
| ID | 규칙 |
|---|---|
| WS-OPS-1 | **ngrok·포트포워딩·리버스 프록시·원격 데스크톱 터널 등으로 외부에 공개 금지.** (Host 검사로 대부분 막히지만 시도 자체를 금지) |
| WS-OPS-2 | 여러 사람이 쓰는 PC·공용 계정에서는 실행하지 않는다 |
| WS-OPS-3 | 콘솔의 접속 URL 을 공유하거나 스크린샷을 남기지 않는다. 다 쓰면 콘솔에서 Ctrl+C |
| WS-OPS-4 | 친구 대신 실행·설정 금지, 로그·스크린샷 받기 금지(기존 원칙 운영 규칙 그대로) |

---

## 5. 남는 위험 (알고 받아들이는 것)
| 위험 | 이유 | 완화 |
|---|---|---|
| 같은 PC 의 **다른 127.0.0.1 웹 서버**(예: 개발 중인 다른 앱, 포트 3000)도 브라우저가 `tim_session` 쿠키를 보낸다 — 쿠키는 **포트를 구분하지 않는다** | 브라우저 쿠키 규격 | `HttpOnly` 라 JS 로는 못 읽음. 그 서버가 악성이면 T3(내 계정 악성코드) 범위. 세션 1개·유휴 만료로 피해 기간 제한. README 에 명시 |
| 접속 URL 이 브라우저 방문 기록에 남는다(303 이동 전 주소) | 브라우저 동작 | 코드는 1회용·2분 만료라 기록에서 꺼내 써도 무효 |
| 브라우저 자동 열기 시 URL 이 잠깐 OS 프로세스 인자로 보인다 | OS 동작 | 같은 PC 사용자만 볼 수 있음 + 1회용·2분. 싫으면 `TOSSINVEST_WEB_OPEN_BROWSER=false` |
| HTTP(평문) | 루프백 | 패킷이 PC 밖으로 나가지 않음 |
| (RC-003) 화면에서 키를 입력하는 동안 키가 브라우저 메모리·JS 문자열로 잠시 존재 | 브라우저 동작 | 보낸 즉시 입력칸 비움, 저장소·URL 에 남기지 않음. 내 계정 악성 브라우저 확장은 입력을 볼 수 있음(setup 창의 키보드 후킹과 같은 범위, §3) |
| 내 계정 악성코드·악성 브라우저 확장 | 구조적 한계 | 범위 밖(§3). `serve` 모드도 동일 |

## 6. 검증 (규칙 → 테스트 → 작업)
| 규칙 | 테스트 | 작업 |
|---|---|---|
| WS-NET-1~3 | `WebModeStartupTest`(LAN 주소 연결 거부 실측), `LoopbackGuardTest`, `WebLauncherTest` | ✅ P9-1 |
| WS-NET-4, WS-REQ-1~6 | MockMvc: 원격 주소 비루프백 403, 잘못된 Host 421, `localhost:<포트>`·공격자 도메인 Host 421, 교차 Origin·`Origin: null` 403, `Sec-Fetch-Site: cross-site`/`same-site` 403, POST·OPTIONS·TRACE 405, 쿠키 없음 401(정적 파일 포함) | ✅ P9-2 |
| WS-AUTH-1~7 | 코드 1회용(재사용 401), 2분 후 만료(주입 `Clock`), 새 코드 발급 시 이전 코드 무효, 10회 실패 시 무효, 성공 시 303 + 쿠키 속성 | ✅ P9-2 |
| WS-SES-1~5 | 쿠키 속성 문자열 검사, 새 로그인 시 이전 세션 401, 유휴 8h·절대 24h 만료, `JSESSIONID` 없음 | ✅ P9-2 |
| WS-RES-1~4 | 200·401·403·404·421 응답 모두에 보안 헤더 존재, `Access-Control-Allow-*` 없음(프리플라이트 포함), 404 본문 없음(`SilentErrorController`), `Server` 헤더 없음 | ✅ P9-2 |
| WS-RES-2 (코드) | ArchUnit: `@CrossOrigin`·`CorsRegistry`·`CorsConfiguration` 의존 금지 | P9-6 |
| WS-LOG-1~2 | `MaskingTest` 에 `tim_session`·`code=`·`Cookie:` 케이스, 로그 파일에 코드·쿠키 문자열 없음 | ✅ P9-2 (`MaskingTest`) |
| WS-REQ-8, WS-RES-5, WS-DATA-1~2 | REST 입력 검증 400, 에러 형식·상태 정규화, `/api/status` 에 키 값 없음 | P9-4 (`/api/status` 는 P9-2 와 함께) |
| WS-KEY-1~9 | `KeySetupServiceTest`(저장·공백 정리·입력 거부·환경변수 거부·1분 5회·토스 거부 시 안내·교체 시 이전 토큰 폐기·저장 실패 메시지 누출 없음), `LocalAccessFilterTest`(세션 없음 401·헤더 없음 403·폼/텍스트 415·교차 출처 403·4KB 초과 413·다른 경로 POST 405·깨진 JSON 400·응답에 키 없음), `TokenManagerTest`(재발급), `WebLauncherTest`(키 없이 기동) | ✅ P9-9 |
| WS-UI-1~5 | 정적 파일 외부 URL 0개, `innerHTML`·`eval`·인라인 스크립트 0개 (파일 스캔 테스트) | ✅ `StaticUiTest`(P9-2 와 함께, D5). 조회 화면 추가 시 P9-5 에서 유지 |
| WS-ORD-* | 커스텀 헤더 없음 403, 폼 Content-Type 415, previewId 재사용 거부, DRY_RUN 주문 API 0회, enable-orders=false 404 | P9-8 |
| WS-NET-7, 전체 | 👤 실사용: 방화벽 창 없음, 다른 기기 접속 불가, 코드 없이 API 직접 열면 401 | P9-7 |

## 7. 결정 항목 — 2026-11-27 👤 모두 추천안으로 승인
| ID | 질문 | 결정 | 이유 |
|---|---|---|---|
| D1 | `Host: localhost:<포트>` 도 허용할까? | **127.0.0.1 만** | 쿠키는 호스트별이라 둘 다 허용하면 출처가 2개가 되어 검사·테스트가 늘어난다. 콘솔 URL 은 항상 127.0.0.1 이라 불편 없음. (ARCHITECTURE §12.2 는 둘 다 허용으로 적혀 있음 → 승인 시 수정) |
| D2 | 유휴 8시간 외에 **절대 만료 24시간**을 둘까? | **둔다** | 켜 둔 채 며칠 지나도 세션이 영원히 살아 있지 않게 |
| D3 | 브라우저 자동 열기 기본값 | **켬** | "실행하면 바로 확인" UX. 끄고 싶으면 환경변수 |
| D4 | 틀린 코드 10회면 코드 무효 | **둔다** | 256비트라 추측은 불가능하지만, 공격 시도를 콘솔에 알려 주는 효과 |
| D5 | P9-2 와 함께 `/api/status` + 최소 첫 화면 + 콘솔 URL 출력·브라우저 열기(P9-4·P9-5 일부)를 앞당길까? | **앞당긴다** | 실행 즉시 브라우저에서 확인 가능. 계좌·시세 데이터는 여전히 P9-3·P9-4 이후 |

## 8. 이 문서를 바꿀 때
- 규칙 추가·완화는 `requirements/CHANGES.md` 에 RC 로 등록 → 👤 승인 → 이 문서·ARCHITECTURE §12·TASKS 반영.
- **완화**(검사 하나 빼기, CORS 허용, 바인딩 주소 설정 가능하게 등)는 원칙 8 위반 가능성이 있으므로 DECISIONS 에도 근거를 남긴다.

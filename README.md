# tossinvest-mcp

토스증권 Open API 를 감싸는 **비공식 로컬 MCP 서버** (Java 21 / Spring Boot 4 / Spring AI MCP).
Claude Desktop·Claude Code 에서 자연어로(MCP), 또는 내 PC 브라우저에서(`web`, 127.0.0.1 전용) 시세 조회·잔고 확인·주문을 할 수 있게 하는 것이 목표입니다.

> ⚠️ **개발 중** — 아직 일반 사용자용이 아닙니다. 설치 가이드는 P6-1 에서 작성합니다.
>
> - 토스증권과 무관한 비공식 프로젝트이며, 투자 조언이 아닙니다.
> - 사용으로 발생한 모든 손실의 책임은 사용자 본인에게 있습니다.
> - 외부 통신은 토스증권 호스트(`openapi.tossinvest.com`, `openapi-ws.tossinvest.com`)뿐이며,
>   도구 결과(시세·잔고 등)는 사용자 본인의 AI 클라이언트로만 전달됩니다.

## 원칙
- 배포하는 것은 소스코드뿐. 각 사용자는 **자기 키로 자기 PC 에서만** 실행합니다.
- 키는 저장소에 두지 않습니다. 환경변수 또는 OS 자격 증명 저장소(`setup`, P1-3)에서만 읽습니다.
- 안전한 기본값: 주문 기능 꺼짐, DRY RUN, 낮은 한도, 허용 종목 비어 있으면 주문 불가.

자세한 내용: [docs/governance/PRINCIPLES.md](docs/governance/PRINCIPLES.md) · [docs/design/ARCHITECTURE.md](docs/design/ARCHITECTURE.md) · [docs/requirements/TASKS.md](docs/requirements/TASKS.md)

## 사용 방법 (미리보기 — 정식 배포 전)
먼저 토스증권 PC 웹(WTS) > 설정 > Open API 에서 **키를 발급**하고, **허용 IP 에 내 PC 의 공인 IP 를 등록**하세요.

**Claude Desktop** (⏸ `.mcpb` 배포 보류 — RC-001) — `tossinvest-mcp-<버전>-win32.mcpb` 더블클릭 → Client ID / Secret 붙여넣기 → 설치 → Claude 에게 "삼성전자 현재가 알려줘"

**Claude Code** — `tossinvest-mcp.exe`(또는 jar) 더블클릭 → 설정 창에 키 붙여넣기 → [저장하고 연결 테스트]
→ 키 저장, 연결 확인, Claude Code 등록이 한 번에 끝납니다. 터미널을 좋아하면 `tossinvest-mcp setup`.

**내 PC 브라우저 (`web` 모드)** — Claude 없이 브라우저에서 조회만 할 때:
```bash
java -jar tossinvest-mcp-<버전>.jar web
```
1. 콘솔에 접속 주소(`http://127.0.0.1:8765/login?code=…`)가 나오고 브라우저가 자동으로 열립니다.
   이 주소는 **2분 안에 한 번만** 쓸 수 있고 비밀번호와 같습니다 — 공유·화면 공유·스크린샷에 주의하세요. 새 주소는 콘솔에서 Enter.
2. 키가 없으면 화면의 "API 키 입력" 칸에 붙여넣고 [저장하고 연결 테스트] (setup 과 같은 OS 자격 증명 저장소에 저장).
3. 종목 검색 → 현재가, 매수 가능 금액, 주문 내역을 볼 수 있습니다. **주문은 이 화면에서 할 수 없습니다.**
4. 끝내려면 콘솔에서 Ctrl+C.

- 이 PC 에서만 열립니다(`127.0.0.1` 전용). 같은 와이파이의 다른 기기·인터넷에서는 접속할 수 없고, Windows 방화벽 허용 창이 뜨면 허용하지 마세요.
- 포트를 바꾸려면 환경변수 `TOSSINVEST_WEB_PORT`(1024~65535), 브라우저 자동 열기를 끄려면 `TOSSINVEST_WEB_OPEN_BROWSER=false`.
- 같은 PC 에서 다른 `127.0.0.1` 개발 서버를 함께 쓰면, 브라우저 쿠키는 포트를 구분하지 않아 그 서버도 세션 쿠키를 받습니다(읽을 수는 없음 — `HttpOnly`). 보안 설계: [docs/design/WEB_SECURITY.md](docs/design/WEB_SECURITY.md)

키는 내 PC 의 OS 자격 증명 저장소(Windows 자격 증명 관리자 등)에만 저장됩니다. 지우려면 `tossinvest-mcp reset-keys`.

## 개발자용 빌드
```bash
./gradlew check bootJar   # build/libs/tossinvest-mcp-<버전>.jar
./gradlew mcpb            # build/mcpb/*.mcpb (+ build/jpackage/tossinvest-mcp/ 실행 파일, JDK 불필요)
```

## 라이선스
[MIT](LICENSE)

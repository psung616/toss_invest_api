# tossinvest-mcp

토스증권 Open API 를 감싸는 **비공식 로컬 MCP 서버** (Java 21 / Spring Boot 4 / Spring AI MCP).
Claude Desktop·Claude Code 에서 자연어로 시세 조회·잔고 확인·주문을 할 수 있게 하는 것이 목표입니다.

> ⚠️ **개발 중 (P0~P1 단계)** — 아직 일반 사용자용이 아닙니다. 설치 가이드는 P6-1 에서 작성합니다.
>
> - 토스증권과 무관한 비공식 프로젝트이며, 투자 조언이 아닙니다.
> - 사용으로 발생한 모든 손실의 책임은 사용자 본인에게 있습니다.
> - 외부 통신은 토스증권 호스트(`openapi.tossinvest.com`, `openapi-ws.tossinvest.com`)뿐이며,
>   도구 결과(시세·잔고 등)는 사용자 본인의 AI 클라이언트로만 전달됩니다.

## 원칙
- 배포하는 것은 소스코드뿐. 각 사용자는 **자기 키로 자기 PC 에서만** 실행합니다.
- 키는 저장소에 두지 않습니다. 환경변수 또는 OS 자격 증명 저장소(`setup`, P1-3)에서만 읽습니다.
- 안전한 기본값: 주문 기능 꺼짐, DRY RUN, 낮은 한도, 허용 종목 비어 있으면 주문 불가.

자세한 내용: [docs/PRINCIPLES.md](docs/PRINCIPLES.md) · [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/TASKS.md](docs/TASKS.md)

## 개발자용 빠른 실행
```bash
./gradlew check bootJar
java -jar build/libs/tossinvest-mcp-0.1.0-SNAPSHOT.jar --help
```

## 라이선스
[MIT](LICENSE)

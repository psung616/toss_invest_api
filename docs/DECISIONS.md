# 토스증권 MCP 서버 — 공개 배포 & 개인정보 원칙

## 확정된 결정 (2026-11-23)
- 구현 언어/프레임워크: **Java 21 + Spring Boot 4.x + Spring AI MCP(STDIO)**, Gradle
- 작업 문서: 로컬 저장소의 `CLAUDE.md`, `docs/TASKS.md`(P0~P7 작업 목록), `docs/ARCHITECTURE.md` 등
- 패키지 루트: `io.github.psung616.tossmcp`
- 키 보관: 저장소 안 키 파일(.env 등) 없음. 환경변수 또는 OS 자격 증명 저장소만
- 키체인 구현: **JNA(`net.java.dev.jna:jna`) 직접 바인딩**. java-keyring 1.0.4 는 2023-08 이후 릴리스·커밋이 없어 제외
  (macOS 는 기본 `security` 명령, Linux 는 `secret-tool` 명령 — 비밀값은 stdin 으로만 전달)
- 설치 UX: Claude Desktop 은 `.mcpb` 원클릭 확장(키는 Claude Desktop 이 OS 저장소에 보관),
  Claude Code 는 setup 마법사(키 저장 + 연결 테스트 + 자동 등록). 배포물은 jpackage 로 Java 런타임 포함

## 확정된 원칙
1. 배포하는 것은 **소스코드뿐**이다. API로 받은 데이터(시세·잔고·종목 마스터 등)는 저장소에 넣지 않는다.
2. **남의 API 키를 어디에도 저장하지 않는다.** 내 서버·프록시·중계 서비스는 운영하지 않는다.
3. **유료 제공 없음.** 공유 범위는 가까운 친구에게 무료로 쓰게 하는 정도.
4. 친구의 개인정보·키·계좌·거래 데이터는 **구조적으로 내가 알 수 없어야** 한다.

## 원칙을 코드로 보장하는 장치
- 100% 로컬 실행(stdio). 친구 PC → 토스증권 API 직접 통신. 내 인프라 경유 없음.
- HTTP 클라이언트에 **호스트 허용목록 하드코딩**: `openapi.tossinvest.com`, `openapi-ws.tossinvest.com` 외 통신 거부 → "외부로 안 보낸다"를 코드 한 곳에서 검증 가능. ArchUnit으로 HTTP 사용 위치 강제.
- 텔레메트리·에러 리포팅(Sentry 등)·자동 업데이트 확인·Spring Boot Actuator 없음.
- 키는 환경변수 또는 OS 자격 증명 저장소(Windows 자격 증명 관리자 / macOS 키체인 / Linux Secret Service)에서만 읽음. 외부 설정 파일 로딩 차단.
- 토큰 캐시·로그·종목 캐시는 OS 사용자 폴더(`%APPDATA%`, `%LOCALAPPDATA%` 등)에만 저장.
- 로그는 기본적으로 키·토큰·accountSeq·보유수량·금액을 마스킹. serve 모드에서 stdout 금지.
- 버그 제보용 `diagnose` 모드: 버전·OS·설정 키 존재 여부(값 X)·에러 코드·X-Request-Id만 출력.
- 안전한 기본값: `ENABLE_ORDERS=false`, `DRY_RUN=true`, 낮은 금액 한도.
- 의존성 최소화 + Gradle 의존성 잠금·체크섬 검증. 친구에게는 태그 소스 빌드 또는 체크섬 확인된 Release jar 안내.

## 원칙이 깨지기 쉬운 지점 (운영 규칙)
- 친구 대신 설정해 주기(원격 접속, 화면 공유 중 키 입력, 설정 파일 받아보기) 금지 → 셋업 가이드로 대체.
- 친구가 로그·스크린샷을 보낼 때 실데이터 포함 금지(토스 정책상 API 데이터 제3자 배포 금지에도 해당) → `diagnose` 출력만 받기.
- GitHub Issue에 로그 붙여넣기 금지 안내(README·이슈 템플릿).
- 친구는 각자 본인의 토스증권 Open API 신청·키 발급·허용 IP 등록, 본인의 Claude 계정 사용. 내 Claude 계정/API 키 공유 금지.
- MCP 도구 결과(잔고 등)는 친구 본인의 Claude로 전달됨 → README에 명시.

## 근거 메모
- 토스증권 FAQ 데이터 이용 정책: API 정보는 투자자 본인 매매용, 상업적 이용 제외, 비상업적이라도 제3자 배포 엄격 금지.
- 클라이언트당 유효 토큰 1개, 새 토큰 발급 시 기존 토큰 무효화 → 토큰 캐시는 사용자별·로컬.

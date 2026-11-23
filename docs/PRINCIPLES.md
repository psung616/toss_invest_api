# 원칙: 공개 배포 & 개인정보

> 이 문서는 코드보다 우선한다. 구현이 이 원칙과 충돌하면 구현을 바꾼다.

## 확정된 원칙
1. 배포하는 것은 **소스코드뿐**이다. API로 받은 데이터(시세·잔고·종목 마스터·실제 응답 JSON)는
   저장소에 넣지 않는다.
2. **남의 API 키를 어디에도 저장하지 않는다.** 내 서버·프록시·중계 서비스는 운영하지 않는다.
3. **유료 제공 없음.** 공유 범위는 가까운 친구에게 무료로 쓰게 하는 정도.
4. 친구의 개인정보·키·계좌·거래 데이터는 **구조적으로 내가 알 수 없어야** 한다.

## 데이터 흐름 (이 외의 경로는 존재하면 안 됨)
```
사용자 PC ──► 토스증권 API  (키·계좌·주문)
    └─────► 사용자 본인의 AI 클라이언트(Claude)  (도구 결과)
개발자(나): 위 경로 어디에도 없음. 제공하는 것은 GitHub 소스코드뿐
```

## 원칙을 코드로 보장하는 장치
| 장치 | 구현 위치 (Java / Spring Boot) | 검증 방법 |
|---|---|---|
| 100% 로컬 실행(stdio) | Spring AI MCP STDIO, `web-application-type=none` | 웹 서버·원격 전송 코드 없음 |
| 호스트 허용목록 하드코딩 | `client.http.AllowedHostInterceptor` | 허용 외 호스트 요청 시 예외 — 테스트 + ArchUnit |
| 텔레메트리·에러리포팅·업데이트확인 없음 | 전체 (Actuator 금지) | 의존성 검토 + ArchUnit |
| 키는 OS 자격 증명 저장소 또는 환경변수에서만 | `secret` 패키지 (java-keyring) | `.env`·외부 설정 파일 로딩 차단 테스트 |
| 캐시·로그는 사용자 폴더에만 | `config.AppPaths` | 저장소 경로에 쓰기 없음 |
| 로그 마스킹, stdout 금지 | `logback-spring.xml`, `logging.MaskingConverter` | 마스킹 테스트, `ServeStdoutIT` |
| `diagnose` 모드 | `cli.DiagnoseCommand` | 출력에 키·토큰·계좌·금액 없음 — 테스트로 보장 |
| 안전한 기본값 | `application.yml`, `TossProperties` | `enable-orders=false`, `dry-run=true` 기본값 테스트 |
| 돈은 BigDecimal | 전체 | ArchUnit(double/float 금지) |
| 의존성 최소화 + 잠금·체크섬 | `build.gradle`, `gradle.lockfile`, `verification-metadata.xml` | 런타임 의존성 목록 검토 |
| 태그 고정 설치 안내 | `README.md` | 태그 소스 빌드 또는 체크섬 확인된 Release jar |

## 운영 규칙 (코드 밖에서 원칙이 깨지는 지점)
- 친구 대신 설정해 주기(원격 접속, 화면 공유 중 키 입력, 설정 파일 받아보기) **금지** → 셋업 가이드로 대체
- 친구가 로그·스크린샷을 보내는 것 **금지**(토스 정책상 API 데이터 제3자 배포 금지에도 해당) → `diagnose` 출력만 받기
- GitHub Issue에 로그 붙여넣기 금지 — 이슈 템플릿에 명시
- 친구는 각자 본인의 토스증권 Open API 신청·키 발급·허용 IP 등록, 본인의 Claude 계정 사용
- README에 "도구 결과는 사용자 본인의 AI 클라이언트로 전달됨" 명시

## 근거 (2026-11-23 확인)
- 토스증권 Open API FAQ 데이터 이용 정책: API 정보는 투자자 본인 매매용, 상업적 이용 제외,
  비상업적이라도 제3자 배포 엄격 금지.
- 클라이언트당 유효 토큰은 1개, 새 토큰 발급 시 기존 토큰 무효화 → 토큰은 사용자별·로컬에서 관리.

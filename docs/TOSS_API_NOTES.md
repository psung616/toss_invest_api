# 토스증권 Open API 요약 노트

> 확인일: 2026-11-23. **정본은 공식 문서**이며, 필드명·요청 바디·레이트리밋 그룹 매핑은
> 반드시 `docs/api/openapi.json`(REST)과 `docs/api/asyncapi.json`(WebSocket)에서 확인한다.
> 이 두 파일은 `./gradlew fetchSpecs`로 받아 로컬에만 두고 **git에 커밋하지 않는다**.

## 공식 문서 링크
- LLM용 안내: https://developers.tossinvest.com/llms.txt
- 개요(빠른 시작·레이트리밋·에러·WebSocket): https://openapi.tossinvest.com/openapi-docs/overview.md
- REST 정본: https://openapi.tossinvest.com/openapi-docs/latest/openapi.json
- WebSocket 정본: https://openapi.tossinvest.com/openapi-docs/latest/asyncapi.json
- API 레퍼런스(MD): https://openapi.tossinvest.com/openapi-docs/latest/api-reference/README.md
- FAQ: https://openapi.tossinvest.com/openapi-docs/faq.md

## 접속
- REST: `https://openapi.tossinvest.com`
- WebSocket: `wss://openapi-ws.tossinvest.com/ws/v1`
- 허용 IP: WTS 설정 > Open API > 허용 IP 관리. 미등록 IP → `403 ip-not-allowed`
  (토큰 발급은 OAuth2 `access_denied`, WebSocket 핸드셰이크는 `403`)
- 모의투자(샌드박스) 환경: 공식 개요 문서에 언급 없음 → **모든 실호출은 실계좌**

## 인증
- `POST /oauth2/token` — Client Credentials, form-encoded (`grant_type`, `client_id`, `client_secret`)
- 모든 API: `Authorization: Bearer {access_token}`
- 계좌·자산·주문·조건주문 API: `X-Tossinvest-Account: {accountSeq}` (계좌번호 아님, `GET /api/v1/accounts`로 획득)
- 만료 시간은 문서에 명시 없음. 만료 시 `401 expired-token`
- **클라이언트당 유효 토큰 1개.** 새로 발급하면 이전 토큰 즉시 무효(`401 token-revoked`).
  여러 프로세스는 토큰 하나를 공유해야 함
- WebSocket은 토큰 만료 시 연결 종료 → 만료 전 재발급·재연결

## 엔드포인트
**시세**: `GET /api/v1/orderbook`, `/prices`, `/trades`, `/price-limits`, `/candles`
**종목**: `GET /api/v1/stocks`, `/stocks/{symbol}/warnings`, `/investor-trading`, `/program-trades`,
`/short-selling`, `/credit-trades`, `/securities-lending`
**시장정보**: `GET /api/v1/exchange-rate`, `/market-calendar/KR`, `/market-calendar/US`
**랭킹**: `GET /api/v1/rankings`, `/rankings/investor-trading`
**시장지표**: `GET /api/v1/market-indicators/prices`, `/market-indicators/{symbol}/candles`, `/investor-trading`
**섹터**: `GET /api/v1/sectors`, `/sectors/{sectorId}`, `/sectors/{sectorId}/stocks`, `/etfs`, `/sectors/rankings`
**계좌·자산**: `GET /api/v1/accounts`, `/holdings`
**주문**: `POST /api/v1/orders`, `POST /orders/{orderId}/modify`, `POST /orders/{orderId}/cancel`,
`GET /orders`, `GET /orders/{orderId}`, `GET /buying-power`, `/sellable-quantity`, `/commissions`
**조건주문**: `POST /api/v1/conditional-orders`, `POST /conditional-orders/{id}/modify`,
`DELETE /conditional-orders/{id}`, `GET /conditional-orders`(OPEN/CLOSED), `GET /conditional-orders/{id}`

## 레이트리밋 (클라이언트 × 그룹, 초당 요청 수)
| 그룹 | 한도 | 그룹 | 한도 |
|---|---|---|---|
| AUTH | 5 | MARKET_INDICATOR_PRICE | 10 |
| ACCOUNT | 1 | MARKET_INDICATOR | 10 |
| ASSET | 5 | MARKET_INDICATOR_CHART | 5 |
| TRANSACTION | 5 | SECTOR | 5 |
| STOCK | 5 | SECTOR_RANKING | 5 |
| STOCK_ALL | 1 | ORDER | 10 |
| STOCK_TRADING_TREND | 10 | ORDER_HISTORY | 5 |
| MARKET_INFO | 3 | ORDER_INFO | 6 |
| MARKET_DATA | 15 | CONDITIONAL_ORDER | 5 |
| MARKET_DATA_CHART | 20 | CONDITIONAL_ORDER_HISTORY | 10 |
| RANKING | 5 | | |

- 확인된 매핑: `/accounts` → ACCOUNT, `/holdings` → ASSET. **나머지 엔드포인트→그룹 매핑은 openapi.json에서 확인**
- 09:00~09:10 KST: ORDER는 10 유지, **ORDER_INFO는 3으로 감소**
- 한도는 예고 없이 변경 가능 → 응답 헤더 `X-RateLimit-Limit` 참고
- 429 응답에 `Retry-After` 포함. 지수 백오프 + 지터 권장
- 짧은 시간 대량 소액 주문은 남용으로 간주되어 거래가 일시 제한될 수 있음

## 에러 모델
- 성공: `{"result": ...}` / 실패: `{"error": {requestId, code, message, data}}`
- `requestId` = 응답 헤더 `X-Request-Id` (고객센터 문의 시 사용)
- 400: `invalid-request`, `confirm-high-value-required`, `account-header-required` 등
- 401: `invalid-token`, `expired-token`, `token-revoked`, `edge-blocked`, `login-user-not-found`
- 403: `ip-not-allowed`, `forbidden`, `edge-blocked`
- 404: `stock-not-found`, `order-not-found`, `conditional-order-not-found`, `account-not-found` 등
- 409: 주문 상태 충돌(이미 체결/취소/정정/거부/처리중), `request-in-progress`, `opposite-pending-order-exists`
- 422: `insufficient-buying-power`, `order-hours-closed`, `price-out-of-range`, `duplicate-conditional-order`,
  `max-order-amount-exceeded`, `idempotency-key-conflict`, `condition-already-met` 등
- 429: `rate-limit-exceeded`, `edge-rate-limit-exceeded` / 500: `internal-error`, `maintenance`

## 주문 규칙
- 주문 유형: LIMIT(지정가), MARKET(시장가) — KR, US
- 조건주문: SINGLE, OCO, OTO (각각 LIMIT 또는 MARKET 실행). **OCO/OTO는 종목당 1개만**
- 정정(가격·수량) 가능, 일부 주문은 `modify-restricted`/`cancel-restricted`
- 미국 금액 기반 주문은 정규장에만
- **1억원 이상**: `confirmHighValueOrder: true` 필요 (생성·정정·지정가 조건주문). 위치는 openapi.json 확인
- **30억원 초과**: `max-order-amount-exceeded`로 거부
- **멱등성**: `clientOrderId`. 처리 중 재전송 → `request-in-progress`, 같은 ID 다른 내용 → `idempotency-key-conflict`
- 같은 종목 반대 방향 미체결 주문 존재 시 `opposite-pending-order-exists`

## WebSocket (P7에서 사용)
- TLS 필수, **계정당 동시 연결 2개** (3번째 연결 시 가장 오래된 연결 종료)
- 연결당 구독 최대 100개(개인 채널의 accountSeq 포함). 구독 선언은 전체 집합을 교체
- 구독 선언 초당 5회. 초과 시 `rate-limit-exceeded`, 약 1초 후 재전송 (`Retry-After` 없음)
- 클라이언트 메시지 없이 180초 지나면 종료 → **약 60초마다 `PING`**
- 체결·호가 채널은 손실 가능(lossy), 개인 주문·조건주문 채널은 세션 내 무손실. 2초 이상 적체 시 연결 종료
- 구독 직후 초기 스냅샷 없음 → 시작 시 REST로 현재값 조회
- 재연결 후 `GET /api/v1/orders`, `GET /api/v1/conditional-orders`로 재동기화

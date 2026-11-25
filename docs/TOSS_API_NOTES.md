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
- 만료: 토큰 응답 `expires_in`(초, 예시 86400). 만료 시 `401 expired-token` (P0-6 확인)
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

- 엔드포인트→그룹 전체 매핑: 하단 "스펙 확인 결과" 표
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
- **1억원 이상**: `confirmHighValueOrder: true` 필요 (생성·정정·지정가 조건주문). 위치: 요청 바디 최상위 boolean
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

---

## 스펙 확인 결과 (P0-6, 2026-11-25 · openapi.json v1.2.28 + overview.md)

> 출처: `docs/api/openapi.json` 각 operation description 끝의 `**Rate Limits Group**: ...` 표기,
> overview.md "Rate Limits" 절. 스펙에 없는 항목은 "없음"으로 적었다(추측 금지).

### 엔드포인트 → 레이트리밋 그룹 (40개 경로 · 43개 operation 전부)
| 그룹 | 초당 한도 | 09:00~09:10 KST | 엔드포인트 | 계좌 헤더 |
|---|---|---|---|---|
| AUTH | 5 | — | `POST /oauth2/token` | — |
| MARKET_DATA | 15 | — | `GET /api/v1/orderbook`, `/prices`, `/trades`, `/price-limits` | — |
| MARKET_DATA_CHART | 20 | — | `GET /api/v1/candles` | — |
| STOCK | 5 | — | `GET /api/v1/stocks`, `/stocks/{symbol}/warnings` | — |
| STOCK_ALL | 1 | — | `GET /api/v1/stocks/all` | — |
| STOCK_TRADING_TREND | 10 | — | `GET /api/v1/stocks/{symbol}/investor-trading`, `/program-trades`, `/short-selling`, `/credit-trades`, `/securities-lending` | — |
| MARKET_INFO | 3 | — | `GET /api/v1/exchange-rate`, `/market-calendar/KR`, `/market-calendar/US` | — |
| RANKING | 5 | — | `GET /api/v1/rankings`, `/rankings/investor-trading` | — |
| MARKET_INDICATOR | 10 | — | `GET /api/v1/market-indicators/prices`, `/market-indicators/{symbol}/investor-trading` | — |
| MARKET_INDICATOR_CHART | 5 | — | `GET /api/v1/market-indicators/{symbol}/candles` | — |
| MARKET_INDICATOR_PRICE | 10 | — | **해당 엔드포인트 없음** (overview 표에만 존재) | — |
| SECTOR | 5 | — | `GET /api/v1/sectors`, `/sectors/{sectorId}`, `/sectors/{sectorId}/stocks`, `/sectors/{sectorId}/etfs` | — |
| SECTOR_RANKING | 5 | — | `GET /api/v1/sectors/rankings` | — |
| ACCOUNT | 1 | — | `GET /api/v1/accounts` | 불필요 |
| ASSET | 5 | — | `GET /api/v1/holdings` | 필요 |
| TRANSACTION | 5 | — | `GET /api/v1/transactions` | 필요 |
| ORDER | 10 | **10 (유지)** | `POST /api/v1/orders`, `/orders/{orderId}/modify`, `/orders/{orderId}/cancel` | 필요 |
| ORDER_HISTORY | 5 | — | `GET /api/v1/orders`, `/orders/{orderId}` | 필요 |
| ORDER_INFO | 6 | **3** | `GET /api/v1/buying-power`, `/sellable-quantity`, `/commissions` | 필요 |
| CONDITIONAL_ORDER | 5 | — | `POST /api/v1/conditional-orders`, `/conditional-orders/{id}/modify`, `DELETE /conditional-orders/{id}` | 필요 |
| CONDITIONAL_ORDER_HISTORY | 10 | — | `GET /api/v1/conditional-orders`, `/conditional-orders/{id}` | 필요 |

- 기존 노트의 ORDER_INFO 기본 한도 "6"은 overview 와 일치. `/transactions`(TRANSACTION)는 기존 엔드포인트 목록에 없던 항목.
- 응답 헤더(overview): **정상·429 모두** `X-RateLimit-Limit`(초당 허용 = burst), `X-RateLimit-Remaining`, `X-RateLimit-Reset`(토큰 1개 재충전 예상 초).
  `Retry-After`(초, 정수)는 **429 에만**. openapi.json 에는 429 응답(`RateLimitExceeded`)에만 헤더가 정의돼 있음.
- 비정상 의심 IP 대역은 초당 1회로 제한될 수 있음(overview). 권장: Retry-After 대기 + 지수 백오프(1s→2s→4s) + 지터.

### 토큰 응답
- `OAuth2TokenResponse` 필수 `access_token`(JWT), `token_type`(`Bearer`), `expires_in`(int64, **만료까지 남은 초**, 예시 86400).
  공통 `{"result":...}` 봉투를 쓰지 않는 OAuth2 표준 형식. → 기존 "만료 시간 문서에 명시 없음"은 **정정: `expires_in` 으로 제공**.

### 주문 요청 바디 (`OrderCreateRequest`, `oneOf`)
- 수량 기반: 필수 `symbol`, `side`(BUY/SELL), `orderType`(LIMIT/MARKET), `quantity` / 선택 `clientOrderId`, `timeInForce`(DAY 기본·CLS·OPG), `price`, `confirmHighValueOrder`
- 금액 기반(US MARKET 전용): `quantity` 대신 `orderAmount`
- `quantity`·`price` 는 **문자열**(format `decimal`, pattern `^\d+(\.\d+)?$`, 최대 30자). LIMIT 은 price 필수, MARKET 은 price 전달 시 400.
  KR 가격은 정수·호가 단위 준수(틀리면 400 + `data` 에 올바른 호가 단위). 소수점 수량은 US 시장가 매도만.
- 정정(`OrderModifyRequest`): 필수 `orderType`, KR 은 `quantity` 필수(정수), US 는 quantity 전달 불가(`us-modify-quantity-not-supported`).

### `clientOrderId`
- 선택. 최대 36자, `^[a-zA-Z0-9\-_]+$`. 서버가 자동 생성하지 않음. **멱등성 키 유효 10분**, 이후 같은 값은 새 주문.
- 현재 코드는 `PreviewStore` 의 `UUID.randomUUID().toString()`(36자, 16진수+`-`)을 사용 → 형식 적합.

### `confirmHighValueOrder`
- 위치: 주문 생성·정정 요청 바디 **최상위 boolean**, 기본 false. 조건주문 생성 바디에도 존재.
- 1억원 이상인데 false → `400 confirm-high-value-required`. 30억원 이상은 플래그와 무관하게 `422 max-order-amount-exceeded`.

### 종목 마스터 (`GET /api/v1/stocks/all`)
- 쿼리: `market`(필수: KOSPI·KOSDAQ·NYSE·NASDAQ·AMEX·KR_ETC·US_ETC), `status`(기본 ACTIVE), `securityType`(선택)
- 응답: `result` 배열 of `ListedStock` — `symbol`, `name`, `securityType`, `isCommonShare`(boolean), `isinCode` (모두 필수)

### 금액 필드 타입
- 스키마 전체에 `type: number` 필드 **0개**. 금액·가격·수량 120개 필드가 모두 `string` + `format: decimal` → `BigDecimal(String)` 으로 파싱.

### Spring AI 2.1.0 (실제 jar 확인)
- `spring-ai-mcp-annotations-2.1.0.jar`: `org.springframework.ai.mcp.annotation.McpTool`, `McpTool$McpAnnotations`, `McpToolParam`
- `spring-ai-autoconfigure-mcp-server-common-2.1.0.jar` 프로퍼티(기본값): `spring.ai.mcp.server.enabled=true`, `.stdio=false`, `.type=sync`,
  `.name=mcp-server`, `.version=1.0.0`, `.annotation-scanner.enabled=true` → 현재 `application.yml`(name·version·type·stdio=false, serve 에서 true) 과 일치.

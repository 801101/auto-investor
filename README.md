# Auto Investor

로컬 PC에서 실행하는 Spring Boot 기반 한국투자증권 OpenAPI 자동매매 프로젝트입니다. 현재 빌드는 Gradle, Java 21, MyBatis XML Mapper, SQLite 구조를 유지합니다.

## Dry Run

기본값은 실전 주문 차단입니다.

```yaml
runtime.trading-enabled: false
investment.live-trading-enabled: false
```

`investment.live-trading-enabled=false`이면 `DryRunOrderExecutor`가 사용되어 KIS 매수/매도 주문 API를 호출하지 않고 `orders`와 `audit_log`에 주문 예정 내역만 기록합니다.

## Local KIS 설정

민감정보는 Git에 올리지 않는 `src/main/resources/application-local.yml`에만 작성합니다.

```yaml
kis:
    app-key: "발급받은_APP_KEY"
    app-secret: "발급받은_APP_SECRET"
    account-number: "12345678"
    account-product-code: "01"
```

위 형식 그대로 `src/main/resources/application-local.yml`에 작성하면 됩니다.

## 실전 주문 활성화

실제 주문 API 호출은 두 단계가 모두 켜져야 합니다.

```yaml
runtime.trading-enabled: true
investment.live-trading-enabled: true
```

`runtime.trading-enabled=false`이면 스케줄러의 주문 판단 사이클 자체가 건너뛰어집니다. 유지보수 사이클의 계좌 동기화는 별도입니다.

## 주문 단위

```yaml
investment.order-unit-type: AMOUNT
investment.unit-amount: 1000
investment.unit-shares: 1
investment.max-holdings: 50
investment.allow-duplicate-stock: false
```

`AMOUNT`는 한 번의 매수에서 사용할 최대 금액입니다. 실제 주문 가능 잔액이 더 적으면 `min(unit-amount, 실제 주문 가능 잔액)` 안에서 가능한 정수 수량을 계산합니다. 1,000원 주문은 현재가가 1,000원 이하인 종목만 1주 이상 주문 가능합니다. 현재가가 70,000원이면 수량 0으로 판단하고 주문 API를 호출하지 않습니다. 현재가가 100원이면 10주까지 주문될 수 있습니다.

`SHARE`는 `unit-shares` 값 그대로 주문수량을 사용합니다.

`max-holdings`는 종목 수가 아니라 중복을 포함한 전체 보유 주 수 제한입니다. 한 종목을 50주 보유했고 `max-holdings: 50`이면 그걸로 끝이고 신규 매수는 막힙니다. `max-holdings: 0`은 제한 없음입니다.

매수 후보는 먼저 실제 주문 가능 잔액, 현재가, 중복 매수 정책, 최대보유수로 구매 가능 여부를 계산합니다. 구매 가능한 후보만 남긴 뒤 기존 평가점수 기준으로 정렬해 가장 좋은 후보를 선택합니다.

## 손절과 GRAY 만료

익절과 손절은 분리되어 있습니다.

```yaml
investment.take-profit.enabled: true
investment.take-profit.rate: 0.10
investment.stop-loss.enabled: false
investment.stop-loss.rate: -0.10
investment.gray-max-trading-days: 3
```

손절을 켜면 WHITE와 GRAY 모두에서 `profitRate <= stop-loss.rate`일 때 BLACK으로 전환하고 `STOP_LOSS`를 매도 사유로 기록합니다. GRAY 제한 거래일 초과는 `GRAY_TIMEOUT` 사유를 사용합니다.

## 안전장치

```yaml
safety.kill-switch-enabled: false
safety.reject-order-when-balance-sync-failed: true
safety.reject-order-when-price-stale-seconds: 30
safety.reject-order-when-account-mismatch: true
```

kill switch가 켜지면 주문 판단을 차단합니다. 계좌 동기화 실패 상태에서도 신규 주문은 차단됩니다.

## 주문 멱등성

`orders.idempotency_key`에 UNIQUE 인덱스를 둡니다. 같은 `decisionCycleId`에서 같은 계좌, 전략, 종목, 주문 방향으로 다시 요청해도 새 주문 레코드를 만들지 않고 기존 주문 상태를 재사용합니다.

## 생애주기 이력

`positions`는 현재 전략 상태와 현재가, 기준가, 최고가, 최저가, 보유수량, 수익률, GRAY 경과 거래일, 최종 평가 시각을 덮어써서 관리합니다.

`trade_lifecycle_history`는 중요한 사건만 저장합니다. 단순 현재가 변동, 상태 유지 평가, 한 호가 단위 변동, 최고가/최저가 소폭 갱신, 반복 스케줄 실행은 저장하지 않습니다.

대표 흐름:

```text
BUY_FILLED -> WHITE_ENTERED -> WHITE_TO_GRAY -> GRAY_DAY_COUNTED -> WHITE_RECOVERED -> TAKE_PROFIT_TRIGGERED -> SELL_FILLED -> CLOSED
```

일반적인 1회 매수 후 1회 매도 라이프사이클은 보통 6-12건 정도의 이력만 남습니다. GRAY가 오래 유지되면 실제 거래일 증가분만큼 `GRAY_DAY_COUNTED`가 추가됩니다.

## 장 운영시간

```yaml
market.timezone: Asia/Seoul
market.regular-open-time: 09:00
market.regular-close-time: 15:20
```

주문 판단 사이클은 `Asia/Seoul` 기준 정규장 시간에만 실행됩니다. 현재 거래일 서비스는 주말만 제외하며, 국내 증시 휴장일 데이터 연동은 TODO입니다.

## 실행

```powershell
.\gradlew.bat bootRun
```

확인 API:

```text
GET  /api/system/status
GET  /api/system/kis/health
POST /api/trading/run-cycle
POST /api/trading/sync
```

## 테스트

```powershell
.\gradlew.bat test
```

## 남은 KIS 확인 항목

미체결 주문 조회와 주문 상태 조회 엔드포인트, 계좌 환경별 TR ID는 공식 KIS 문서 기준으로 확인 후 연결해야 합니다. 확인되지 않은 필드는 추측 구현하지 않습니다.

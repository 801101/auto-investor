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

예시는 `src/main/resources/application-local.example.yml`을 참고하십시오.

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
investment.max-holding-stocks: 50
investment.allow-duplicate-stock: false
```

`AMOUNT`는 목표 금액을 현재가로 나누어 정수 수량으로 내림 계산합니다. 1,000원 주문에 현재가가 70,000원이면 수량 0으로 판단하고 주문 API를 호출하지 않습니다.

`max-holding-stocks`는 주 수가 아니라 보유 종목 수 제한입니다. 삼성전자 50주만 보유한 경우 보유 종목 수는 1개입니다.

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

## 위험 한도

```yaml
risk.max-daily-loss-rate: -0.03
risk.max-daily-order-count: 100
risk.max-single-order-amount: 10000
risk.max-total-invested-amount: 50000
risk.minimum-cash-reserve: 5000
risk.consecutive-error-stop-count: 5
```

위 값들은 소액 파일럿 기본값입니다. 주문 직전 단일 주문 금액, 일일 주문 수, 총 투자금, 예비 현금, 연속 실패 수를 검사합니다.

## 주문 멱등성

`orders.idempotency_key`에 UNIQUE 인덱스를 둡니다. 같은 `decisionCycleId`에서 같은 계좌, 전략, 종목, 주문 방향으로 다시 요청해도 새 주문 레코드를 만들지 않고 기존 주문 상태를 재사용합니다.

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
POST /api/trading/sync
POST /api/trading/run-cycle
```

## 테스트

```powershell
.\gradlew.bat test
```

## 남은 KIS 확인 항목

미체결 주문 조회와 주문 상태 조회 엔드포인트, 계좌 환경별 TR ID는 공식 KIS 문서 기준으로 확인 후 연결해야 합니다. 확인되지 않은 필드는 추측 구현하지 않습니다.

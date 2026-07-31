# Auto Investor

로컬 PC에서 실행하는 Spring Boot, MyBatis, SQLite 기반 한국투자증권 OpenAPI 자동매매 프로젝트입니다.

## 설정

민감정보는 Git에 올리지 않는 `src/main/resources/application-local.yml`에 작성합니다.

```yaml
runtime:
    trading-enabled: true

investment:
    market:
        type: OVERSEAS
        domestic-market-code: ALL
        overseas-exchange-code: NASD
        overseas-price-exchange-code: NAS
        overseas-currency-code: USD
    order:
        unit-type: SHARE
        unit-amount: 1.00
        unit-shares: 1
    holding:
        allow-duplicate-stock: 1
        max-holdings: 50
    candidate:
        include-etf: false
    strategy:
        take-profit:
            enabled: true
            rate: 0.10
        stop-loss:
            enabled: true
            rate: -0.10
        gray-max-trading-days: 3

kis:
    account-mode: PAPER
    app-key: "발급받은_APP_KEY"
    app-secret: "발급받은_APP_SECRET"
    account-number: "계좌번호_앞_8자리"
    account-product-code: "상품코드_2자리"
```

`runtime.trading-enabled=false`이면 주문 판단을 시작하지 않습니다. `PAPER`는 모의투자, `REAL`은 실전투자입니다. KIS URL, TR_ID, API path는 코드 기본값을 사용하므로 일반 실행에서는 설정하지 않습니다.

## 투자 설정

`investment.market.type`은 `OVERSEAS` 또는 `DOMESTIC`입니다.

`investment.order.unit-type`은 `SHARE` 또는 `AMOUNT`입니다. `SHARE`는 `unit-shares`만큼 주문하고, `AMOUNT`는 `unit-amount` 안에서 주문 가능 수량을 계산합니다.

`investment.holding.allow-duplicate-stock`은 같은 종목의 총 구매 허용 횟수입니다. `0`이면 무제한, `1`이면 1회, `10`이면 10회입니다. 같은 스케줄 안에서는 같은 종목을 여러 번 주문하지 않습니다.

`investment.holding.max-holdings`는 중복 구매를 포함한 전체 보유 슬롯입니다.

국내 실행 시 필요하면 다음만 추가합니다.

```yaml
investment:
    market:
        type: DOMESTIC
        domestic-market-code: ALL
```

해외 실행 시 거래소를 바꾸려면 다음만 조정합니다.

```yaml
investment:
    market:
        overseas-exchange-code: NASD
        overseas-price-exchange-code: NAS
        overseas-currency-code: USD
```

## 후보 선정

종목 코드는 설정에 넣지 않습니다. 국내/해외 종목 마스터를 DB에 저장하고 후보를 순환 선택합니다.

후보 필터는 주문 가능성과 전략 운영에 필요한 것만 남겼습니다.

- 거래 가능 종목
- 거래정지, 정리매매, 관리종목, SPAC 등 제외
- ETF 포함 여부
- 보유 슬롯과 동일 종목 구매 횟수
- 미체결 또는 예약 주문 제외
- 실패 후 짧은 재시도 대기
- 해외 금액 주문은 소수점 가능 확인 종목만 사용

절대 가격, 시가총액, 거래대금 필터는 사용하지 않습니다. 현재가, 잔액, 주문수량은 주문 직전에 다시 조회해서 검증합니다.

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
GET  /api/domestic/dashboard
GET  /api/overseas/dashboard
```

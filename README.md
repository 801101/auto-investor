# auto-investor

한국투자증권(KIS) 계좌를 기준으로 보유 종목을 동기화하고, 국내 또는 해외 주식의 매수·매도 생애주기를 관리하는 로컬 단일 프로세스 자동매매 프로그램입니다.

이 프로젝트는 전략 실험용이므로 기본 실행은 주문 비활성 상태입니다. `runtime.trading-enabled=true`를 명시적으로 설정했을 때만 매매 판단과 주문 실행 흐름이 시작됩니다.

## 1. 실행 환경

- Java 17 이상
- Spring Boot 3.2.3
- Gradle Wrapper
- MyBatis XML Mapper
- SQLite
- Swagger UI
- 로컬 PC 단일 JVM 실행

현재 저장소의 빌드 기준은 `build.gradle`입니다. Maven 프로젝트가 아니므로 실행과 빌드는 `gradlew.bat`를 사용합니다.

## 2. 빠른 시작

### 2.1 로컬 설정 파일 만들기

민감정보는 `src/main/resources/application-local.yml`에 작성합니다. 이 파일은 `.gitignore`에 등록되어 Git에 올라가지 않습니다. 별도 예제 파일은 만들지 않습니다.

최소 모의투자 설정:

```yaml
runtime:
  trading-enabled: true

kis:
  account-mode: PAPER
  app-key: 여기에_모의투자_App_Key
  app-secret: 여기에_모의투자_App_Secret
  account-number: 계좌번호_앞_8자리
  account-product-code: 계좌상품코드_2자리
```

국내 계좌를 사용할 때:

```yaml
investment:
  market:
    type: DOMESTIC
```

해외 계좌를 사용할 때:

```yaml
investment:
  market:
    type: OVERSEAS
```

`application.yml`은 위 파일을 `optional:classpath:application-local.yml`로 불러옵니다. 따라서 현재 구조에서는 `spring.profiles.active=local`을 별도로 지정하지 않아도 됩니다. 로컬 파일이 없으면 기본값으로 기동하지만 KIS 주문은 실행되지 않습니다.

### 2.2 실행

```powershell
.\gradlew.bat bootRun
```

빌드:

```powershell
.\gradlew.bat clean build
```

기본 포트는 `8080`입니다. 이미 사용 중이면 기존 프로세스를 종료하거나 실행 시 `--server.port=8081`처럼 포트를 바꿉니다.

### 2.3 실행 확인

브라우저에서 다음 주소를 확인합니다.

- Swagger: `http://localhost:8080/swagger-ui/index.html`
- 시스템 상태: `GET http://localhost:8080/api/system/status`
- KIS 연결 상태: `GET http://localhost:8080/api/system/kis/health`
- 계좌 조회: `GET http://localhost:8080/api/account`
- 보유 포지션: `GET http://localhost:8080/api/positions`

## 3. 설정 구조

설정은 `src/main/resources/application.yml`에 기본값을 두고, 민감하거나 PC별로 달라지는 값은 `application-local.yml`에서 덮어씁니다.

### runtime

```yaml
runtime:
  trading-enabled: false
  internal-batch-interval-minutes: 5
  kis-batch-interval-minutes: 20
```

- `trading-enabled`: `true`일 때 매매 사이클을 실행합니다. 기본값은 `false`입니다.
- `internal-batch-interval-minutes`: 상태 평가와 주문 상태 정리 등 내부 유지보수 주기입니다.
- `kis-batch-interval-minutes`: 계좌 동기화와 매매 사이클 주기입니다.

처음 기동하면 시작 계좌 동기화를 먼저 수행하고, 첫 번째 매매 스케줄은 건너뜁니다. 이후 스케줄부터 기존 주문 상태와 계좌를 확인한 뒤 매매 사이클을 실행합니다.

### investment.market

- `type`: `DOMESTIC` 또는 `OVERSEAS`
- `domestic-market-code`: 국내 시장 범위입니다. 기본값은 `ALL`입니다.
- `overseas-exchange-code`: 해외 주문 거래소 코드입니다.
- `overseas-price-exchange-code`: 해외 현재가 조회 거래소 코드입니다.
- `overseas-currency-code`: 해외 거래 통화입니다. 기본값은 `USD`입니다.

종목코드를 YAML에 직접 입력하지 않습니다. 국내·해외 KIS 종목 마스터를 DB에 적재한 뒤 후보 조건에 맞는 종목을 순환 선택합니다.

### investment.order

```yaml
investment:
  order:
    unit-type: SHARE
    unit-amount: 1.00
    unit-shares: 1
```

- `SHARE`: `unit-shares`만큼 주문합니다.
- `AMOUNT`: `unit-amount`를 현재가로 나누어 주문 가능 정수 수량을 계산합니다.
- 주문 가능 수량이 0이면 KIS 주문을 호출하지 않고 건너뜁니다.

### investment.holding

- `max-holdings`: 중복 포함 전체 보유 슬롯입니다. `50`이면 전체 수량성 보유 건수가 50에 도달할 때 신규 매수를 멈춥니다.
- `max-holdings-per-stock`: 동일 종목의 최대 보유량입니다.

### investment.candidate

- `include-etf`: 해외·국내 마스터에서 ETF/ETP를 후보에 포함할지 결정합니다.

가격, 거래량, 시가총액을 전략 판단용 설정으로 중복해서 두지 않습니다. 후보는 마스터의 거래 가능 여부, 시장·통화·종목 유형, 보유 및 미체결 주문 상태를 기준으로 필터링합니다.

### investment.strategy

- `take-profit.rate`: 수익률이 이 값 이상이면 BLACK으로 전환합니다. 기본값 `0.10`입니다.
- `stop-loss.rate`: 손실률이 이 값 이하이면 BLACK으로 전환합니다. 기본값 `-0.10`입니다.
- `white.flat-grace-trading-days`: WHITE에서 가격 보합이 이어질 때 GRAY로 전환하기까지의 거래일입니다.
- `gray.grace-trading-days`: GRAY에서 BLACK으로 전환하기까지의 유예 거래일입니다.

익절과 손절은 비율로 관리합니다. WHITE와 GRAY 모두 익절·손절을 먼저 평가하며, BLACK은 상태를 재평가하지 않고 다음 매매 사이클에서 매도를 시도합니다.

### kis

`kis.account-mode`가 `PAPER`이면 모의투자 프로파일, `REAL`이면 실전 프로파일을 사용합니다.

- `app-key`: KIS App Key
- `app-secret`: KIS App Secret
- `account-number`: 계좌번호 앞 8자리
- `account-product-code`: 계좌상품코드 2자리
- `account-mode`: `PAPER` 또는 `REAL`

App Key, App Secret, 토큰, 계좌 전체 번호는 소스·Git·로그에 저장하지 않습니다. KIS 프로파일별 URL과 TR ID는 `KisProperties`에서 계좌 모드에 맞춰 선택합니다.

실전 주문을 사용할 때는 다음 두 조건이 모두 필요합니다.

1. `runtime.trading-enabled=true`
2. `kis.account-mode=REAL`

실전 전환 전에는 반드시 `PAPER`에서 계좌 동기화, 주문 상태 조회, 취소, 체결 반영을 확인해야 합니다.

## 4. 프로그램 처리 순서

### 시작 시

1. SQLite 스키마를 생성하거나 기존 스키마를 보완합니다.
2. KIS 계좌를 조회합니다.
3. KIS를 기준으로 DB 포지션을 생성·갱신·종료합니다.
4. 시작 동기화 완료 상태를 기록합니다.
5. 첫 번째 매매 스케줄은 건너뜁니다.

### 20분 매매 배치

1. 계좌를 KIS 기준으로 동기화합니다.
2. ACCEPTED 주문의 실제 KIS 상태를 주문번호 기준으로 조회합니다.
3. 확인된 미체결 주문만 취소 정책 대상에 포함합니다.
4. BLACK 포지션의 매도를 시도합니다.
5. WHITE와 GRAY 포지션의 현재가를 조회하고 상태를 평가합니다.
6. 매도 처리 후 남은 보유 슬롯을 계산합니다.
7. DB에 적재된 다음 후보를 선택합니다.
8. 잔고·현재가·중복 주문·주문 수량을 다시 검증합니다.
9. 조건을 만족하면 주문을 생성하고 결과를 `ORDERS`에 남깁니다.

### 5분 내부 유지보수 배치

주문 상태 재확인, 계좌 보정, 가격 기반 상태 평가와 라이프사이클 이력 처리를 수행합니다. 단순 가격 틱이나 상태 유지 평가를 매번 이력으로 저장하지 않습니다.

## 5. 상태 머신

```text
BUY 체결 → WHITE

WHITE
  익절/손절 기준 도달 → BLACK
  이전 확인 가격보다 하락 → GRAY
  보합 유예 거래일 도달 → GRAY
  그 외 → WHITE 유지

GRAY
  익절/손절 기준 도달 → BLACK
  최초 평균매수가 이상으로 회복 → WHITE
  GRAY 유예 거래일 초과 → BLACK
  그 외 → GRAY 유지

BLACK
  다음 매매 사이클에서 매도 시도
  체결 완료 → CLOSED
  실패·장외·미체결 → BLACK 유지 후 다음 사이클 재시도
```

현재가, 평균매수가, 기준가, 최고가, 최저가, 보유수량, 수익률은 포지션 현재값으로 갱신합니다. 라이프사이클 이력에는 BUY 체결, 상태 변경, 기준가 변경, GRAY 거래일 증가, 매도 주문·체결, 종료, 주요 오류 같은 사건만 기록합니다.

주문 상태는 접수와 체결을 구분합니다.

```text
ACCEPTED → PARTIALLY_FILLED → FILLED
ACCEPTED → CANCELLED
ACCEPTED → REJECTED
```

계좌에 종목이 있다는 이유만으로 모든 동일 종목 주문을 FILLED 처리하지 않습니다. 가능한 경우 KIS 주문번호와 원주문번호로 개별 주문을 확인하며, 확인되지 않은 상태는 기존 상태를 유지합니다.

## 6. 패키지와 주요 파일

```text
com.won.autoinvestor
├─ AutoInvestorApplication.java       애플리케이션 시작점
├─ common
│  ├─ config
│  │  ├─ ClockConfig.java              Asia/Seoul 기준 Clock
│  │  ├─ DatabaseFileBootstrap.java    SQLite 파일 준비
│  │  ├─ InvestmentProperties.java     투자 설정 바인딩
│  │  ├─ RuntimeProperties.java        실행·배치 설정 바인딩
│  │  ├─ TradingPolicyValidator.java   설정 검증
│  │  └─ TradingSchemaInitializer.java DB 스키마·보완 마이그레이션
│  ├─ kis
│  │  ├─ BrokerClient.java              증권사 연동 포트
│  │  ├─ KoreaInvestmentBrokerClient.java KIS HTTP 어댑터
│  │  ├─ KisAccessTokenManager.java     토큰 발급·캐시
│  │  ├─ KisProperties.java              PAPER/REAL API 설정
│  │  ├─ *StockMasterProvider.java       종목 마스터 원천 추상화
│  │  └─ *StockMasterService.java        마스터 DB 적재
│  ├─ scheduler
│  │  ├─ StartupAccountSyncRunner.java  시작 계좌 동기화
│  │  ├─ TradingCycleScheduler.java     20분·5분 배치
│  │  └─ SchedulerLockService.java      단일 JVM 실행 락
│  ├─ trade
│  │  ├─ AccountSyncStateService.java   계좌 동기화 상태
│  │  ├─ *StockCandidateService.java    DB 종목 후보 생성
│  │  ├─ OrderExecutor*.java             Dry Run/실제 주문 분리
│  │  ├─ OrderSafetyService.java         주문 직전 안전 검증
│  │  ├─ OrderSizingService.java         금액→수량 계산
│  │  ├─ TradingDayService.java          거래일 계산
│  │  ├─ TradingStatus.java              WHITE/GRAY/BLACK/CLOSED 상태
│  │  ├─ ExitReason.java                  매도 사유
│  │  └─ LifecycleEventType.java         생애주기 사건 종류
│  ├─ exception/GlobalExceptionHandler.java 공통 REST 오류 처리
│  └─ util/MapUtils.java                 MyBatis Map 결과 변환
└─ pilot
   ├─ PilotController.java               REST 진입점
   ├─ PilotService.java                  파일럿 업무 로직·거래 흐름
   └─ PilotMapper.java                   MyBatis 호출 선언
```

`pilot`는 현재 애플리케이션의 업무 진입점입니다. Controller는 호출과 반환만 담당하고, Service가 업무 순서를 조정하며, Mapper와 `src/main/resources/mapper/PilotMapper.xml`이 DB 접근을 담당합니다.

작은 공통 파일을 무리하게 한 파일로 합치면 Spring 빈 이름, KIS 어댑터 경계, DB 초기화 순서가 깨질 수 있습니다. 이번 정리에서는 역할이 실제로 겹치던 `WeekendOnlyTradingDayService`를 `TradingDayService`에 통합했고, KIS 공급자·주문 실행기·설정·스케줄러처럼 교체 또는 생명주기 경계가 있는 파일은 유지합니다.

## 7. SQLite와 주요 테이블

기본 DB 경로는 `src/main/resources/auto-investor.db`입니다. `AUTO_INVESTOR_DB_URL`로 변경할 수 있습니다.

주요 테이블:

- `POSITIONS`: 현재 보유 상태와 현재 평가값
- `ORDERS`: 주문 접수·체결·취소·거절 전체 기록
- `DOMESTIC_STOCK_MASTER`: 국내 후보 원천 데이터와 선택 상태
- `OVERSEAS_STOCK_MASTER`: 해외 후보 원천 데이터와 선택 상태
- `DOMESTIC_STOCK_DASHBOARD`, `OVERSEAS_STOCK_DASHBOARD`: 화면용 후보 현황 캐시
- `TRADE_LIFECYCLE_HISTORY`: 중요한 생애주기 사건
- `SCHEDULER_EXECUTION`: 배치 실행 이력
- `AUDIT_LOG`: 장애·동기화 추적용 감사 기록

스키마는 `TradingSchemaInitializer`가 애플리케이션 시작 시 `CREATE TABLE IF NOT EXISTS`와 필요한 컬럼 보완으로 관리합니다. 기존 데이터를 삭제하거나 테이블을 무조건 재생성하지 않습니다.

DB 파일과 토큰 캐시는 `.gitignore`에 포함되어 있습니다. 운영 전에는 DB 백업을 별도로 보관하십시오.

## 8. REST API

| Method | Path | 용도 |
|---|---|---|
| GET | `/api/system/status` | 실행·동기화 상태 |
| GET | `/api/system/kis/health` | KIS 연결 상태 |
| GET | `/api/account` | 계좌 조회 |
| GET | `/api/positions` | 포지션 조회 |
| GET | `/api/domestic/dashboard` | 국내 후보 현황 |
| GET | `/api/overseas/dashboard` | 해외 후보 현황 |
| POST | `/api/trading/sync` | 계좌 동기화 요청 |
| POST | `/api/trading/evaluate` | 상태 평가 요청 |
| POST | `/api/trading/run-cycle` | 매매 사이클 요청 |

수동 API 호출도 `runtime.trading-enabled`와 주문 실행기 설정의 영향을 받습니다. 실전 주문을 직접 호출하는 별도 주문 API는 제공하지 않습니다.

## 9. 종목 후보와 마스터

종목코드는 설정에 넣지 않습니다.

1. KIS 마스터 파일을 읽습니다.
2. 국내 또는 해외 마스터 테이블에 upsert합니다.
3. 거래 가능, 활성, 시장·통화, 종목 유형 조건으로 후보를 필터링합니다.
4. 보유 종목, 미체결 매수, 당일 매수, 재시도 대기 종목을 제외합니다.
5. `LAST_SELECTED_AT`, `LAST_BUY_ATTEMPT_AT` 기준으로 다음 후보를 선택합니다.

해외 소수점 거래 상태는 `UNKNOWN`, `YES`, `NO`로 관리합니다. 확인되지 않은 종목을 임의로 `YES`로 만들지 않으며, 실제 KIS 주문 접수가 확인된 경우에만 검증 상태를 갱신합니다.

## 10. 문제 해결

### 주문이 실행되지 않음

- `runtime.trading-enabled=true`인지 확인합니다.
- `kis.account-mode`가 계좌 종류와 일치하는지 확인합니다.
- App Key, App Secret, 계좌번호 8자리, 상품코드 2자리를 확인합니다.
- 현재가가 오래되지 않았는지 확인합니다.
- 보유 한도, 중복 보유 한도, 잔고, 후보 마스터 적재 상태를 확인합니다.
- 해외 AMOUNT 주문은 현재가가 주문금액보다 높으면 계산 수량이 0이 되어 건너뛸 수 있습니다.

### `EGW00202` 또는 라우팅 오류

라우팅 오류는 장외시간이나 소수점 불가로 임의 분류하지 않습니다. KIS 응답 코드·메시지와 계좌 모드, URL, TR ID를 확인하고 주문 상태가 확인될 때까지 기존 주문 상태를 유지합니다.

### `EGW00201` 호출 제한

토큰 발급 또는 KIS 요청이 제한된 상태입니다. 반복 재시작과 수동 API 호출을 중지하고 호출 제한이 해제된 뒤 다시 확인합니다.

### 포트 충돌

`Port 8080 was already in use`가 나오면 기존 Spring Boot 프로세스를 종료하거나 다른 포트를 지정합니다.

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

### 후보가 없음

마스터 파일 다운로드·파싱 로그, `ACTIVE`, `TRADABLE`, 거래소·통화, 해외 `FRACTIONAL_TRADABLE` 상태, 재시도 대기 시간을 확인합니다. 후보가 없을 때 임의의 샘플 종목을 넣는 fallback은 없습니다.

## 11. 운영 전 확인 목록

- `application-local.yml`이 Git 추적 대상이 아닌지 확인
- `PAPER` 계좌에서 KIS 연결과 계좌 동기화 확인
- 주문 단위와 보유 한도 확인
- 현재가와 주문 상태 조회가 정상인지 확인
- `ORDERS`의 ACCEPTED/FILLED/CANCELLED 상태가 주문번호별로 남는지 확인
- `POSITIONS`가 KIS 보유 수량과 일치하는지 확인
- BLACK 포지션이 다음 사이클에 매도 대상으로 남는지 확인
- `TRADE_LIFECYCLE_HISTORY`에 중요한 사건만 기록되는지 확인
- 백업한 SQLite가 복구 가능한지 확인
- 실전 전환 시 `kis.account-mode=REAL`을 별도로 검토

## 12. 검증 명령

```powershell
.\gradlew.bat clean build
```

현재 저장소에는 별도 테스트 소스가 없으므로 Gradle 테스트 태스크가 `NO-SOURCE`로 끝날 수 있습니다. 이 경우 성공은 컴파일·패키징 성공을 의미하며, 실제 KIS 주문 체결을 검증했다는 뜻은 아닙니다.

```sql
SELECT ORDER_STATUS, COUNT(*) FROM ORDERS GROUP BY ORDER_STATUS;
SELECT STATUS, ACTIVE, COUNT(*) FROM POSITIONS GROUP BY STATUS, ACTIVE;
SELECT EVENT_TYPE, COUNT(*) FROM TRADE_LIFECYCLE_HISTORY GROUP BY EVENT_TYPE;
```

## 13. 제한 사항

- 현재 거래일 계산은 주말만 제외합니다. 한국 증시 공식 휴장일 데이터는 별도 공급원이 연결될 때 추가해야 합니다.
- SQLite는 로컬 단일 활성 인스턴스 운영을 전제로 합니다. 여러 PC나 서버가 하나의 SQLite 파일을 공유하는 구조는 지원하지 않습니다.
- KIS API의 계좌 권한, 장 운영시간, 거래소별 주문 가능 여부는 계좌와 상품에 따라 달라질 수 있습니다.
- 실제 주문 결과의 최종 판단은 KIS 주문·체결 조회와 계좌 동기화 결과를 함께 확인해야 합니다.

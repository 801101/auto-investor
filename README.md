# auto-investor

한국투자증권(KIS) 계좌를 기준으로 보유 종목을 동기화하고, 국내 또는 해외 주식의 매수·매도 생애주기를 관리하는 로컬 단일 프로세스 자동매매 프로그램입니다.

이 프로젝트는 전략 실험용이므로 기본 실행은 주문 비활성 상태입니다. `runtime.trading-enabled=true`를 명시적으로 설정했을 때만 매매 판단과 주문 실행 흐름이 시작됩니다.

## 프로젝트 설계 원칙

### 한국어

이 프로젝트는 **KIS 계좌를 단일 기준(Source of Truth)** 으로 사용하는 자동매매 프로그램입니다.

프로그램 내부 DB는 실제 계좌를 대체하지 않으며, 계좌 상태를 빠르게 조회하고 전략을 수행하기 위한 **로컬 상태 저장소(Local State)** 입니다.

프로젝트는 다음 원칙을 항상 유지합니다.

- KIS 계좌가 항상 최종 기준입니다.
- POSITIONS는 현재 보유 상태를 관리하는 원장입니다.
- ORDERS는 주문의 생애주기를 관리하는 원장입니다.
- TRADE_LIFECYCLE_HISTORY는 중요한 이벤트만 기록합니다.
- AUDIT_LOG는 운영 및 장애 추적을 위한 로그입니다.
- STOCK_MASTER는 종목 후보의 원본 데이터입니다.
- STOCK_DASHBOARD는 화면 표시를 위한 캐시입니다.
- 계좌 동기화를 통해 DB는 항상 KIS 상태를 따라갑니다.
- 프로그램이 비정상 종료되거나 일시적으로 오류가 발생해도 다음 동기화에서 정상 상태로 복구되는 것을 목표로 합니다.
- 모든 전략은 WHITE / GRAY / BLACK 상태머신을 기준으로 동작합니다.

### 시장 선택 책임

`investment.market.type`은 **신규 매수 시장을 선택하는 설정**입니다. 후보 생성과 BUY 주문은 이 값을 따릅니다.

매수 이후에는 포지션이 생성될 때 `POSITIONS.MARKET_TYPE`에 시장을 저장합니다. 이후 내부 갱신과 매도는 설정값이 아니라 포지션의 시장을 우선 사용합니다.

```text
신규 매수
    │
    ▼
investment.market.type
    │
    ▼
국내 또는 해외 후보·BUY

POSITION 생성
    │
    ▼
POSITIONS.MARKET_TYPE
    │
    ├── 내부 갱신: 해당 시장의 현재가 조회
    └── BLACK 매도: 해당 시장의 매도 API 호출
                         │
                         └── 값이 없거나 잘못되면 investment.market.type 사용
```

따라서 `market.type=DOMESTIC`으로 실행 중이어도 `POSITIONS.MARKET_TYPE=OVERSEAS`인 기존 포지션은 해외 현재가 조회와 해외 매도 API를 사용합니다. 시장값이 없는 기존 레거시 포지션은 현재 설정값을 fallback으로 사용합니다.

## 1. 실행 환경

- Java 17 이상
- Spring Boot 3.2.3
- Gradle Wrapper
- MyBatis XML Mapper
- SQLite
- Swagger UI
- 로컬 PC 단일 JVM 실행

현재 저장소의 빌드 기준은 `build.gradle`입니다. Maven 프로젝트가 아니므로 실행과 빌드는 `gradlew.bat`를 사용합니다.

## 아키텍처 다이어그램

### 전체 아키텍처

```text
Client
    │
    ▼
REST API
    │
    ▼
PilotService
    │
    ├── KIS API
    ├── SQLite
    └── Scheduler
```

### 프로그램 실행 순서

```text
Program Start
    │
    ▼
Schema Initialize
    │
    ▼
Startup Account Sync
    │
    ▼
Skip First Trading Scheduler
    │
    ▼
20 Minute Trading Cycle
    │
    ▼
5 Minute Maintenance Cycle
```

### Trading Cycle

```text
Sync Account
    │
    ▼
Check Accepted Orders
    │
    ▼
Cancel Confirmed Open Orders
    │
    ▼
Sell BLACK
    │
    ▼
Evaluate WHITE / GRAY
    │
    ▼
Candidate Selection
    │
    ▼
BUY
```

### 상태머신(State Machine)

```text
BUY 주문 접수
    │
    ▼
Account Sync에서 보유 확인
    │
    ▼
WHITE
 ├─ 하락 → GRAY
 ├─ 익절 → BLACK
 ├─ 손절 → BLACK
 └─ 보합기간 초과 → GRAY

GRAY
 ├─ 회복 → WHITE
 ├─ 익절 → BLACK
 ├─ 손절 → BLACK
 └─ 기간초과 → BLACK

BLACK
    │
    ▼
SELL
    │
    ▼
Account Sync에서 보유 소멸 확인
    │
    ▼
CLOSED
```

### 계좌 동기화(Account Sync)

```text
KIS Account
      │
      ▼
Compare POSITIONS
      │
 ┌────┼────┐
 │    │    │
 ▼    ▼    ▼
CREATE UPDATE CLOSE
```

### 데이터 흐름

```text
KIS
 │
 ▼
POSITIONS
 │
 ├──── ORDERS
 │         │
 │         ▼
 │   TRADE_LIFECYCLE_HISTORY
 │
 ├──── STOCK_DASHBOARD
 │
 └──── AUDIT_LOG
```

### 테이블 관계

```text
POSITIONS (현재 상태)

↓

ORDERS (주문)

↓

TRADE_LIFECYCLE_HISTORY (중요 이벤트)

AUDIT_LOG는 운영 로그
STOCK_MASTER는 후보 원본
STOCK_DASHBOARD는 화면 캐시
```

### Scheduler 흐름

```text
Startup Sync

↓

20분 Trading Scheduler

↓

5분 Maintenance Scheduler

↓

반복
```

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

`application.yml`은 위 파일을 `optional:classpath:application-local.yml`로 불러옵니다. DB 경로는 별도로 시작한 Spring 프로필을 기준으로 선택합니다. 로컬 DB를 사용하려면 반드시 `spring.profiles.active=local`을 지정합니다. 로컬 파일이 없으면 기본값으로 기동하지만 KIS 주문은 실행되지 않습니다.

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

- `type`: 신규 후보 생성과 BUY 주문에 사용할 시장입니다. `DOMESTIC` 또는 `OVERSEAS`를 사용합니다.
- `domestic-market-code`: 국내 시장 범위입니다. 기본값은 `ALL`입니다.
- `overseas-exchange-code`: 해외 주문 거래소 코드입니다.
- `overseas-price-exchange-code`: 해외 현재가 조회 거래소 코드입니다.
- `overseas-currency-code`: 해외 거래 통화입니다. 기본값은 `USD`입니다.

내부 갱신과 BLACK 매도는 `POSITIONS.MARKET_TYPE`을 우선 사용합니다. 값이 없거나 유효하지 않은 기존 포지션만 현재 `investment.market.type`으로 fallback합니다. 따라서 설정을 국내로 바꿔도 시장값이 해외로 저장된 기존 포지션은 해외 가격 조회와 해외 매도 API를 사용합니다.

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
BUY 주문 접수 → 다음 Account Sync에서 실제 보유 확인 → WHITE

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
  다음 Account Sync에서 보유 수량 감소 또는 소멸 확인 → 수량 갱신 또는 CLOSED
  실패·장외·미체결 → BLACK 유지 후 다음 사이클 재시도
```

현재가, 평균매수가, 기준가, 최고가, 최저가, 보유수량, 수익률은 포지션 현재값으로 갱신합니다. 라이프사이클 이력에는 Account Sync에 의한 생성·수량 보정·종료, 상태 변경, 기준가 변경, GRAY 거래일 증가, 매도 주문, 종료, 주요 오류 같은 사건만 기록합니다.

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

DB 파일명은 항상 `auto-investor.db`입니다. DB 위치는 Spring 시작 프로필로 결정합니다.

```yaml
spring:
  profiles:
    active: local
```

- 로컬 실행: `spring.profiles.active=local`
- JAR 실행: `local` 이외의 프로필 또는 프로필 미지정
- 로컬 프로필은 `src/main/resources/auto-investor.db`를 사용합니다.
- JAR 실행 시 같은 폴더에 DB가 있으면 그대로 사용하고, 없으면 자동 생성합니다.
- `AUTO_INVESTOR_DB_URL`을 지정하면 위 자동 경로보다 우선합니다.
- DB 파일은 JAR 안에 넣지 않고 외부 파일로 유지합니다.

IntelliJ에서는 Run/Debug Configuration의 **Active profiles**에 `local`을 입력합니다. 명령줄에서는 다음처럼 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
java -jar auto-investor.jar --spring.profiles.active=server
```

스키마는 `TradingSchemaInitializer`가 애플리케이션 시작 시 `CREATE TABLE IF NOT EXISTS`와 필요한 컬럼 보완으로 관리합니다. 기존 데이터를 삭제하거나 테이블을 무조건 재생성하지 않습니다.

`POSITIONS`는 종목별 집계 행이 아니라 **Account Sync에서 실제 보유가 확인된 한 번의 Lifecycle을 관리하는 원장**입니다. 정상 매수는 BUY 주문과 연결되고, DB 복구 시에는 KIS 계좌 스냅샷으로 새 WHITE 포지션을 시작합니다. 동일 `STOCK_CODE`에 여러 ACTIVE 행이 존재할 수 있으며, WHITE/GRAY/BLACK 평가와 SELL은 각 `POSITIONS.ID` 기준으로 수행합니다. DB가 삭제되어 복구되는 경우에는 과거 매수별 Lifecycle을 추정하지 않고 종목당 WHITE 포지션 1개를 새로 만듭니다.

`ORDERS.POSITION_ID`는 연결된 POSITION을 가리키는 Snapshot 연결값입니다. 실제 POSITION 생성은 다음 Account Sync에서 KIS 보유가 확인될 때 수행하며, 기존 BUY 주문이 있으면 해당 주문을 POSITION에 연결한 뒤 `FILLED`로 확정합니다. 기존 BUY 주문이 없으면 `ACCOUNT_SYNC_FALLBACK` BUY Snapshot을 만든 뒤 Account Sync 확정 `FILLED`로 기록합니다. SELL은 주문이 접수된 뒤 매 배치에서 KIS 주문 존재 여부를 확인하고, 미체결 주문이 남아 있으면 중복 주문하지 않고 재시도 대기합니다. KIS 주문이 더 이상 남아 있지 않고 계좌에서도 보유가 사라지면 POSITION을 CLOSED 처리하고 SELL 주문을 Account Sync 확정 `FILLED`로 기록합니다. 여기서 `FILLED`는 단순 체결 응답이 아니라 BUY/SELL 주문과 POSITION Lifecycle 연결이 최종 확정됐다는 뜻입니다.

Account Sync는 기존 POSITION의 평균매수가를 수정하지 않습니다. KIS 보유수량과 DB 수량이 다르면 보유수량과 ACTIVE/CLOSED 상태만 조정합니다. DB에 POSITION이 전혀 없는 종목을 KIS Snapshot으로 처음 등록할 때만 KIS 평균매수가를 새 POSITION의 생성값으로 사용합니다. 이 경우 과거 매수 Lifecycle을 추정하지 않고 종목당 POSITION 1개로 시작합니다.

`ACCOUNT_BALANCE`는 KIS 계좌 동기화가 성공할 때 현재 현금과 평가금액을 저장하는 단일 현재값 테이블입니다. 대시보드는 이 테이블을 조회하며, 페이지 새로고침마다 KIS 잔액 API를 직접 호출하지 않습니다.

DB 파일과 토큰 캐시는 `.gitignore`에 포함되어 있습니다. 운영 전에는 DB 백업을 별도로 보관하십시오.

### 테이블 DDL

실제 생성 로직은 `src/main/java/com/won/autoinvestor/common/config/TradingSchemaInitializer.java`에 있습니다. 아래는 확인용 DDL입니다.

```sql
CREATE TABLE IF NOT EXISTS POSITIONS (
    /* 현재 보유 포지션의 식별자 */
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    /* 국내·해외 종목 코드 */
    STOCK_CODE TEXT NOT NULL,
    /* 종목명 */
    STOCK_NAME TEXT,
    /* WHITE, GRAY, BLACK, CLOSED */
    STATUS TEXT NOT NULL,
    /* 최초 매수가 */
    PURCHASE_PRICE TEXT NOT NULL,
    /* 최초 매수 수량 */
    PURCHASE_QUANTITY TEXT NOT NULL,
    /* 현재 투자 원금 */
    INVESTED_AMOUNT TEXT NOT NULL,
    /* 최종 동기화 현재가 */
    CURRENT_PRICE TEXT,
    /* 현재 평가금액 */
    CURRENT_VALUATION_AMOUNT TEXT,
    /* 현재 수익률 */
    PROFIT_RATE TEXT,
    /* 직전 상태 평가 가격 */
    LAST_EVALUATED_PRICE TEXT,
    /* 상태 평가 기준 가격 */
    STATUS_REFERENCE_PRICE TEXT,
    /* GRAY 진입 거래일 */
    GRAY_ENTERED_DATE TEXT,
    /* GRAY 경과 거래일 수 */
    GRAY_TRADING_DAYS INTEGER NOT NULL DEFAULT 0,
    /* 포지션 생성 매수 주문번호 */
    BROKER_ORDER_ID TEXT,
    /* 보유 중 Y, 종료 N */
    ACTIVE TEXT NOT NULL DEFAULT 'Y',
    /* 생성 시각 */
    CREATED_AT TEXT NOT NULL,
    /* 최종 수정 시각 */
    UPDATED_AT TEXT NOT NULL,
    /* KIS 동기화 기준 평균 매수가 */
    AVERAGE_BUY_PRICE TEXT,
    /* 상태 판단용 기준가 */
    REFERENCE_PRICE TEXT,
    /* 라이프사이클 최고가 */
    HIGHEST_PRICE TEXT,
    /* 라이프사이클 최저가 */
    LOWEST_PRICE TEXT,
    /* 현재 보유 수량 */
    HOLDING_QUANTITY TEXT,
    /* 현재 수익률 원장값 */
    RETURN_RATE TEXT,
    /* 최종 상태 평가 시각 */
    LAST_EVALUATED_AT TEXT,
    /* 보합 시작 거래일 */
    FLAT_STARTED_DATE TEXT,
    /* WHITE 상태에서 가격 미변동을 추적 중인지: Y/N */
    FLAT_ACTIVE TEXT NOT NULL DEFAULT 'N',
    /* KIS 정상 보유값 또는 계좌 동기화 대체값의 출처 */
    ACCOUNT_SYNC_SOURCE TEXT,
    /* POSITION, BUY ORDER, SELL ORDER, 이력을 묶는 생애주기 키 */
    LIFECYCLE_KEY TEXT,
    /* 이 포지션의 국내·해외 시장 구분. 내부 갱신과 매도 API 선택에 사용 */
    MARKET_TYPE TEXT
);

CREATE TABLE IF NOT EXISTS ORDERS (
    /* 로컬 주문 식별자 */
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    /* KIS 주문번호 */
    BROKER_ORDER_ID TEXT,
    /* 이 주문과 연결된 POSITIONS.ID. BUY 접수 전에는 NULL일 수 있음 */
    POSITION_ID INTEGER,
    /* 종목 코드 */
    STOCK_CODE TEXT NOT NULL,
    /* BUY 또는 SELL */
    ORDER_TYPE TEXT NOT NULL,
    /* 주문 수량 */
    ORDER_QUANTITY TEXT NOT NULL,
    /* 주문 가격 */
    ORDER_PRICE TEXT,
    /* 주문 목표 금액 */
    ORDER_AMOUNT TEXT NOT NULL,
    /* REQUESTED, ACCEPTED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED 등 */
    ORDER_STATUS TEXT NOT NULL,
    /* 재시도 횟수 */
    RETRY_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 오류 원문 또는 내부 오류 설명 */
    ERROR_MESSAGE TEXT,
    /* 주문 요청 시각 */
    REQUESTED_AT TEXT NOT NULL,
    /* KIS 접수 확인 시각 */
    ACCEPTED_AT TEXT,
    /* 실제 체결 완료 시각 */
    FILLED_AT TEXT,
    /* 최종 수정 시각 */
    UPDATED_AT TEXT NOT NULL,
    /* 주문 중복 전송 방지 키 */
    IDEMPOTENCY_KEY TEXT,
    /* 주문 판단 사이클 식별자 */
    DECISION_CYCLE_ID TEXT,
    /* 실행 인스턴스 식별자 */
    INSTANCE_ID TEXT,
    /* 로그용 마스킹 계좌 */
    MASKED_ACCOUNT TEXT,
    /* 주문하지 않은 경우의 사유 */
    SKIP_REASON TEXT,
    /* TAKE_PROFIT, STOP_LOSS, GRAY_TIMEOUT 등 매도 사유 */
    EXIT_REASON TEXT,
    /* Dry Run이면 Y */
    DRY_RUN TEXT NOT NULL DEFAULT 'N',
    /* 주문 직전 확인 현재가 */
    CURRENT_PRICE TEXT,
    /* 현재가 조회 시각 */
    CURRENT_PRICE_AT TEXT,
    /* ===========================================
       BUY 후보 판단 Snapshot
       BUY 주문 생성 당시 값을 보존하며 이후 후보 재계산으로 변경하지 않음
    =========================================== */
    /* 매수 당시 후보 순위 */
    CANDIDATE_RANK INTEGER,
    /* 거래대금 평가 점수. 산출하지 않은 경우 NULL */
    TRADING_VALUE_SCORE TEXT,
    /* 거래량 평가 점수. 산출하지 않은 경우 NULL */
    VOLUME_SCORE TEXT,
    /* 변동성 평가 점수. 산출하지 않은 경우 NULL */
    VOLATILITY_SCORE TEXT,
    /* 매수 당시 최종 후보 점수 */
    TOTAL_SCORE TEXT,
    /* ===========================================
       SELL 포지션 상태 Snapshot
       SELL 주문 생성 당시 BLACK POSITION 값을 보존
    =========================================== */
    /* 매도 주문 생성 당시 포지션 상태 */
    POSITION_STATUS TEXT,
    /* 매도 주문 생성 당시 평균 매수가 */
    AVERAGE_BUY_PRICE TEXT,
    /* 매도 주문 생성 당시 최고가 */
    HIGHEST_PRICE TEXT,
    /* 매도 주문 생성 당시 최저가 */
    LOWEST_PRICE TEXT,
    /* 매도 주문 생성 당시 수익률 */
    RETURN_RATE TEXT,
    /* 매도 주문 생성 당시 GRAY 경과 거래일 */
    GRAY_TRADING_DAYS INTEGER,
    /* KIS 정정·취소에 사용하는 원주문번호 */
    BROKER_ORDER_ORGNO TEXT,
    /* KIS 주문 상태 원문 */
    BROKER_STATUS TEXT,
    /* 실제 체결 수량 */
    FILLED_QUANTITY TEXT,
    /* 실제 체결 평균 가격. 부분 체결 누적과 재시작 복구에 사용 */
    FILLED_PRICE TEXT,
    /* 미체결 잔여 수량 */
    REMAINING_QUANTITY TEXT,
    /* 마지막 KIS 상태 조회 시각 */
    LAST_BROKER_STATUS_CHECKED_AT TEXT,
    /* POSITION과 BUY/SELL 주문을 묶는 생애주기 키 */
    LIFECYCLE_KEY TEXT,
    /* 주문 생성 출처: BROKER_ORDER, DRY_RUN 또는 ACCOUNT_SYNC_FALLBACK */
    ORDER_SOURCE TEXT
);

CREATE TABLE IF NOT EXISTS STOCK_MASTER (
    /* 종목 마스터 행 식별자 */
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    /* DOMESTIC 또는 OVERSEAS */
    TYPE TEXT NOT NULL DEFAULT 'DOMESTIC',
    /* 종목 코드 */
    SYMBOL TEXT NOT NULL,
    /* 종목명 */
    STOCK_NAME TEXT,
    /* 국내 시장 코드 또는 해외 거래소 코드 */
    MARKET_CODE TEXT NOT NULL DEFAULT '',
    /* 국내 표준 종목 코드 */
    STANDARD_CODE TEXT,
    /* 국내 원천 종목 분류 코드 */
    SECURITY_GROUP_CODE TEXT,
    /* 해외 거래소 코드 */
    EXCHANGE_CODE TEXT NOT NULL DEFAULT '',
    /* 해외 시세 거래소 코드 */
    PRICE_EXCHANGE_CODE TEXT NOT NULL DEFAULT '',
    /* 주문 통화 */
    CURRENCY_CODE TEXT NOT NULL DEFAULT '',
    /* 해외 원천 종목 유형 */
    SECURITY_TYPE TEXT,
    /* ETF/ETP 여부 Y/N */
    ETP TEXT NOT NULL DEFAULT 'N',
    /* SPAC 여부 Y/N */
    SPAC TEXT NOT NULL DEFAULT 'N',
    /* 주문 가능 여부 Y/N */
    TRADABLE TEXT NOT NULL DEFAULT 'N',
    /* 후보 원천 활성 여부 Y/N */
    ACTIVE TEXT NOT NULL DEFAULT 'Y',
    /* UNKNOWN, YES, NO */
    FRACTIONAL_TRADABLE TEXT NOT NULL DEFAULT 'UNKNOWN',
    /* 최근 종목 가격 */
    LAST_PRICE TEXT,
    /* 시가총액 */
    MARKET_CAP TEXT,
    /* 거래량 */
    TRADING_VOLUME TEXT,
    /* 마지막 후보 선택 시각 */
    LAST_SELECTED_AT TEXT,
    /* 마지막 매수 시도 시각 */
    LAST_BUY_ATTEMPT_AT TEXT,
    /* 마지막 매수 성공 시각 */
    LAST_BUY_SUCCESS_AT TEXT,
    /* 연속 주문 실패 횟수 */
    CONSECUTIVE_FAILURES INTEGER NOT NULL DEFAULT 0,
    /* 재시도 허용 시각 */
    RETRY_AFTER TEXT,
    /* 후보 제외 사유 */
    EXCLUDED_REASON TEXT,
    /* 생성 시각 */
    CREATED_AT TEXT NOT NULL,
    /* 최종 수정 시각 */
    UPDATED_AT TEXT NOT NULL,
    /* KIS 종목 마스터 동기화 시각 */
    LAST_SYNCED_AT TEXT,
    /* 소수점 주문 검증 완료 시각 */
    FRACTIONAL_VERIFIED_AT TEXT,
    /* 소수점 주문 검증 출처 */
    FRACTIONAL_VERIFICATION_SOURCE TEXT,
    /* 마지막 검증 주문 시도 시각 */
    LAST_VERIFICATION_ATTEMPT_AT TEXT,
    /* 검증 주문 시도 횟수 */
    VERIFICATION_ATTEMPT_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 마지막 KIS 응답 코드 */
    LAST_KIS_RESPONSE_CODE TEXT,
    /* 마지막 KIS 응답 메시지 */
    LAST_KIS_RESPONSE_MESSAGE TEXT
);

CREATE TABLE IF NOT EXISTS STOCK_DASHBOARD (
    /* DOMESTIC 또는 OVERSEAS */
    TYPE TEXT NOT NULL,
    /* 종목 코드 */
    SYMBOL TEXT NOT NULL,
    /* 국내 시장 또는 해외 거래소 코드 */
    MARKET_CODE TEXT NOT NULL DEFAULT '',
    /* 기존 해외 화면 조회 호환용 거래소 코드 */
    EXCHANGE_CODE TEXT NOT NULL DEFAULT '',
    /* 후보 순위 */
    RANK_NO INTEGER NOT NULL,
    /* 종목명 */
    STOCK_NAME TEXT,
    /* 후보 정렬 점수 */
    CANDIDATE_SCORE TEXT NOT NULL,
    /* 매수 후보 구간 */
    PURCHASE_ZONE TEXT NOT NULL,
    /* 화면용 후보 상태 */
    DASHBOARD_STATUS TEXT NOT NULL,
    /* 화면 표시 가격 */
    LAST_PRICE TEXT,
    /* 체결 완료 매수 건수 */
    COMPLETED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 진행 중 매수 건수 */
    PENDING_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 예약 매수 건수 */
    RESERVED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 현재 중복 보유 건수 */
    CURRENT_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 설정된 최대 중복 보유 건수 */
    MAXIMUM_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 추가 매수 가능한 중복 건수 */
    REMAINING_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
    /* 현재 투자 원금 합계 */
    TOTAL_INVESTED_AMOUNT TEXT NOT NULL DEFAULT '0',
    /* 진행 중 매수 주문금액 합계 */
    PENDING_INVESTMENT_AMOUNT TEXT NOT NULL DEFAULT '0',
    /* 현재 매수 가능 여부 Y/N */
    PURCHASABLE TEXT NOT NULL DEFAULT 'N',
    /* 후보 제외 사유 */
    EXCLUSION_REASON TEXT,
    /* 마지막 후보 선택 시각 */
    LAST_SELECTED_AT TEXT,
    /* 마지막 매수 성공 시각 */
    LAST_BUY_SUCCESS_AT TEXT,
    /* 재시도 허용 시각 */
    RETRY_AFTER TEXT,
    /* 후보 평가 시각 */
    EVALUATED_AT TEXT NOT NULL,
    /* 캐시 최종 수정 시각 */
    UPDATED_AT TEXT NOT NULL,
    /* 시장·종목·코드 복합 기본키 */
    PRIMARY KEY(TYPE, SYMBOL, MARKET_CODE, EXCHANGE_CODE)
);

CREATE TABLE IF NOT EXISTS TRADE_LIFECYCLE_HISTORY (
    /* 중요 사건 이력 식별자 */
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    /* 연결된 포지션 ID */
    LIFECYCLE_ID INTEGER NOT NULL,
    /* ACCOUNT_SYNC_CREATED, 상태 전환, SELL_ORDER_CREATED, ACCOUNT_SYNC_CLOSED, CLOSED 등 */
    EVENT_TYPE TEXT NOT NULL,
    /* 사건 직전 상태 */
    PREVIOUS_STATE TEXT,
    /* 사건 처리 후 상태 */
    NEW_STATE TEXT,
    /* 사건 당시 현재가 */
    CURRENT_PRICE TEXT,
    /* 사건 당시 평균 매수가 */
    AVERAGE_BUY_PRICE TEXT,
    /* 사건 당시 상태 기준가 */
    REFERENCE_PRICE TEXT,
    /* 사건 당시 라이프사이클 최고가 */
    HIGHEST_PRICE TEXT,
    /* 사건 당시 라이프사이클 최저가 */
    LOWEST_PRICE TEXT,
    /* 사건 당시 보유 수량 */
    HOLDING_QUANTITY TEXT,
    /* 사건 당시 수익률 */
    RETURN_RATE TEXT,
    /* 사건 당시 GRAY 경과 거래일 */
    GRAY_TRADING_DAYS INTEGER,
    /* 사건 발생 이유 */
    REASON TEXT,
    /* 관련 주문 ID */
    ORDER_ID INTEGER,
    /* 관련 실행 ID */
    EXECUTION_ID TEXT,
    /* 사건 중복 기록 방지 키 */
    IDEMPOTENCY_KEY TEXT NOT NULL,
    /* 사건 발생 시각 */
    OCCURRED_AT TEXT NOT NULL,
    /* POSITION, 주문과 동일 생애주기를 조회하기 위한 키 */
    LIFECYCLE_KEY TEXT
);

CREATE TABLE IF NOT EXISTS SCHEDULER_EXECUTION (
    /* 스케줄 실행 기록 식별자 */
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    /* 실행한 스케줄 종류 */
    SCHEDULER_TYPE TEXT NOT NULL,
    /* 실행 시작 시각 */
    STARTED_AT TEXT NOT NULL,
    /* 실행 종료 시각 */
    FINISHED_AT TEXT,
    /* STARTED, COMPLETED, FAILED, SKIPPED */
    EXECUTION_STATUS TEXT NOT NULL,
    /* 처리 결과 또는 생략 사유 */
    MESSAGE TEXT
);

CREATE TABLE IF NOT EXISTS AUDIT_LOG (
    /* 운영 로그 식별자 */
    ID INTEGER PRIMARY KEY AUTOINCREMENT,
    /* 동기화, 주문 차단, API 오류 등의 사건 종류 */
    EVENT_TYPE TEXT NOT NULL,
    /* 관련 종목 코드. 계좌 전체 사건이면 NULL */
    STOCK_CODE TEXT,
    /* 장애 추적 상세 내용. 민감정보는 저장하지 않음 */
    DETAILS TEXT NOT NULL,
    /* 로그 기록 시각 */
    CREATED_AT TEXT NOT NULL
);
```

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
| POST | `/api/trading/maintenance` | 내부 유지보수 배치 요청 |
| POST | `/api/trading/run-cycle` | 매매 사이클 요청 |

수동 API 호출도 `runtime.trading-enabled`와 주문 실행기 설정의 영향을 받습니다. 실전 주문을 직접 호출하는 별도 주문 API는 제공하지 않습니다.

## 9. 종목 후보와 마스터

종목코드는 설정에 넣지 않습니다.

1. KIS 마스터 파일을 읽습니다.
2. `STOCK_MASTER`에 시장 타입을 포함해 upsert합니다.
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

점유 프로세스 확인:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
    Select-Object LocalAddress, LocalPort, OwningProcess
Get-Process -Id <PID>
```

확인한 PID 종료:

```powershell
Stop-Process -Id <PID> -Force
```

8080을 점유한 프로세스를 한 번에 확인 후 종료하려면:

```powershell
$port = 8080
$connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($connection) {
    Stop-Process -Id $connection.OwningProcess -Force
}
```

다른 포트로 실행:

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

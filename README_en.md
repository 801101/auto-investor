# auto-investor

An automated trading application for a local, single-process environment. It synchronizes domestic or overseas holdings with the Korea Investment & Securities (KIS) account and manages the trading lifecycle through the WHITE / GRAY / BLACK state machine.

Trading is disabled by default. The trading flow starts only when `runtime.trading-enabled=true` is explicitly configured.

## Architecture Principles

The KIS account is the **single source of truth**.

The local database does not replace the brokerage account. It stores local state for fast status checks, synchronization, lifecycle tracking, and strategy execution.

- KIS is always the final authority.
- `POSITIONS` is the ledger for the current holding state.
- `ORDERS` is the ledger for the order lifecycle.
- `TRADE_LIFECYCLE_HISTORY` stores significant lifecycle events only.
- `AUDIT_LOG` stores operational and troubleshooting records.
- `STOCK_MASTER` is the source data for candidate stocks.
- `STOCK_DASHBOARD` is a UI cache, not a business ledger.
- Account synchronization aligns the local database with KIS.
- The next synchronization should recover local state after a temporary failure or unexpected shutdown.
- All strategy decisions are based on WHITE / GRAY / BLACK.

### Market Responsibility

`investment.market.type` selects the market for **new entries**. Candidate generation and BUY orders follow this setting.

When a POSITION is created, its market is stored in `POSITIONS.MARKET_TYPE`. Internal price refresh and SELL orders use the position market first, rather than the current entry setting.

```text
New BUY
    |
    v
investment.market.type
    |
    v
Domestic or overseas candidate and BUY

POSITION created
    |
    v
POSITIONS.MARKET_TYPE
    |
    +-- Internal refresh: price API for that market
    +-- BLACK SELL: sell API for that market
                              |
                              +-- Missing or invalid value falls back to investment.market.type
```

Changing `market.type` to `DOMESTIC` does not force an existing position with `POSITIONS.MARKET_TYPE=OVERSEAS` through the domestic SELL API. Legacy positions without a market value use the current setting as a fallback.

## 1. Environment

- Java 17 or later
- Spring Boot 3.2.3
- Gradle Wrapper
- MyBatis XML Mapper
- SQLite
- Swagger UI
- One local JVM process

The build is defined by `build.gradle`. Use `gradlew.bat` for build and execution.

## 2. Quick Start

### 2.1 Local settings

Create `src/main/resources/application-local.yml`. This file is ignored by Git and is loaded with the `local` profile.

Example:

```yaml
kis:
  account-mode: PAPER
  app-key: ${KIS_APP_KEY:}
  app-secret: ${KIS_APP_SECRET:}
  account-number: ${KIS_ACCOUNT_NUMBER:}
  account-product-code: ${KIS_ACCOUNT_PRODUCT_CODE:01}
  paper-base-url: https://openapivts.koreainvestment.com:29443

runtime:
  trading-enabled: false
```

Keep app keys, secrets, access tokens, and full account numbers out of source control and logs.

### 2.2 Run

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Build a runnable JAR:

```powershell
.\gradlew.bat clean bootJar
java -jar build/libs/auto-investor-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

The default local server port is `8080`.

Portfolio dashboard:

```text
http://localhost:8080/dashboard.html
```

The dashboard is stored at `src/main/view/dashboard.html` and reads current positions through `/api/positions/detail`.

## 3. Configuration

The default configuration is in `src/main/resources/application.yml`. PC-specific and sensitive values belong in `application-local.yml`.

### runtime

`runtime.trading-enabled` controls whether the trading pipeline is allowed to run. Keep it `false` while checking connectivity and database synchronization.

### investment

The `investment` group contains market, order, holding, candidate, and strategy policies.

- `market.type`: market for new candidates and BUY orders; existing refresh and SELL use `POSITIONS.MARKET_TYPE`
- `order.unit-type`: `AMOUNT` or `SHARE`
- `order.unit-amount`: target amount for amount-based orders
- `order.unit-shares`: quantity for share-based orders
- `holding.max-holdings`: total holding limit
- `holding.max-holdings-per-stock`: maximum quantity for one stock
- `strategy.take-profit.rate`: profit rate that moves a position to BLACK
- `strategy.stop-loss.rate`: loss rate that moves a position to BLACK
- `strategy.white.flat-grace-trading-days`: trading days allowed without price movement in WHITE
- `strategy.gray.grace-trading-days`: trading days allowed in GRAY before BLACK

Rates use decimal notation. For example, `0.10` means 10% and `-0.10` means -10%.

### kis

The `kis` group contains the account mode, credentials, account identifiers, and domestic/overseas API endpoints. `PAPER` selects the KIS virtual trading endpoint; `REAL` selects the live endpoint.

## 4. Processing Flow

### Startup

```text
Program Start
    |
    v
Schema Initialize
    |
    v
Startup Account Sync
    |
    v
Skip First Trading Scheduler
    |
    v
20 Minute Trading Cycle
    |
    v
5 Minute Maintenance Cycle
```

### Trading cycle

```text
Sync Account
    |
    v
Check Accepted Orders
    |
    v
Cancel Confirmed Open Orders
    |
    v
Sell BLACK
    |
    v
Evaluate WHITE / GRAY
    |
    v
Candidate Selection
    |
    v
BUY
```

The first trading scheduler execution after startup is skipped. Subsequent cycles run according to the configured interval. Maintenance synchronization may run independently.

## 5. State Machine

```text
BUY ORDER ACCEPTED
    |
    v
Account Sync confirms holding
    |
    v
WHITE
 ├─ price decline                  -> GRAY
 ├─ take profit                    -> BLACK
 ├─ stop loss                      -> BLACK
 └─ flat period exceeded           -> GRAY

GRAY
 ├─ recovery                       -> WHITE
 ├─ take profit                    -> BLACK
 ├─ stop loss                      -> BLACK
 └─ grace period exceeded          -> BLACK

BLACK
    |
    v
SELL
    |
    v
Account Sync confirms holding is gone
    |
    v
CLOSED
```

`FILLED` is an Account Sync completion marker for lifecycle linkage, not a required broker callback. A BUY order becomes `FILLED` after a KIS holding is confirmed and the order is linked to the POSITION. A SELL order becomes `FILLED` after the KIS order is no longer pending and the holding has disappeared from the account. Orders remain snapshots; POSITIONS remain the holding lifecycle ledger.

BLACK is sold on the next trading cycle. A failed, rejected, or unavailable sell remains BLACK until the order can be retried. A position is CLOSED only after the holding is no longer present according to KIS synchronization.

## 6. Account Synchronization

```text
KIS Account
      |
      v
Compare POSITIONS
      |
 ┌────┼────┐
 |    |    |
 v    v    v
CREATE UPDATE CLOSE
```

KIS holdings create missing active positions, update quantities and average prices, and close local active positions that are no longer present in KIS. Local positions are not deleted during synchronization.

## 7. Package Structure

```text
com.won.autoinvestor
├── common
│   ├── config
│   ├── exception
│   ├── kis
│   ├── scheduler
│   ├── trade
│   └── util
└── pilot
    ├── PilotController.java
    ├── PilotService.java
    ├── PilotMapper.java
    └── PilotMapper.xml
```

The controller receives REST requests and returns responses. Business flow is handled by `PilotService`. Database access is handled by `PilotMapper` and its XML mapper.

## 8. Database

The local SQLite file is `auto-investor.db`.

- With the `local` profile, the database is read from `src/main/resources/auto-investor.db`.
- Without the `local` profile, the database is read from the same directory as the runnable JAR.
- The database file is created when it does not exist.

Main tables:

| Table | Purpose |
| --- | --- |
| `POSITIONS` | Current holding ledger |
| `ACCOUNT_BALANCE` | Latest synchronized cash and valuation snapshot |
| `ORDERS` | Order lifecycle and broker status |
| `TRADE_LIFECYCLE_HISTORY` | Significant state and trading events |
| `AUDIT_LOG` | Operational and troubleshooting log |
| `STOCK_MASTER` | Candidate stock master data |
| `STOCK_DASHBOARD` | Candidate display cache |
| `PRICE_HISTORY` | Stored price records used by the current implementation |

The schema is initialized by `TradingSchemaInitializer`. Detailed table definitions and column comments are maintained in that class.

`ACCOUNT_BALANCE` stores the latest cash and valuation snapshot from a successful KIS account synchronization. The dashboard reads this table instead of calling the KIS balance API on every page refresh.

## 9. REST API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/system/status` | Runtime status |
| GET | `/api/system/kis/health` | KIS connection status |
| GET | `/api/account` | Account summary |
| GET | `/api/account/balance` | Latest DB account balance snapshot |
| GET | `/api/positions` | Active position count |
| GET | `/api/positions/detail` | Dashboard position details |
| GET | `/api/orders` | Order history |
| GET | `/api/status-history` | State transition history |
| GET | `/api/domestic/dashboard` | Domestic candidate dashboard |
| GET | `/api/overseas/dashboard` | Overseas candidate dashboard |
| POST | `/api/trading/evaluate` | Request strategy evaluation |
| POST | `/api/trading/sync` | Request account synchronization |
| POST | `/api/trading/maintenance` | Request one maintenance cycle |
| POST | `/api/trading/run-cycle` | Request one trading cycle |

Swagger UI is available at `/swagger-ui/index.html` when the application is running.

## 10. Candidate Stocks

Candidates are generated from the stock master rather than from a hard-coded stock-code list. The active market, tradability, security type, exchange, currency, and fractional-trading policy are checked before a candidate is selected.

Domestic and overseas stock master providers are separated because their KIS master files and fields differ, while the candidate selection flow is shared at the trading-policy level.

## 11. Troubleshooting

### Orders do not run

Check all of the following:

1. `runtime.trading-enabled=true`
2. The selected `kis.account-mode` is correct.
3. The KIS credentials and account identifiers are configured.
4. The current market and order policy allow the request.
5. The account has sufficient balance and available holding capacity.

### KIS routing errors

`EGW00202` is treated as a gateway routing error. Do not classify it automatically as an exchange-hours error or a fractional-order error.

### Port conflict

Run the application on another port:

```powershell
.\gradlew.bat bootRun --args="--server.port=8091 --spring.profiles.active=local"
```

### No candidates

The application waits for the next cycle when no valid candidate exists. It does not insert a fallback stock code.

## 12. Operating Checklist

- Confirm the correct KIS account mode.
- Keep `runtime.trading-enabled=false` during initial checks.
- Confirm account synchronization and position counts.
- Confirm the database file path.
- Confirm market and order unit settings.
- Confirm the holding limits.
- Check open and accepted orders before enabling trading.
- Enable live trading only after the paper-account flow is verified.

## 13. Verification Commands

```powershell
.\gradlew.bat clean build --console=plain
```

Start locally without trading:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local --runtime.trading-enabled=false"
```

## 14. Limitations

- The application is designed for one active local JVM instance per account.
- SQLite is not treated as a shared multi-server database.
- KIS API response fields and order behavior depend on the selected account mode and market.
- Market holidays and fractional-trading eligibility are used only when supported by the available KIS data or confirmed order responses.

package com.won.autoinvestor.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@DependsOn("databaseFileBootstrap")
public class TradingSchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(TradingSchemaInitializer.class);

    private final DataSource dataSource;

    public TradingSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            dropUnusedTables(statement);
            createPositions(connection, statement);
            createAccountBalance(statement);
            createOrders(connection, statement);
            createStockMaster(connection, statement);
            createStockDashboard(connection, statement);
            createTradeLifecycleHistory(statement);
            createSchedulerExecution(statement);
            createAuditLog(statement);
            normalizeSchemaNames(connection, statement);
        }

        logger.info("trading schema initialized");
    }

    private void dropUnusedTables(Statement statement) throws SQLException {
        statement.executeUpdate("DROP TABLE IF EXISTS PANIC_STOP_EVENTS");
        statement.executeUpdate("DROP TABLE IF EXISTS ACTIVE_STATUS_TRACKER");
        statement.executeUpdate("DROP TABLE IF EXISTS ASSET_GRADE_DECISIONS");
        statement.executeUpdate("DROP TABLE IF EXISTS AUTOBOT_ORDER_INTENTS");
        statement.executeUpdate("DROP TABLE IF EXISTS BUDGET_ALLOCATION_SNAPSHOTS");
        statement.executeUpdate("DROP TABLE IF EXISTS PILOT_MARKET_TICKS");
        statement.executeUpdate("DROP TABLE IF EXISTS PILOT_OBSERVATIONS");
        statement.executeUpdate("DROP TABLE IF EXISTS PILOT_ORDER_INTENTS");
        statement.executeUpdate("DROP TABLE IF EXISTS PILOT_POSITIONS");
        statement.executeUpdate("DROP TABLE IF EXISTS RAW_MARKET_DATA");
        statement.executeUpdate("DROP TABLE IF EXISTS TRADING_HISTORY_MASTER");
        statement.executeUpdate("DROP TABLE IF EXISTS TRADING_STATUS_HISTORY_LOG");
        statement.executeUpdate("DROP TABLE IF EXISTS TRADING_TRAINING_DATASET");
        statement.executeUpdate("DROP TABLE IF EXISTS 시장_관측_데이터");
        statement.executeUpdate("DROP TABLE IF EXISTS 오토봇_잔고");
        statement.executeUpdate("DROP TABLE IF EXISTS 파일럿_잔고");
    }

    private void createPositions(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS POSITIONS (
                    /* ===========================================
                       POSITIONS
                       현재 보유 포지션의 기준 테이블
                       KIS 계좌 동기화와 WHITE/GRAY/BLACK 상태 평가가 갱신하는 원장
                    =========================================== */
                    /* 포지션 식별자 */
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    /* 종목 코드 */
                    STOCK_CODE TEXT NOT NULL,
                    /* 종목명 */
                    STOCK_NAME TEXT,
                    /* WHITE, GRAY, BLACK, CLOSED 상태 */
                    STATUS TEXT NOT NULL,
                    /* 최초 매수가 */
                    PURCHASE_PRICE TEXT NOT NULL,
                    /* 최초 매수 수량 */
                    PURCHASE_QUANTITY TEXT NOT NULL,
                    /* 투입 원금 */
                    INVESTED_AMOUNT TEXT NOT NULL,
                    /* 최근 현재가 */
                    CURRENT_PRICE TEXT,
                    /* 현재 평가금액 */
                    CURRENT_VALUATION_AMOUNT TEXT,
                    /* 현재 수익률 */
                    PROFIT_RATE TEXT,
                    /* 직전 상태 평가 가격 */
                    LAST_EVALUATED_PRICE TEXT,
                    /* 상태 비교 기준 가격 */
                    STATUS_REFERENCE_PRICE TEXT,
                    /* GRAY 진입 거래일 */
                    GRAY_ENTERED_DATE TEXT,
                    /* GRAY 경과 거래일 수 */
                    GRAY_TRADING_DAYS INTEGER NOT NULL DEFAULT 0,
                    /* 포지션을 생성한 브로커 주문번호 */
                    BROKER_ORDER_ID TEXT,
                    /* 현재 보유 여부: Y/N */
                    ACTIVE TEXT NOT NULL DEFAULT 'Y',
                    /* 생성 시각 */
                    CREATED_AT TEXT NOT NULL,
                    /* 최종 수정 시각 */
                    UPDATED_AT TEXT NOT NULL,
                    /* 현재 평균 매수가 */
                    AVERAGE_BUY_PRICE TEXT,
                    /* 현재 상태 기준가 */
                    REFERENCE_PRICE TEXT,
                    /* 라이프사이클 최고가 */
                    HIGHEST_PRICE TEXT,
                    /* 라이프사이클 최저가 */
                    LOWEST_PRICE TEXT,
                    /* 현재 보유 수량 */
                    HOLDING_QUANTITY TEXT,
                    /* 현재 수익률의 원장 값 */
                    RETURN_RATE TEXT,
                    /* 최종 평가 시각 */
                    LAST_EVALUATED_AT TEXT,
                    /* 보합 상태 시작 거래일 */
                    FLAT_STARTED_DATE TEXT,
                    /* WHITE 상태에서 가격 미변동을 추적 중인지: Y/N */
                    FLAT_ACTIVE TEXT NOT NULL DEFAULT 'N',
                    /* KIS 정상 보유값 또는 계좌 동기화 대체값의 출처 */
                    ACCOUNT_SYNC_SOURCE TEXT,
                    /* POSITION, BUY ORDER, SELL ORDER, 이력을 묶는 불변 생애주기 키 */
                    LIFECYCLE_KEY TEXT
                )
                """);
        addColumnIfMissing(connection, "POSITIONS", "AVERAGE_BUY_PRICE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "REFERENCE_PRICE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "HIGHEST_PRICE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "LOWEST_PRICE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "HOLDING_QUANTITY", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "RETURN_RATE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "LAST_EVALUATED_AT", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "FLAT_STARTED_DATE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "FLAT_ACTIVE", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "POSITIONS", "ACCOUNT_SYNC_SOURCE", "TEXT");
        addColumnIfMissing(connection, "POSITIONS", "LIFECYCLE_KEY", "TEXT");
        statement.executeUpdate("UPDATE POSITIONS SET LIFECYCLE_KEY = 'POSITION-' || ID WHERE LIFECYCLE_KEY IS NULL");
        statement.executeUpdate("UPDATE POSITIONS SET ACCOUNT_SYNC_SOURCE = 'LEGACY_UNKNOWN' WHERE ACCOUNT_SYNC_SOURCE IS NULL");
        statement.executeUpdate("""
                DROP INDEX IF EXISTS UX_POSITIONS_ACTIVE_STOCK_CODE
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_POSITIONS_ACTIVE_STOCK_CODE
                ON POSITIONS(STOCK_CODE, ACTIVE, ID)
                """);
    }

    private void createAccountBalance(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ACCOUNT_BALANCE (
                    /* ===========================================
                       ACCOUNT_BALANCE
                       KIS 계좌 동기화 결과 중 대시보드에 필요한 현재 잔액을 보관
                       ID=1인 단일 행만 사용하며, 계좌 동기화 성공 시 갱신
                    =========================================== */
                    /* 단일 현재 잔액 행 식별자: 항상 1 */
                    ID INTEGER PRIMARY KEY CHECK (ID = 1),
                    /* KIS 기준 현재 주문 가능 현금 또는 외화 잔액 */
                    CASH_BALANCE TEXT NOT NULL,
                    /* KIS 기준 계좌 평가금액 */
                    TOTAL_VALUATION_AMOUNT TEXT,
                    /* 잔액 통화: 국내 KRW, 해외 설정 통화 */
                    CURRENCY_CODE TEXT NOT NULL,
                    /* 잔액의 원본: KIS_ACCOUNT_SYNC */
                    SOURCE TEXT NOT NULL,
                    /* KIS 계좌 조회가 완료된 시각 */
                    UPDATED_AT TEXT NOT NULL
                )
                """);
    }

    private void createOrders(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ORDERS (
                    /* ===========================================
                       ORDERS
                       BUY/SELL 주문의 전체 처리 이력을 보관하는 원장
                       ACCEPTED부터 FILLED/CANCELLED/REJECTED까지 삭제하지 않음
                    =========================================== */
                    /* 로컬 주문 식별자 */
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                     /* KIS 주문번호 */
                     BROKER_ORDER_ID TEXT,
                     /* 이 주문이 생성하거나 종료하는 POSITIONS.ID. 매수 접수 전에는 NULL일 수 있습니다. */
                     POSITION_ID INTEGER,
                    /* 종목 코드 */
                    STOCK_CODE TEXT NOT NULL,
                    /* BUY 또는 SELL */
                    ORDER_TYPE TEXT NOT NULL,
                    /* 주문 수량 */
                    ORDER_QUANTITY TEXT NOT NULL,
                    /* 주문 가격 */
                    ORDER_PRICE TEXT,
                    /* 주문 금액 */
                    ORDER_AMOUNT TEXT NOT NULL,
                    /* 주문 처리 상태 */
                    ORDER_STATUS TEXT NOT NULL,
                    /* 재시도 횟수 */
                    RETRY_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 오류 메시지 */
                    ERROR_MESSAGE TEXT,
                    /* 주문 요청 시각 */
                    REQUESTED_AT TEXT NOT NULL,
                    /* 주문 접수 시각 */
                    ACCEPTED_AT TEXT,
                    /* 체결 완료 시각 */
                    FILLED_AT TEXT,
                    /* 최종 수정 시각 */
                    UPDATED_AT TEXT NOT NULL,
                    /* 주문 중복 방지 키 */
                    IDEMPOTENCY_KEY TEXT,
                    /* 주문 판단 주기 ID */
                    DECISION_CYCLE_ID TEXT,
                    /* 실행 인스턴스 ID */
                    INSTANCE_ID TEXT,
                    /* 마스킹된 계좌 식별값 */
                    MASKED_ACCOUNT TEXT,
                    /* 주문하지 않은 이유 */
                    SKIP_REASON TEXT,
                    /* 매도 사유 */
                    EXIT_REASON TEXT,
                    /* Dry Run 여부: Y/N */
                    DRY_RUN TEXT NOT NULL DEFAULT 'N',
                    /* 주문 당시 현재가 */
                    CURRENT_PRICE TEXT,
                    /* 현재가 조회 시각 */
                    CURRENT_PRICE_AT TEXT,
                    /* ===========================================
                       BUY 후보 판단 Snapshot
                       주문 당시 후보 평가값을 보존하며 이후 재계산으로 변경하지 않음
                    =========================================== */
                    /* 매수 당시 후보 순위 */
                    CANDIDATE_RANK INTEGER,
                    /* 거래대금 평가 점수. 현재 산출되지 않으면 NULL */
                    TRADING_VALUE_SCORE TEXT,
                    /* 거래량 평가 점수. 현재 산출되지 않으면 NULL */
                    VOLUME_SCORE TEXT,
                    /* 변동성 평가 점수. 현재 산출되지 않으면 NULL */
                    VOLATILITY_SCORE TEXT,
                    /* 매수 당시 최종 후보 점수 */
                    TOTAL_SCORE TEXT,
                    /* ===========================================
                       SELL 포지션 상태 Snapshot
                       BLACK 전환 후 매도 주문 생성 시점의 포지션 값
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
                    /* KIS 원주문번호 */
                    BROKER_ORDER_ORGNO TEXT,
                    /* KIS 상태 원문 */
                    BROKER_STATUS TEXT,
                    /* 실제 체결 수량 */
                    FILLED_QUANTITY TEXT,
                    /* 실제 체결 평균 가격. 부분 체결 누적과 재시작 복구에 사용 */
                    FILLED_PRICE TEXT,
                    /* 미체결 잔여 수량 */
                    REMAINING_QUANTITY TEXT,
                    /* 마지막 KIS 상태 확인 시각 */
                    LAST_BROKER_STATUS_CHECKED_AT TEXT,
                    /* POSITION과 BUY/SELL 주문을 묶는 생애주기 키 */
                    LIFECYCLE_KEY TEXT,
                    /* 주문 생성 출처: BROKER_ORDER, DRY_RUN 또는 ACCOUNT_SYNC_FALLBACK */
                    ORDER_SOURCE TEXT
                )
                """);
        addColumnIfMissing(connection, "ORDERS", "IDEMPOTENCY_KEY", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "DECISION_CYCLE_ID", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "INSTANCE_ID", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "MASKED_ACCOUNT", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "SKIP_REASON", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "EXIT_REASON", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "DRY_RUN", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "ORDERS", "CURRENT_PRICE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "CURRENT_PRICE_AT", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "CANDIDATE_RANK", "INTEGER");
        addColumnIfMissing(connection, "ORDERS", "TRADING_VALUE_SCORE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "VOLUME_SCORE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "VOLATILITY_SCORE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "TOTAL_SCORE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "POSITION_STATUS", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "AVERAGE_BUY_PRICE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "HIGHEST_PRICE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "LOWEST_PRICE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "RETURN_RATE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "GRAY_TRADING_DAYS", "INTEGER");
        addColumnIfMissing(connection, "ORDERS", "BROKER_ORDER_ORGNO", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "POSITION_ID", "INTEGER");
        addColumnIfMissing(connection, "ORDERS", "BROKER_STATUS", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "FILLED_QUANTITY", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "FILLED_PRICE", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "REMAINING_QUANTITY", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "LAST_BROKER_STATUS_CHECKED_AT", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "LIFECYCLE_KEY", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "ORDER_SOURCE", "TEXT");
        statement.executeUpdate("UPDATE ORDERS SET ORDER_SOURCE = CASE WHEN BROKER_ORDER_ID IS NULL THEN 'LEGACY_UNKNOWN' ELSE 'BROKER_ORDER' END WHERE ORDER_SOURCE IS NULL");
        statement.executeUpdate("UPDATE ORDERS SET LIFECYCLE_KEY = (SELECT p.LIFECYCLE_KEY FROM POSITIONS p WHERE p.ID = ORDERS.POSITION_ID) WHERE LIFECYCLE_KEY IS NULL AND POSITION_ID IS NOT NULL");
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_ORDERS_STOCK_STATUS
                ON ORDERS(STOCK_CODE, ORDER_STATUS, UPDATED_AT DESC)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_ORDERS_BROKER_ORDER
                ON ORDERS(BROKER_ORDER_ID, BROKER_ORDER_ORGNO)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_ORDERS_POSITION_STATUS
                ON ORDERS(POSITION_ID, ORDER_STATUS, UPDATED_AT DESC)
                """);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_ORDERS_IDEMPOTENCY_KEY
                ON ORDERS(IDEMPOTENCY_KEY)
                WHERE IDEMPOTENCY_KEY IS NOT NULL
                """);
    }

    private void createStockMaster(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS STOCK_MASTER (
                    /* ===========================================
                       STOCK_MASTER
                       국내·해외 종목 후보의 기준 정보와 후보 순환 상태
                       KIS 종목 마스터 동기화가 갱신하는 원천 데이터
                    =========================================== */
                    /* 종목 마스터 식별자 */
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    /* DOMESTIC 또는 OVERSEAS */
                    TYPE TEXT NOT NULL DEFAULT 'DOMESTIC',
                    /* 종목 코드 */
                    SYMBOL TEXT NOT NULL,
                    /* 종목명 */
                    STOCK_NAME TEXT,
                    /* 국내 시장 또는 해외 거래소 코드 */
                    MARKET_CODE TEXT NOT NULL DEFAULT '',
                    /* 국내 표준 종목 코드 */
                    STANDARD_CODE TEXT,
                    /* 국내 종목 분류 코드 */
                    SECURITY_GROUP_CODE TEXT,
                    /* 해외 거래소 코드 */
                    EXCHANGE_CODE TEXT NOT NULL DEFAULT '',
                    /* 해외 시세 거래소 코드 */
                    PRICE_EXCHANGE_CODE TEXT NOT NULL DEFAULT '',
                    /* 거래 통화 */
                    CURRENCY_CODE TEXT NOT NULL DEFAULT '',
                    /* 종목 유형 */
                    SECURITY_TYPE TEXT,
                    /* ETF/ETP 여부: Y/N */
                    ETP TEXT NOT NULL DEFAULT 'N',
                    /* SPAC 여부: Y/N */
                    SPAC TEXT NOT NULL DEFAULT 'N',
                    /* 거래 가능 여부: Y/N */
                    TRADABLE TEXT NOT NULL DEFAULT 'N',
                    /* 마스터 활성 여부: Y/N */
                    ACTIVE TEXT NOT NULL DEFAULT 'Y',
                    /* 해외 소수점 주문 상태: UNKNOWN/YES/NO */
                    FRACTIONAL_TRADABLE TEXT NOT NULL DEFAULT 'UNKNOWN',
                    /* 최근 가격 */
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
                    /* 연속 실패 횟수 */
                    CONSECUTIVE_FAILURES INTEGER NOT NULL DEFAULT 0,
                    /* 재시도 가능 시각 */
                    RETRY_AFTER TEXT,
                    /* 후보 제외 사유 */
                    EXCLUDED_REASON TEXT,
                    /* 생성 시각 */
                    CREATED_AT TEXT NOT NULL,
                    /* 최종 수정 시각 */
                    UPDATED_AT TEXT NOT NULL,
                    /* 마지막 마스터 동기화 시각 */
                    LAST_SYNCED_AT TEXT,
                    /* 소수점 주문 확인 시각 */
                    FRACTIONAL_VERIFIED_AT TEXT,
                    /* 소수점 주문 확인 출처 */
                    FRACTIONAL_VERIFICATION_SOURCE TEXT,
                    /* 마지막 소수점 검증 시도 시각 */
                    LAST_VERIFICATION_ATTEMPT_AT TEXT,
                    /* 소수점 검증 시도 횟수 */
                    VERIFICATION_ATTEMPT_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 마지막 KIS 응답 코드 */
                    LAST_KIS_RESPONSE_CODE TEXT,
                    /* 마지막 KIS 응답 메시지 */
                    LAST_KIS_RESPONSE_MESSAGE TEXT
                )
                """);
        addColumnIfMissing(connection, "STOCK_MASTER", "TYPE", "TEXT NOT NULL DEFAULT 'DOMESTIC'");
        addColumnIfMissing(connection, "STOCK_MASTER", "MARKET_CODE", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "STOCK_MASTER", "STANDARD_CODE", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "SECURITY_GROUP_CODE", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "EXCHANGE_CODE", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "STOCK_MASTER", "PRICE_EXCHANGE_CODE", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "STOCK_MASTER", "CURRENCY_CODE", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "STOCK_MASTER", "ETP", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "STOCK_MASTER", "SPAC", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "STOCK_MASTER", "FRACTIONAL_TRADABLE", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "STOCK_MASTER", "LAST_SELECTED_AT", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "LAST_BUY_ATTEMPT_AT", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "LAST_BUY_SUCCESS_AT", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "CONSECUTIVE_FAILURES", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "STOCK_MASTER", "RETRY_AFTER", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "EXCLUDED_REASON", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "FRACTIONAL_VERIFIED_AT", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "FRACTIONAL_VERIFICATION_SOURCE", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "LAST_VERIFICATION_ATTEMPT_AT", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "VERIFICATION_ATTEMPT_COUNT", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "STOCK_MASTER", "LAST_KIS_RESPONSE_CODE", "TEXT");
        addColumnIfMissing(connection, "STOCK_MASTER", "LAST_KIS_RESPONSE_MESSAGE", "TEXT");
        migrateLegacyStockMaster(connection, statement);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_STOCK_MASTER_TYPE_SYMBOL_MARKET_EXCHANGE
                ON STOCK_MASTER(TYPE, SYMBOL, MARKET_CODE, EXCHANGE_CODE)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_STOCK_MASTER_CANDIDATE
                ON STOCK_MASTER(TYPE, EXCHANGE_CODE, MARKET_CODE, PRICE_EXCHANGE_CODE, CURRENCY_CODE, ACTIVE, TRADABLE, FRACTIONAL_TRADABLE, LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, SYMBOL)
                """);
    }

    private void migrateLegacyStockMaster(Connection connection, Statement statement) throws SQLException {
        if (findActualTableName(connection, "DOMESTIC_STOCK_MASTER") != null) {
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO STOCK_MASTER (
                        TYPE, SYMBOL, STOCK_NAME, MARKET_CODE, STANDARD_CODE, SECURITY_GROUP_CODE,
                        EXCHANGE_CODE, PRICE_EXCHANGE_CODE, CURRENCY_CODE, SECURITY_TYPE, ETP, SPAC,
                        TRADABLE, ACTIVE, FRACTIONAL_TRADABLE, LAST_PRICE, MARKET_CAP, TRADING_VOLUME,
                        LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, LAST_BUY_SUCCESS_AT, CONSECUTIVE_FAILURES,
                        RETRY_AFTER, EXCLUDED_REASON, CREATED_AT, UPDATED_AT, LAST_SYNCED_AT
                    )
                    SELECT
                        'DOMESTIC', SYMBOL, STOCK_NAME, COALESCE(MARKET_CODE, ''), STANDARD_CODE, SECURITY_GROUP_CODE,
                        '', '', '', NULL, COALESCE(ETP, 'N'), COALESCE(SPAC, 'N'),
                        COALESCE(TRADABLE, 'N'), COALESCE(ACTIVE, 'Y'), 'UNKNOWN', LAST_PRICE, MARKET_CAP, TRADING_VOLUME,
                        LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, LAST_BUY_SUCCESS_AT, COALESCE(CONSECUTIVE_FAILURES, 0),
                        RETRY_AFTER, EXCLUDED_REASON, CREATED_AT, UPDATED_AT, LAST_SYNCED_AT
                    FROM DOMESTIC_STOCK_MASTER
                    """);
            if (countMissingLegacyRows(connection, "DOMESTIC_STOCK_MASTER", "DOMESTIC") == 0) {
                statement.executeUpdate("DROP TABLE DOMESTIC_STOCK_MASTER");
            }
        }

        if (findActualTableName(connection, "OVERSEAS_STOCK_MASTER") != null) {
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO STOCK_MASTER (
                        TYPE, SYMBOL, STOCK_NAME, MARKET_CODE, STANDARD_CODE, SECURITY_GROUP_CODE,
                        EXCHANGE_CODE, PRICE_EXCHANGE_CODE, CURRENCY_CODE, SECURITY_TYPE, ETP, SPAC,
                        TRADABLE, ACTIVE, FRACTIONAL_TRADABLE, LAST_PRICE, MARKET_CAP, TRADING_VOLUME,
                        LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, LAST_BUY_SUCCESS_AT, CONSECUTIVE_FAILURES,
                        RETRY_AFTER, EXCLUDED_REASON, CREATED_AT, UPDATED_AT, LAST_SYNCED_AT,
                        FRACTIONAL_VERIFIED_AT, FRACTIONAL_VERIFICATION_SOURCE, LAST_VERIFICATION_ATTEMPT_AT,
                        VERIFICATION_ATTEMPT_COUNT, LAST_KIS_RESPONSE_CODE, LAST_KIS_RESPONSE_MESSAGE
                    )
                    SELECT
                        'OVERSEAS', SYMBOL, STOCK_NAME, '', '', '',
                        COALESCE(EXCHANGE_CODE, ''), COALESCE(PRICE_EXCHANGE_CODE, ''), COALESCE(CURRENCY_CODE, ''), SECURITY_TYPE,
                        'N', 'N', COALESCE(TRADABLE, 'N'), COALESCE(ACTIVE, 'Y'), COALESCE(FRACTIONAL_TRADABLE, 'UNKNOWN'),
                        LAST_PRICE, MARKET_CAP, TRADING_VOLUME, LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, LAST_BUY_SUCCESS_AT,
                        COALESCE(CONSECUTIVE_FAILURES, 0), RETRY_AFTER, EXCLUDED_REASON, CREATED_AT, UPDATED_AT, LAST_SYNCED_AT,
                        FRACTIONAL_VERIFIED_AT, FRACTIONAL_VERIFICATION_SOURCE, LAST_VERIFICATION_ATTEMPT_AT,
                        COALESCE(VERIFICATION_ATTEMPT_COUNT, 0), LAST_KIS_RESPONSE_CODE, LAST_KIS_RESPONSE_MESSAGE
                    FROM OVERSEAS_STOCK_MASTER
                    """);
            if (countMissingLegacyRows(connection, "OVERSEAS_STOCK_MASTER", "OVERSEAS") == 0) {
                statement.executeUpdate("DROP TABLE OVERSEAS_STOCK_MASTER");
            }
        }
    }

    private int countMissingLegacyRows(Connection connection, String legacyTable, String type) throws SQLException {
        String match = "DOMESTIC".equals(type)
                ? "t.SYMBOL = l.SYMBOL AND t.MARKET_CODE = COALESCE(l.MARKET_CODE, '') AND t.EXCHANGE_CODE = ''"
                : "t.SYMBOL = l.SYMBOL AND t.EXCHANGE_CODE = COALESCE(l.EXCHANGE_CODE, '')";
        String sql = "SELECT COUNT(*) FROM " + legacyTable + " l WHERE NOT EXISTS (SELECT 1 FROM STOCK_MASTER t WHERE t.TYPE = '" + type + "' AND " + match + ")";
        try (Statement countStatement = connection.createStatement();
             ResultSet resultSet = countStatement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void createStockDashboard(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS STOCK_DASHBOARD (
                    /* ===========================================
                       STOCK_DASHBOARD
                       후보 종목의 화면 표시용 계산 결과
                       원장 데이터가 아니며 후보 평가 때 현재값으로 덮어씀
                    =========================================== */
                    /* 국내 또는 해외 */
                    TYPE TEXT NOT NULL,
                    /* 종목 코드 */
                    SYMBOL TEXT NOT NULL,
                    /* 시장 또는 거래소 코드 */
                    MARKET_CODE TEXT NOT NULL DEFAULT '',
                    /* 해외 호환 거래소 코드 */
                    EXCHANGE_CODE TEXT NOT NULL DEFAULT '',
                    /* 후보 순위 */
                    RANK_NO INTEGER NOT NULL,
                    /* 종목명 */
                    STOCK_NAME TEXT,
                    /* 후보 점수 */
                    CANDIDATE_SCORE TEXT NOT NULL,
                    /* 매수 구간 */
                    PURCHASE_ZONE TEXT NOT NULL,
                    /* 화면 상태 */
                    DASHBOARD_STATUS TEXT NOT NULL,
                    /* 화면 표시 가격 */
                    LAST_PRICE TEXT,
                    /* 체결 완료 매수 건수 */
                    COMPLETED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 진행 중 매수 건수 */
                    PENDING_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 예약 매수 건수 */
                    RESERVED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 현재 중복 보유 수 */
                    CURRENT_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 최대 중복 보유 수 */
                    MAXIMUM_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 남은 중복 매수 가능 수 */
                    REMAINING_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    /* 현재 투자금 */
                    TOTAL_INVESTED_AMOUNT TEXT NOT NULL DEFAULT '0',
                    /* 진행 중 주문 금액 */
                    PENDING_INVESTMENT_AMOUNT TEXT NOT NULL DEFAULT '0',
                    /* 매수 가능 여부: Y/N */
                    PURCHASABLE TEXT NOT NULL DEFAULT 'N',
                    /* 후보 제외 사유 */
                    EXCLUSION_REASON TEXT,
                    /* 마지막 후보 선택 시각 */
                    LAST_SELECTED_AT TEXT,
                    /* 마지막 매수 성공 시각 */
                    LAST_BUY_SUCCESS_AT TEXT,
                    /* 재시도 가능 시각 */
                    RETRY_AFTER TEXT,
                    /* 평가 시각 */
                    EVALUATED_AT TEXT NOT NULL,
                    /* 최종 수정 시각 */
                    UPDATED_AT TEXT NOT NULL,
                    /* 시장·종목·코드 복합키 */
                    PRIMARY KEY(TYPE, SYMBOL, MARKET_CODE, EXCHANGE_CODE)
                )
                """);
        migrateLegacyDashboard(connection, statement);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_STOCK_DASHBOARD_ZONE_SCORE
                ON STOCK_DASHBOARD(TYPE, MARKET_CODE, EXCHANGE_CODE, PURCHASE_ZONE, DASHBOARD_STATUS, CANDIDATE_SCORE DESC, RANK_NO)
                """);
    }

    private void migrateLegacyDashboard(Connection connection, Statement statement) throws SQLException {
        if (findActualTableName(connection, "DOMESTIC_STOCK_DASHBOARD") != null) {
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO STOCK_DASHBOARD (
                        TYPE, SYMBOL, MARKET_CODE, EXCHANGE_CODE, RANK_NO, STOCK_NAME, CANDIDATE_SCORE,
                        PURCHASE_ZONE, DASHBOARD_STATUS, LAST_PRICE, COMPLETED_BUY_COUNT, PENDING_BUY_COUNT,
                        RESERVED_BUY_COUNT, CURRENT_DUPLICATE_COUNT, MAXIMUM_DUPLICATE_COUNT,
                        REMAINING_DUPLICATE_COUNT, TOTAL_INVESTED_AMOUNT, PENDING_INVESTMENT_AMOUNT,
                        PURCHASABLE, EXCLUSION_REASON, LAST_SELECTED_AT, LAST_BUY_SUCCESS_AT, RETRY_AFTER,
                        EVALUATED_AT, UPDATED_AT
                    )
                    SELECT
                        'DOMESTIC', SYMBOL, COALESCE(MARKET_CODE, ''), '', RANK_NO, STOCK_NAME, CANDIDATE_SCORE,
                        PURCHASE_ZONE, DASHBOARD_STATUS, LAST_PRICE, COMPLETED_BUY_COUNT, PENDING_BUY_COUNT,
                        RESERVED_BUY_COUNT, CURRENT_DUPLICATE_COUNT, MAXIMUM_DUPLICATE_COUNT,
                        REMAINING_DUPLICATE_COUNT, TOTAL_INVESTED_AMOUNT, PENDING_INVESTMENT_AMOUNT,
                        PURCHASABLE, EXCLUSION_REASON, LAST_SELECTED_AT, LAST_BUY_SUCCESS_AT, RETRY_AFTER,
                        EVALUATED_AT, UPDATED_AT
                    FROM DOMESTIC_STOCK_DASHBOARD
                    """);
            if (countMissingLegacyDashboardRows(connection, "DOMESTIC_STOCK_DASHBOARD", "DOMESTIC") == 0) {
                statement.executeUpdate("DROP TABLE DOMESTIC_STOCK_DASHBOARD");
            }
        }
        if (findActualTableName(connection, "OVERSEAS_STOCK_DASHBOARD") != null) {
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO STOCK_DASHBOARD (
                        TYPE, SYMBOL, MARKET_CODE, EXCHANGE_CODE, RANK_NO, STOCK_NAME, CANDIDATE_SCORE,
                        PURCHASE_ZONE, DASHBOARD_STATUS, LAST_PRICE, COMPLETED_BUY_COUNT, PENDING_BUY_COUNT,
                        RESERVED_BUY_COUNT, CURRENT_DUPLICATE_COUNT, MAXIMUM_DUPLICATE_COUNT,
                        REMAINING_DUPLICATE_COUNT, TOTAL_INVESTED_AMOUNT, PENDING_INVESTMENT_AMOUNT,
                        PURCHASABLE, EXCLUSION_REASON, LAST_SELECTED_AT, LAST_BUY_SUCCESS_AT, RETRY_AFTER,
                        EVALUATED_AT, UPDATED_AT
                    )
                    SELECT
                        'OVERSEAS', SYMBOL, '', COALESCE(EXCHANGE_CODE, ''), RANK_NO, STOCK_NAME, CANDIDATE_SCORE,
                        PURCHASE_ZONE, DASHBOARD_STATUS, LAST_PRICE, COMPLETED_BUY_COUNT, PENDING_BUY_COUNT,
                        RESERVED_BUY_COUNT, CURRENT_DUPLICATE_COUNT, MAXIMUM_DUPLICATE_COUNT,
                        REMAINING_DUPLICATE_COUNT, TOTAL_INVESTED_AMOUNT, PENDING_INVESTMENT_AMOUNT,
                        PURCHASABLE, EXCLUSION_REASON, LAST_SELECTED_AT, LAST_BUY_SUCCESS_AT, RETRY_AFTER,
                        EVALUATED_AT, UPDATED_AT
                    FROM OVERSEAS_STOCK_DASHBOARD
                    """);
            if (countMissingLegacyDashboardRows(connection, "OVERSEAS_STOCK_DASHBOARD", "OVERSEAS") == 0) {
                statement.executeUpdate("DROP TABLE OVERSEAS_STOCK_DASHBOARD");
            }
        }
    }

    private int countMissingLegacyDashboardRows(Connection connection, String legacyTable, String type) throws SQLException {
        String match = "DOMESTIC".equals(type)
                ? "t.SYMBOL = l.SYMBOL AND t.MARKET_CODE = COALESCE(l.MARKET_CODE, '') AND t.EXCHANGE_CODE = ''"
                : "t.SYMBOL = l.SYMBOL AND t.MARKET_CODE = '' AND t.EXCHANGE_CODE = COALESCE(l.EXCHANGE_CODE, '')";
        String sql = "SELECT COUNT(*) FROM " + legacyTable + " l WHERE NOT EXISTS (SELECT 1 FROM STOCK_DASHBOARD t WHERE t.TYPE = '" + type + "' AND " + match + ")";
        try (Statement countStatement = connection.createStatement();
             ResultSet resultSet = countStatement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void createTradeLifecycleHistory(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS TRADE_LIFECYCLE_HISTORY (
                    /* ===========================================
                       TRADE_LIFECYCLE_HISTORY
                       포지션 생애주기의 중요 사건만 저장하는 append-only 이력
                       단순 가격 변동과 상태 유지 평가 결과는 저장하지 않음
                    =========================================== */
                    /* 이력 행의 고유 식별자. 사건을 삭제하지 않고 순서대로 보관합니다. */
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    /* 현재 사건이 연결된 POSITIONS.ID 또는 라이프사이클 식별자입니다. */
                    LIFECYCLE_ID INTEGER NOT NULL,
                    /* 매수 체결, 상태 전환, 매도, 종료 등 중요한 사건의 종류입니다. */
                    EVENT_TYPE TEXT NOT NULL,
                    /* 사건 발생 직전의 WHITE/GRAY/BLACK/CLOSED 상태입니다. */
                    PREVIOUS_STATE TEXT,
                    /* 사건 처리 후 저장된 상태입니다. */
                    NEW_STATE TEXT,
                    /* 사건 발생 시점의 현재가입니다. 단순 가격 틱은 기록하지 않습니다. */
                    CURRENT_PRICE TEXT,
                    /* 사건 발생 시점의 평균 매수가입니다. */
                    AVERAGE_BUY_PRICE TEXT,
                    /* 상태 판단에 사용된 기준가입니다. */
                    REFERENCE_PRICE TEXT,
                    /* 사건 발생 시점까지의 라이프사이클 최고가입니다. */
                    HIGHEST_PRICE TEXT,
                    /* 사건 발생 시점까지의 라이프사이클 최저가입니다. */
                    LOWEST_PRICE TEXT,
                    /* 사건 발생 시점의 실제 보유 수량입니다. */
                    HOLDING_QUANTITY TEXT,
                    /* 사건 발생 시점의 수익률입니다. */
                    RETURN_RATE TEXT,
                    /* 사건 발생 시점의 GRAY 경과 거래일 수입니다. */
                    GRAY_TRADING_DAYS INTEGER,
                    /* 상태 전환·주문·동기화가 발생한 구체적인 이유입니다. */
                    REASON TEXT,
                    /* 관련 ORDERS.ID입니다. 주문과 사건을 연결할 때 사용합니다. */
                    ORDER_ID INTEGER,
                    /* 스케줄 또는 실행 단위 식별자입니다. */
                    EXECUTION_ID TEXT,
                    /* 동일 사건의 중복 INSERT를 막는 데이터베이스 유일 키입니다. */
                    IDEMPOTENCY_KEY TEXT NOT NULL,
                    /* 사건이 실제로 발생한 시각입니다. */
                    OCCURRED_AT TEXT NOT NULL,
                    /* POSITION, 주문과 동일 생애주기를 조회하기 위한 키 */
                    LIFECYCLE_KEY TEXT
                )
                """);
        addColumnIfMissing(statement.getConnection(), "TRADE_LIFECYCLE_HISTORY", "LIFECYCLE_KEY", "TEXT");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_TRADE_LIFECYCLE_HISTORY_IDEMPOTENCY
                ON TRADE_LIFECYCLE_HISTORY(IDEMPOTENCY_KEY)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_TRADE_LIFECYCLE_HISTORY_LIFECYCLE_TIME
                ON TRADE_LIFECYCLE_HISTORY(LIFECYCLE_ID, OCCURRED_AT, ID)
                """);
        statement.executeUpdate("UPDATE TRADE_LIFECYCLE_HISTORY SET LIFECYCLE_KEY = 'POSITION-' || LIFECYCLE_ID WHERE LIFECYCLE_KEY IS NULL");
    }

    private void createSchedulerExecution(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SCHEDULER_EXECUTION (
                    /* ===========================================
                       SCHEDULER_EXECUTION
                       내부 유지보수와 KIS 매매 배치의 실행 결과
                       실행 중복·실패·건너뜀 여부를 확인하는 운영 기록
                    =========================================== */
                    /* 스케줄 실행 기록의 고유 식별자입니다. */
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    /* KIS 매매 배치 또는 내부 유지보수 배치의 종류입니다. */
                    SCHEDULER_TYPE TEXT NOT NULL,
                    /* 스케줄러가 실제 작업을 시작한 시각입니다. */
                    STARTED_AT TEXT NOT NULL,
                    /* 정상 종료 또는 실패 처리가 끝난 시각입니다. */
                    FINISHED_AT TEXT,
                    /* STARTED, COMPLETED, FAILED, SKIPPED 등의 실행 결과입니다. */
                    EXECUTION_STATUS TEXT NOT NULL,
                    /* 오류 내용, 건너뛴 이유, 처리 건수 등 사람이 확인할 메시지입니다. */
                    MESSAGE TEXT
                )
                """);
    }

    private void createAuditLog(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS AUDIT_LOG (
                    /* ===========================================
                       AUDIT_LOG
                       계좌 동기화·주문 차단·API 오류를 추적하는 운영 로그
                       거래 원장이나 상태 이력 대신 장애 원인 확인에 사용
                    =========================================== */
                    /* 감사 로그의 고유 식별자입니다. */
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    /* 계좌 동기화, 주문 차단, API 오류 등 운영 사건의 종류입니다. */
                    EVENT_TYPE TEXT NOT NULL,
                    /* 사건과 관련된 종목 코드입니다. 전체 계좌 사건이면 NULL입니다. */
                    STOCK_CODE TEXT,
                    /* 운영자가 원인을 추적할 수 있는 상세 내용입니다. 민감정보는 저장하지 않습니다. */
                    DETAILS TEXT NOT NULL,
                    /* 감사 로그가 기록된 시각입니다. */
                    CREATED_AT TEXT NOT NULL
                )
                """);
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void normalizeSchemaNames(Connection connection, Statement statement) throws SQLException {
        Map<String, List<String>> tableColumns = new LinkedHashMap<>();
        tableColumns.put("POSITIONS", List.of(
                "ID", "STOCK_CODE", "STOCK_NAME", "STATUS", "PURCHASE_PRICE", "PURCHASE_QUANTITY",
                "INVESTED_AMOUNT", "CURRENT_PRICE", "CURRENT_VALUATION_AMOUNT", "PROFIT_RATE",
                "LAST_EVALUATED_PRICE", "STATUS_REFERENCE_PRICE", "GRAY_ENTERED_DATE", "GRAY_TRADING_DAYS",
                "BROKER_ORDER_ID", "ACTIVE", "CREATED_AT", "UPDATED_AT", "AVERAGE_BUY_PRICE",
                "REFERENCE_PRICE", "HIGHEST_PRICE", "LOWEST_PRICE", "HOLDING_QUANTITY", "RETURN_RATE",
                "LAST_EVALUATED_AT", "FLAT_STARTED_DATE", "FLAT_ACTIVE", "ACCOUNT_SYNC_SOURCE", "LIFECYCLE_KEY"));
        tableColumns.put("ORDERS", List.of(
                "ID", "BROKER_ORDER_ID", "POSITION_ID", "STOCK_CODE", "ORDER_TYPE", "ORDER_QUANTITY", "ORDER_PRICE",
                "ORDER_AMOUNT", "ORDER_STATUS", "RETRY_COUNT", "ERROR_MESSAGE", "REQUESTED_AT",
                "ACCEPTED_AT", "FILLED_AT", "UPDATED_AT", "IDEMPOTENCY_KEY", "DECISION_CYCLE_ID",
                "INSTANCE_ID", "MASKED_ACCOUNT", "SKIP_REASON", "EXIT_REASON", "DRY_RUN",
                "CURRENT_PRICE", "CURRENT_PRICE_AT", "BROKER_ORDER_ORGNO", "BROKER_STATUS",
                "FILLED_QUANTITY", "FILLED_PRICE", "REMAINING_QUANTITY", "LAST_BROKER_STATUS_CHECKED_AT",
                "LIFECYCLE_KEY", "ORDER_SOURCE"));
        tableColumns.put("STOCK_MASTER", List.of(
                "ID", "TYPE", "SYMBOL", "STOCK_NAME", "MARKET_CODE", "STANDARD_CODE", "SECURITY_GROUP_CODE",
                "EXCHANGE_CODE", "PRICE_EXCHANGE_CODE", "CURRENCY_CODE", "SECURITY_TYPE", "ETP", "SPAC",
                "TRADABLE", "ACTIVE", "FRACTIONAL_TRADABLE", "LAST_PRICE", "MARKET_CAP", "TRADING_VOLUME",
                "LAST_SELECTED_AT", "LAST_BUY_ATTEMPT_AT", "LAST_BUY_SUCCESS_AT", "CONSECUTIVE_FAILURES",
                "RETRY_AFTER", "EXCLUDED_REASON", "CREATED_AT", "UPDATED_AT", "LAST_SYNCED_AT",
                "FRACTIONAL_VERIFIED_AT", "FRACTIONAL_VERIFICATION_SOURCE", "LAST_VERIFICATION_ATTEMPT_AT",
                "VERIFICATION_ATTEMPT_COUNT", "LAST_KIS_RESPONSE_CODE", "LAST_KIS_RESPONSE_MESSAGE"));
        tableColumns.put("STOCK_DASHBOARD", List.of(
                "TYPE", "SYMBOL", "MARKET_CODE", "EXCHANGE_CODE", "RANK_NO", "STOCK_NAME", "CANDIDATE_SCORE",
                "PURCHASE_ZONE", "DASHBOARD_STATUS", "LAST_PRICE", "COMPLETED_BUY_COUNT", "PENDING_BUY_COUNT",
                "RESERVED_BUY_COUNT", "CURRENT_DUPLICATE_COUNT", "MAXIMUM_DUPLICATE_COUNT",
                "REMAINING_DUPLICATE_COUNT", "TOTAL_INVESTED_AMOUNT", "PENDING_INVESTMENT_AMOUNT",
                "PURCHASABLE", "EXCLUSION_REASON", "LAST_SELECTED_AT", "LAST_BUY_SUCCESS_AT",
                "RETRY_AFTER", "EVALUATED_AT", "UPDATED_AT"));
        tableColumns.put("TRADE_LIFECYCLE_HISTORY", List.of(
                "ID", "LIFECYCLE_ID", "EVENT_TYPE", "PREVIOUS_STATE", "NEW_STATE", "CURRENT_PRICE",
                "AVERAGE_BUY_PRICE", "REFERENCE_PRICE", "HIGHEST_PRICE", "LOWEST_PRICE", "HOLDING_QUANTITY",
                "RETURN_RATE", "GRAY_TRADING_DAYS", "REASON", "ORDER_ID", "EXECUTION_ID",
                "IDEMPOTENCY_KEY", "OCCURRED_AT", "LIFECYCLE_KEY"));
        tableColumns.put("SCHEDULER_EXECUTION", List.of(
                "ID", "SCHEDULER_TYPE", "STARTED_AT", "FINISHED_AT", "EXECUTION_STATUS", "MESSAGE"));
        tableColumns.put("AUDIT_LOG", List.of(
                "ID", "EVENT_TYPE", "STOCK_CODE", "DETAILS", "CREATED_AT"));

        for (String tableName : tableColumns.keySet()) {
            normalizeTableName(connection, statement, tableName);
            normalizeColumnNames(connection, statement, tableName, tableColumns.get(tableName));
        }
    }

    private void normalizeTableName(Connection connection, Statement statement, String desiredName) throws SQLException {
        String actualName = findActualTableName(connection, desiredName);
        if (actualName == null || desiredName.equals(actualName)) {
            return;
        }
        String temporaryName = desiredName + "_RENAMING";
        statement.executeUpdate("ALTER TABLE " + quoted(actualName) + " RENAME TO " + quoted(temporaryName));
        statement.executeUpdate("ALTER TABLE " + quoted(temporaryName) + " RENAME TO " + quoted(desiredName));
    }

    private String findActualTableName(Connection connection, String desiredName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (tables.next()) {
                String actualName = tables.getString("name");
                if (desiredName.equalsIgnoreCase(actualName)) {
                    return actualName;
                }
            }
        }
        return null;
    }

    private void normalizeColumnNames(Connection connection, Statement statement, String tableName, List<String> desiredColumns) throws SQLException {
        for (String desiredColumn : desiredColumns) {
            String actualColumn = findActualColumnName(connection, tableName, desiredColumn);
            if (actualColumn == null || desiredColumn.equals(actualColumn)) {
                continue;
            }
            String temporaryColumn = "__" + desiredColumn + "_RENAMING";
            statement.executeUpdate("ALTER TABLE " + quoted(tableName) + " RENAME COLUMN " + quoted(actualColumn) + " TO " + quoted(temporaryColumn));
            statement.executeUpdate("ALTER TABLE " + quoted(tableName) + " RENAME COLUMN " + quoted(temporaryColumn) + " TO " + quoted(desiredColumn));
        }
    }

    private String findActualColumnName(Connection connection, String tableName, String desiredColumn) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + quoted(tableName) + ")")) {
            while (columns.next()) {
                String actualColumn = columns.getString("name");
                if (desiredColumn.equalsIgnoreCase(actualColumn)) {
                    return actualColumn;
                }
            }
        }
        return null;
    }

    private String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"").toUpperCase(Locale.ROOT) + "\"";
    }
}

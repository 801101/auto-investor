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
            createOrders(connection, statement);
            createDomesticStockMaster(connection, statement);
            createDomesticStockDashboard(statement);
            createOverseasStockMaster(connection, statement);
            createOverseasStockDashboard(statement);
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
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    STOCK_CODE TEXT NOT NULL,
                    STOCK_NAME TEXT,
                    STATUS TEXT NOT NULL,
                    PURCHASE_PRICE TEXT NOT NULL,
                    PURCHASE_QUANTITY TEXT NOT NULL,
                    INVESTED_AMOUNT TEXT NOT NULL,
                    CURRENT_PRICE TEXT,
                    CURRENT_VALUATION_AMOUNT TEXT,
                    PROFIT_RATE TEXT,
                    LAST_EVALUATED_PRICE TEXT,
                    STATUS_REFERENCE_PRICE TEXT,
                    GRAY_ENTERED_DATE TEXT,
                    GRAY_TRADING_DAYS INTEGER NOT NULL DEFAULT 0,
                    BROKER_ORDER_ID TEXT,
                    ACTIVE TEXT NOT NULL DEFAULT 'Y',
                    CREATED_AT TEXT NOT NULL,
                    UPDATED_AT TEXT NOT NULL
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
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_POSITIONS_ACTIVE_STOCK_CODE
                ON POSITIONS(STOCK_CODE)
                WHERE ACTIVE = 'Y'
                """);
    }

    private void createOrders(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ORDERS (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    BROKER_ORDER_ID TEXT,
                    STOCK_CODE TEXT NOT NULL,
                    ORDER_TYPE TEXT NOT NULL,
                    ORDER_QUANTITY TEXT NOT NULL,
                    ORDER_PRICE TEXT,
                    ORDER_AMOUNT TEXT NOT NULL,
                    ORDER_STATUS TEXT NOT NULL,
                    RETRY_COUNT INTEGER NOT NULL DEFAULT 0,
                    ERROR_MESSAGE TEXT,
                    REQUESTED_AT TEXT NOT NULL,
                    ACCEPTED_AT TEXT,
                    FILLED_AT TEXT,
                    UPDATED_AT TEXT NOT NULL
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
        addColumnIfMissing(connection, "ORDERS", "BROKER_ORDER_ORGNO", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "BROKER_STATUS", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "FILLED_QUANTITY", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "REMAINING_QUANTITY", "TEXT");
        addColumnIfMissing(connection, "ORDERS", "LAST_BROKER_STATUS_CHECKED_AT", "TEXT");
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_ORDERS_STOCK_STATUS
                ON ORDERS(STOCK_CODE, ORDER_STATUS, UPDATED_AT DESC)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_ORDERS_BROKER_ORDER
                ON ORDERS(BROKER_ORDER_ID, BROKER_ORDER_ORGNO)
                """);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_ORDERS_IDEMPOTENCY_KEY
                ON ORDERS(IDEMPOTENCY_KEY)
                WHERE IDEMPOTENCY_KEY IS NOT NULL
                """);
    }

    private void createOverseasStockMaster(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS OVERSEAS_STOCK_MASTER (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    SYMBOL TEXT NOT NULL,
                    STOCK_NAME TEXT,
                    EXCHANGE_CODE TEXT NOT NULL,
                    PRICE_EXCHANGE_CODE TEXT NOT NULL,
                    CURRENCY_CODE TEXT NOT NULL,
                    SECURITY_TYPE TEXT,
                    TRADABLE TEXT NOT NULL DEFAULT 'N',
                    ACTIVE TEXT NOT NULL DEFAULT 'Y',
                    FRACTIONAL_TRADABLE TEXT NOT NULL DEFAULT 'UNKNOWN',
                    LAST_PRICE TEXT,
                    MARKET_CAP TEXT,
                    TRADING_VOLUME TEXT,
                    LAST_SELECTED_AT TEXT,
                    LAST_BUY_ATTEMPT_AT TEXT,
                    LAST_BUY_SUCCESS_AT TEXT,
                    CONSECUTIVE_FAILURES INTEGER NOT NULL DEFAULT 0,
                    RETRY_AFTER TEXT,
                    EXCLUDED_REASON TEXT,
                    CREATED_AT TEXT NOT NULL,
                    UPDATED_AT TEXT NOT NULL,
                    LAST_SYNCED_AT TEXT
                )
                """);
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "FRACTIONAL_TRADABLE", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "LAST_SELECTED_AT", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "LAST_BUY_ATTEMPT_AT", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "LAST_BUY_SUCCESS_AT", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "CONSECUTIVE_FAILURES", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "RETRY_AFTER", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "EXCLUDED_REASON", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "FRACTIONAL_VERIFIED_AT", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "FRACTIONAL_VERIFICATION_SOURCE", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "LAST_VERIFICATION_ATTEMPT_AT", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "VERIFICATION_ATTEMPT_COUNT", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "LAST_KIS_RESPONSE_CODE", "TEXT");
        addColumnIfMissing(connection, "OVERSEAS_STOCK_MASTER", "LAST_KIS_RESPONSE_MESSAGE", "TEXT");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_OVERSEAS_STOCK_MASTER_SYMBOL_EXCHANGE
                ON OVERSEAS_STOCK_MASTER(SYMBOL, EXCHANGE_CODE)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_OVERSEAS_STOCK_MASTER_CANDIDATE
                ON OVERSEAS_STOCK_MASTER(EXCHANGE_CODE, PRICE_EXCHANGE_CODE, CURRENCY_CODE, ACTIVE, TRADABLE, FRACTIONAL_TRADABLE, LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, SYMBOL)
                """);
    }

    private void createDomesticStockMaster(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS DOMESTIC_STOCK_MASTER (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    SYMBOL TEXT NOT NULL,
                    STOCK_NAME TEXT,
                    MARKET_CODE TEXT NOT NULL,
                    STANDARD_CODE TEXT,
                    SECURITY_GROUP_CODE TEXT,
                    ETP TEXT NOT NULL DEFAULT 'N',
                    SPAC TEXT NOT NULL DEFAULT 'N',
                    TRADABLE TEXT NOT NULL DEFAULT 'N',
                    ACTIVE TEXT NOT NULL DEFAULT 'Y',
                    LAST_PRICE TEXT,
                    MARKET_CAP TEXT,
                    TRADING_VOLUME TEXT,
                    LAST_SELECTED_AT TEXT,
                    LAST_BUY_ATTEMPT_AT TEXT,
                    LAST_BUY_SUCCESS_AT TEXT,
                    CONSECUTIVE_FAILURES INTEGER NOT NULL DEFAULT 0,
                    RETRY_AFTER TEXT,
                    EXCLUDED_REASON TEXT,
                    CREATED_AT TEXT NOT NULL,
                    UPDATED_AT TEXT NOT NULL,
                    LAST_SYNCED_AT TEXT
                )
                """);
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "STANDARD_CODE", "TEXT");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "SECURITY_GROUP_CODE", "TEXT");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "ETP", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "SPAC", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "LAST_SELECTED_AT", "TEXT");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "LAST_BUY_ATTEMPT_AT", "TEXT");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "LAST_BUY_SUCCESS_AT", "TEXT");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "CONSECUTIVE_FAILURES", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "RETRY_AFTER", "TEXT");
        addColumnIfMissing(connection, "DOMESTIC_STOCK_MASTER", "EXCLUDED_REASON", "TEXT");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_DOMESTIC_STOCK_MASTER_SYMBOL_MARKET
                ON DOMESTIC_STOCK_MASTER(SYMBOL, MARKET_CODE)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_DOMESTIC_STOCK_MASTER_CANDIDATE
                ON DOMESTIC_STOCK_MASTER(MARKET_CODE, ACTIVE, TRADABLE, LAST_SELECTED_AT, LAST_BUY_ATTEMPT_AT, SYMBOL)
                """);
    }

    private void createDomesticStockDashboard(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS DOMESTIC_STOCK_DASHBOARD (
                    SYMBOL TEXT NOT NULL,
                    MARKET_CODE TEXT NOT NULL,
                    RANK_NO INTEGER NOT NULL,
                    STOCK_NAME TEXT,
                    CANDIDATE_SCORE TEXT NOT NULL,
                    PURCHASE_ZONE TEXT NOT NULL,
                    DASHBOARD_STATUS TEXT NOT NULL,
                    LAST_PRICE TEXT,
                    COMPLETED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    PENDING_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    RESERVED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    CURRENT_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    MAXIMUM_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    REMAINING_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    TOTAL_INVESTED_AMOUNT TEXT NOT NULL DEFAULT '0',
                    PENDING_INVESTMENT_AMOUNT TEXT NOT NULL DEFAULT '0',
                    PURCHASABLE TEXT NOT NULL DEFAULT 'N',
                    EXCLUSION_REASON TEXT,
                    LAST_SELECTED_AT TEXT,
                    LAST_BUY_SUCCESS_AT TEXT,
                    RETRY_AFTER TEXT,
                    EVALUATED_AT TEXT NOT NULL,
                    UPDATED_AT TEXT NOT NULL,
                    PRIMARY KEY(SYMBOL, MARKET_CODE)
                )
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_DOMESTIC_STOCK_DASHBOARD_ZONE_SCORE
                ON DOMESTIC_STOCK_DASHBOARD(MARKET_CODE, PURCHASE_ZONE, DASHBOARD_STATUS, CANDIDATE_SCORE DESC, RANK_NO)
                """);
    }

    private void createOverseasStockDashboard(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS OVERSEAS_STOCK_DASHBOARD (
                    SYMBOL TEXT NOT NULL,
                    EXCHANGE_CODE TEXT NOT NULL,
                    RANK_NO INTEGER NOT NULL,
                    STOCK_NAME TEXT,
                    CANDIDATE_SCORE TEXT NOT NULL,
                    PURCHASE_ZONE TEXT NOT NULL,
                    DASHBOARD_STATUS TEXT NOT NULL,
                    LAST_PRICE TEXT,
                    COMPLETED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    PENDING_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    RESERVED_BUY_COUNT INTEGER NOT NULL DEFAULT 0,
                    CURRENT_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    MAXIMUM_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    REMAINING_DUPLICATE_COUNT INTEGER NOT NULL DEFAULT 0,
                    TOTAL_INVESTED_AMOUNT TEXT NOT NULL DEFAULT '0',
                    PENDING_INVESTMENT_AMOUNT TEXT NOT NULL DEFAULT '0',
                    PURCHASABLE TEXT NOT NULL DEFAULT 'N',
                    EXCLUSION_REASON TEXT,
                    LAST_SELECTED_AT TEXT,
                    LAST_BUY_SUCCESS_AT TEXT,
                    RETRY_AFTER TEXT,
                    EVALUATED_AT TEXT NOT NULL,
                    UPDATED_AT TEXT NOT NULL,
                    PRIMARY KEY(SYMBOL, EXCHANGE_CODE)
                )
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_OVERSEAS_STOCK_DASHBOARD_ZONE_SCORE
                ON OVERSEAS_STOCK_DASHBOARD(EXCHANGE_CODE, PURCHASE_ZONE, DASHBOARD_STATUS, CANDIDATE_SCORE DESC, RANK_NO)
                """);
    }

    private void createTradeLifecycleHistory(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS TRADE_LIFECYCLE_HISTORY (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    LIFECYCLE_ID INTEGER NOT NULL,
                    EVENT_TYPE TEXT NOT NULL,
                    PREVIOUS_STATE TEXT,
                    NEW_STATE TEXT,
                    CURRENT_PRICE TEXT,
                    AVERAGE_BUY_PRICE TEXT,
                    REFERENCE_PRICE TEXT,
                    HIGHEST_PRICE TEXT,
                    LOWEST_PRICE TEXT,
                    HOLDING_QUANTITY TEXT,
                    RETURN_RATE TEXT,
                    GRAY_TRADING_DAYS INTEGER,
                    REASON TEXT,
                    ORDER_ID INTEGER,
                    EXECUTION_ID TEXT,
                    IDEMPOTENCY_KEY TEXT NOT NULL,
                    OCCURRED_AT TEXT NOT NULL
                )
                """);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS UX_TRADE_LIFECYCLE_HISTORY_IDEMPOTENCY
                ON TRADE_LIFECYCLE_HISTORY(IDEMPOTENCY_KEY)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS IDX_TRADE_LIFECYCLE_HISTORY_LIFECYCLE_TIME
                ON TRADE_LIFECYCLE_HISTORY(LIFECYCLE_ID, OCCURRED_AT, ID)
                """);
    }

    private void createSchedulerExecution(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SCHEDULER_EXECUTION (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    SCHEDULER_TYPE TEXT NOT NULL,
                    STARTED_AT TEXT NOT NULL,
                    FINISHED_AT TEXT,
                    EXECUTION_STATUS TEXT NOT NULL,
                    MESSAGE TEXT
                )
                """);
    }

    private void createAuditLog(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS AUDIT_LOG (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    EVENT_TYPE TEXT NOT NULL,
                    STOCK_CODE TEXT,
                    details TEXT NOT NULL,
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
                "LAST_EVALUATED_AT", "FLAT_STARTED_DATE"));
        tableColumns.put("ORDERS", List.of(
                "ID", "BROKER_ORDER_ID", "STOCK_CODE", "ORDER_TYPE", "ORDER_QUANTITY", "ORDER_PRICE",
                "ORDER_AMOUNT", "ORDER_STATUS", "RETRY_COUNT", "ERROR_MESSAGE", "REQUESTED_AT",
                "ACCEPTED_AT", "FILLED_AT", "UPDATED_AT", "IDEMPOTENCY_KEY", "DECISION_CYCLE_ID",
                "INSTANCE_ID", "MASKED_ACCOUNT", "SKIP_REASON", "EXIT_REASON", "DRY_RUN",
                "CURRENT_PRICE", "CURRENT_PRICE_AT", "BROKER_ORDER_ORGNO", "BROKER_STATUS",
                "FILLED_QUANTITY", "REMAINING_QUANTITY", "LAST_BROKER_STATUS_CHECKED_AT"));
        tableColumns.put("DOMESTIC_STOCK_MASTER", List.of(
                "ID", "SYMBOL", "STOCK_NAME", "MARKET_CODE", "STANDARD_CODE", "SECURITY_GROUP_CODE",
                "ETP", "SPAC", "TRADABLE", "ACTIVE", "LAST_PRICE", "MARKET_CAP", "TRADING_VOLUME",
                "LAST_SELECTED_AT", "LAST_BUY_ATTEMPT_AT", "LAST_BUY_SUCCESS_AT", "CONSECUTIVE_FAILURES",
                "RETRY_AFTER", "EXCLUDED_REASON", "CREATED_AT", "UPDATED_AT", "LAST_SYNCED_AT"));
        tableColumns.put("DOMESTIC_STOCK_DASHBOARD", List.of(
                "SYMBOL", "MARKET_CODE", "RANK_NO", "STOCK_NAME", "CANDIDATE_SCORE", "PURCHASE_ZONE",
                "DASHBOARD_STATUS", "LAST_PRICE", "COMPLETED_BUY_COUNT", "PENDING_BUY_COUNT",
                "RESERVED_BUY_COUNT", "CURRENT_DUPLICATE_COUNT", "MAXIMUM_DUPLICATE_COUNT",
                "REMAINING_DUPLICATE_COUNT", "TOTAL_INVESTED_AMOUNT", "PENDING_INVESTMENT_AMOUNT",
                "PURCHASABLE", "EXCLUSION_REASON", "LAST_SELECTED_AT", "LAST_BUY_SUCCESS_AT",
                "RETRY_AFTER", "EVALUATED_AT", "UPDATED_AT"));
        tableColumns.put("OVERSEAS_STOCK_MASTER", List.of(
                "ID", "SYMBOL", "STOCK_NAME", "EXCHANGE_CODE", "PRICE_EXCHANGE_CODE", "CURRENCY_CODE",
                "SECURITY_TYPE", "TRADABLE", "ACTIVE", "FRACTIONAL_TRADABLE", "LAST_PRICE",
                "MARKET_CAP", "TRADING_VOLUME", "LAST_SELECTED_AT", "LAST_BUY_ATTEMPT_AT",
                "LAST_BUY_SUCCESS_AT", "CONSECUTIVE_FAILURES", "RETRY_AFTER", "EXCLUDED_REASON",
                "CREATED_AT", "UPDATED_AT", "LAST_SYNCED_AT", "FRACTIONAL_VERIFIED_AT",
                "FRACTIONAL_VERIFICATION_SOURCE", "LAST_VERIFICATION_ATTEMPT_AT",
                "VERIFICATION_ATTEMPT_COUNT", "LAST_KIS_RESPONSE_CODE", "LAST_KIS_RESPONSE_MESSAGE"));
        tableColumns.put("OVERSEAS_STOCK_DASHBOARD", List.of(
                "SYMBOL", "EXCHANGE_CODE", "RANK_NO", "STOCK_NAME", "CANDIDATE_SCORE", "PURCHASE_ZONE",
                "DASHBOARD_STATUS", "LAST_PRICE", "COMPLETED_BUY_COUNT", "PENDING_BUY_COUNT",
                "RESERVED_BUY_COUNT", "CURRENT_DUPLICATE_COUNT", "MAXIMUM_DUPLICATE_COUNT",
                "REMAINING_DUPLICATE_COUNT", "TOTAL_INVESTED_AMOUNT", "PENDING_INVESTMENT_AMOUNT",
                "PURCHASABLE", "EXCLUSION_REASON", "LAST_SELECTED_AT", "LAST_BUY_SUCCESS_AT",
                "RETRY_AFTER", "EVALUATED_AT", "UPDATED_AT"));
        tableColumns.put("TRADE_LIFECYCLE_HISTORY", List.of(
                "ID", "LIFECYCLE_ID", "EVENT_TYPE", "PREVIOUS_STATE", "NEW_STATE", "CURRENT_PRICE",
                "AVERAGE_BUY_PRICE", "REFERENCE_PRICE", "HIGHEST_PRICE", "LOWEST_PRICE", "HOLDING_QUANTITY",
                "RETURN_RATE", "GRAY_TRADING_DAYS", "REASON", "ORDER_ID", "EXECUTION_ID",
                "IDEMPOTENCY_KEY", "OCCURRED_AT"));
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

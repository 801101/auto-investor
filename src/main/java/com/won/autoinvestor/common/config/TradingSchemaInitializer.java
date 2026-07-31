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
            createPanicStopEvents(statement);
            createPositions(connection, statement);
            createOrders(connection, statement);
            createDomesticStockMaster(connection, statement);
            createDomesticStockDashboard(statement);
            createOverseasStockMaster(connection, statement);
            createOverseasStockDashboard(statement);
            createTradeLifecycleHistory(statement);
            createSchedulerExecution(statement);
            createAuditLog(statement);
        }

        logger.info("trading schema initialized");
    }

    private void createPanicStopEvents(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS panic_stop_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reason TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    alert_status TEXT NOT NULL,
                    active TEXT NOT NULL DEFAULT 'Y',
                    created_at TEXT NOT NULL,
                    resolved_at TEXT
                )
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_panic_stop_events_active
                ON panic_stop_events(active, created_at DESC, id DESC)
                """);
    }

    private void createPositions(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS positions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stock_code TEXT NOT NULL,
                    stock_name TEXT,
                    status TEXT NOT NULL,
                    purchase_price TEXT NOT NULL,
                    purchase_quantity TEXT NOT NULL,
                    invested_amount TEXT NOT NULL,
                    current_price TEXT,
                    current_valuation_amount TEXT,
                    profit_rate TEXT,
                    last_evaluated_price TEXT,
                    status_reference_price TEXT,
                    gray_entered_date TEXT,
                    gray_trading_days INTEGER NOT NULL DEFAULT 0,
                    broker_order_id TEXT,
                    active TEXT NOT NULL DEFAULT 'Y',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
        addColumnIfMissing(connection, "positions", "average_buy_price", "TEXT");
        addColumnIfMissing(connection, "positions", "reference_price", "TEXT");
        addColumnIfMissing(connection, "positions", "highest_price", "TEXT");
        addColumnIfMissing(connection, "positions", "lowest_price", "TEXT");
        addColumnIfMissing(connection, "positions", "holding_quantity", "TEXT");
        addColumnIfMissing(connection, "positions", "return_rate", "TEXT");
        addColumnIfMissing(connection, "positions", "last_evaluated_at", "TEXT");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_positions_active_stock_code
                ON positions(stock_code)
                WHERE active = 'Y'
                """);
    }

    private void createOrders(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    broker_order_id TEXT,
                    stock_code TEXT NOT NULL,
                    order_type TEXT NOT NULL,
                    order_quantity TEXT NOT NULL,
                    order_price TEXT,
                    order_amount TEXT NOT NULL,
                    order_status TEXT NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    error_message TEXT,
                    requested_at TEXT NOT NULL,
                    accepted_at TEXT,
                    filled_at TEXT,
                    updated_at TEXT NOT NULL
                )
                """);
        addColumnIfMissing(connection, "orders", "idempotency_key", "TEXT");
        addColumnIfMissing(connection, "orders", "decision_cycle_id", "TEXT");
        addColumnIfMissing(connection, "orders", "instance_id", "TEXT");
        addColumnIfMissing(connection, "orders", "masked_account", "TEXT");
        addColumnIfMissing(connection, "orders", "skip_reason", "TEXT");
        addColumnIfMissing(connection, "orders", "exit_reason", "TEXT");
        addColumnIfMissing(connection, "orders", "dry_run", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "orders", "current_price", "TEXT");
        addColumnIfMissing(connection, "orders", "current_price_at", "TEXT");
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_orders_stock_status
                ON orders(stock_code, order_status, updated_at DESC)
                """);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_idempotency_key
                ON orders(idempotency_key)
                WHERE idempotency_key IS NOT NULL
                """);
    }

    private void createOverseasStockMaster(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS overseas_stock_master (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    symbol TEXT NOT NULL,
                    stock_name TEXT,
                    exchange_code TEXT NOT NULL,
                    price_exchange_code TEXT NOT NULL,
                    currency_code TEXT NOT NULL,
                    security_type TEXT,
                    tradable TEXT NOT NULL DEFAULT 'N',
                    active TEXT NOT NULL DEFAULT 'Y',
                    fractional_tradable TEXT NOT NULL DEFAULT 'UNKNOWN',
                    last_price TEXT,
                    market_cap TEXT,
                    trading_volume TEXT,
                    last_selected_at TEXT,
                    last_buy_attempt_at TEXT,
                    last_buy_success_at TEXT,
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    retry_after TEXT,
                    excluded_reason TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    last_synced_at TEXT
                )
                """);
        addColumnIfMissing(connection, "overseas_stock_master", "fractional_tradable", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "overseas_stock_master", "last_selected_at", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "last_buy_attempt_at", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "last_buy_success_at", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "consecutive_failures", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "overseas_stock_master", "retry_after", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "excluded_reason", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "fractional_verified_at", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "fractional_verification_source", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "last_verification_attempt_at", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "verification_attempt_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "overseas_stock_master", "last_kis_response_code", "TEXT");
        addColumnIfMissing(connection, "overseas_stock_master", "last_kis_response_message", "TEXT");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_overseas_stock_master_symbol_exchange
                ON overseas_stock_master(symbol, exchange_code)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_overseas_stock_master_candidate
                ON overseas_stock_master(exchange_code, price_exchange_code, currency_code, active, tradable, fractional_tradable, last_selected_at, last_buy_attempt_at, symbol)
                """);
    }

    private void createDomesticStockMaster(Connection connection, Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS domestic_stock_master (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    symbol TEXT NOT NULL,
                    stock_name TEXT,
                    market_code TEXT NOT NULL,
                    standard_code TEXT,
                    security_group_code TEXT,
                    etp TEXT NOT NULL DEFAULT 'N',
                    spac TEXT NOT NULL DEFAULT 'N',
                    tradable TEXT NOT NULL DEFAULT 'N',
                    active TEXT NOT NULL DEFAULT 'Y',
                    last_price TEXT,
                    market_cap TEXT,
                    trading_volume TEXT,
                    last_selected_at TEXT,
                    last_buy_attempt_at TEXT,
                    last_buy_success_at TEXT,
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    retry_after TEXT,
                    excluded_reason TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    last_synced_at TEXT
                )
                """);
        addColumnIfMissing(connection, "domestic_stock_master", "standard_code", "TEXT");
        addColumnIfMissing(connection, "domestic_stock_master", "security_group_code", "TEXT");
        addColumnIfMissing(connection, "domestic_stock_master", "etp", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "domestic_stock_master", "spac", "TEXT NOT NULL DEFAULT 'N'");
        addColumnIfMissing(connection, "domestic_stock_master", "last_selected_at", "TEXT");
        addColumnIfMissing(connection, "domestic_stock_master", "last_buy_attempt_at", "TEXT");
        addColumnIfMissing(connection, "domestic_stock_master", "last_buy_success_at", "TEXT");
        addColumnIfMissing(connection, "domestic_stock_master", "consecutive_failures", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "domestic_stock_master", "retry_after", "TEXT");
        addColumnIfMissing(connection, "domestic_stock_master", "excluded_reason", "TEXT");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_domestic_stock_master_symbol_market
                ON domestic_stock_master(symbol, market_code)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_domestic_stock_master_candidate
                ON domestic_stock_master(market_code, active, tradable, last_selected_at, last_buy_attempt_at, symbol)
                """);
    }

    private void createDomesticStockDashboard(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS domestic_stock_dashboard (
                    symbol TEXT NOT NULL,
                    market_code TEXT NOT NULL,
                    rank_no INTEGER NOT NULL,
                    stock_name TEXT,
                    candidate_score TEXT NOT NULL,
                    purchase_zone TEXT NOT NULL,
                    dashboard_status TEXT NOT NULL,
                    last_price TEXT,
                    completed_buy_count INTEGER NOT NULL DEFAULT 0,
                    pending_buy_count INTEGER NOT NULL DEFAULT 0,
                    reserved_buy_count INTEGER NOT NULL DEFAULT 0,
                    current_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    maximum_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    remaining_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    total_invested_amount TEXT NOT NULL DEFAULT '0',
                    pending_investment_amount TEXT NOT NULL DEFAULT '0',
                    purchasable TEXT NOT NULL DEFAULT 'N',
                    exclusion_reason TEXT,
                    last_selected_at TEXT,
                    last_buy_success_at TEXT,
                    retry_after TEXT,
                    evaluated_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(symbol, market_code)
                )
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_domestic_stock_dashboard_zone_score
                ON domestic_stock_dashboard(market_code, purchase_zone, dashboard_status, candidate_score DESC, rank_no)
                """);
    }

    private void createOverseasStockDashboard(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS overseas_stock_dashboard (
                    symbol TEXT NOT NULL,
                    exchange_code TEXT NOT NULL,
                    rank_no INTEGER NOT NULL,
                    stock_name TEXT,
                    candidate_score TEXT NOT NULL,
                    purchase_zone TEXT NOT NULL,
                    dashboard_status TEXT NOT NULL,
                    last_price TEXT,
                    completed_buy_count INTEGER NOT NULL DEFAULT 0,
                    pending_buy_count INTEGER NOT NULL DEFAULT 0,
                    reserved_buy_count INTEGER NOT NULL DEFAULT 0,
                    current_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    maximum_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    remaining_duplicate_count INTEGER NOT NULL DEFAULT 0,
                    total_invested_amount TEXT NOT NULL DEFAULT '0',
                    pending_investment_amount TEXT NOT NULL DEFAULT '0',
                    purchasable TEXT NOT NULL DEFAULT 'N',
                    exclusion_reason TEXT,
                    last_selected_at TEXT,
                    last_buy_success_at TEXT,
                    retry_after TEXT,
                    evaluated_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(symbol, exchange_code)
                )
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_overseas_stock_dashboard_zone_score
                ON overseas_stock_dashboard(exchange_code, purchase_zone, dashboard_status, candidate_score DESC, rank_no)
                """);
    }

    private void createTradeLifecycleHistory(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS trade_lifecycle_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lifecycle_id INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    previous_state TEXT,
                    new_state TEXT,
                    current_price TEXT,
                    average_buy_price TEXT,
                    reference_price TEXT,
                    highest_price TEXT,
                    lowest_price TEXT,
                    holding_quantity TEXT,
                    return_rate TEXT,
                    gray_trading_days INTEGER,
                    reason TEXT,
                    order_id INTEGER,
                    execution_id TEXT,
                    idempotency_key TEXT NOT NULL,
                    occurred_at TEXT NOT NULL
                )
                """);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_trade_lifecycle_history_idempotency
                ON trade_lifecycle_history(idempotency_key)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_trade_lifecycle_history_lifecycle_time
                ON trade_lifecycle_history(lifecycle_id, occurred_at, id)
                """);
    }

    private void createSchedulerExecution(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS scheduler_execution (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scheduler_type TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    finished_at TEXT,
                    execution_status TEXT NOT NULL,
                    message TEXT
                )
                """);
    }

    private void createAuditLog(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT NOT NULL,
                    stock_code TEXT,
                    details TEXT NOT NULL,
                    created_at TEXT NOT NULL
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
}

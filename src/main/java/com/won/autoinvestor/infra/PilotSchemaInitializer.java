package com.won.autoinvestor.infra;

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
public class PilotSchemaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(PilotSchemaInitializer.class);

    private final DataSource dataSource;

    public PilotSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pilot_market_ticks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        last_price TEXT NOT NULL,
                        bid_price TEXT,
                        ask_price TEXT,
                        traded_at TEXT NOT NULL,
                        received_at TEXT NOT NULL DEFAULT (datetime('now'))
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_pilot_market_ticks_latest
                    ON pilot_market_ticks(symbol, received_at DESC, id DESC)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pilot_positions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        quantity TEXT NOT NULL,
                        invested_amount TEXT NOT NULL,
                        average_entry_price TEXT NOT NULL,
                        status TEXT NOT NULL,
                        opened_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        closed_at TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_pilot_positions_open_symbol
                    ON pilot_positions(symbol)
                    WHERE status = 'OPEN'
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pilot_order_intents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        side TEXT NOT NULL,
                        order_amount TEXT NOT NULL,
                        order_quantity TEXT NOT NULL,
                        reference_price TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pilot_observations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        market_state TEXT NOT NULL,
                        entry_pattern TEXT NOT NULL,
                        position_status TEXT NOT NULL,
                        survival_seconds INTEGER NOT NULL,
                        observed_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS "파일럿_잔고" (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        "종목" TEXT NOT NULL,
                        "통화" TEXT NOT NULL,
                        "수량" TEXT NOT NULL,
                        "투입금액" TEXT NOT NULL,
                        "평균진입가" TEXT NOT NULL,
                        "상태" TEXT NOT NULL,
                        "진입시각" TEXT NOT NULL,
                        "갱신시각" TEXT NOT NULL,
                        "청산시각" TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS "ux_파일럿_잔고_보유_종목"
                    ON "파일럿_잔고"("종목")
                    WHERE "상태" = '보유'
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS "오토봇_잔고" (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        "종목" TEXT NOT NULL,
                        "통화" TEXT NOT NULL,
                        "수량" TEXT NOT NULL,
                        "평균진입가" TEXT NOT NULL,
                        "상태" TEXT NOT NULL,
                        "등급" TEXT NOT NULL,
                        "진입시각" TEXT NOT NULL,
                        "갱신시각" TEXT NOT NULL,
                        "청산시각" TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS "ux_오토봇_잔고_보유_종목"
                    ON "오토봇_잔고"("종목")
                    WHERE "상태" = '보유'
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS "시장_관측_데이터" (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        "종목" TEXT NOT NULL,
                        "통화" TEXT NOT NULL,
                        "시장상태" TEXT NOT NULL,
                        "진입패턴" TEXT NOT NULL,
                        "포지션상태" TEXT NOT NULL,
                        "생존초" INTEGER NOT NULL,
                        "등급" TEXT NOT NULL,
                        "관측시각" TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS asset_grade_decisions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        grade TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        decided_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_asset_grade_decisions_latest
                    ON asset_grade_decisions(symbol, decided_at DESC, id DESC)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS autobot_order_intents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        side TEXT NOT NULL,
                        share_quantity TEXT NOT NULL,
                        reference_price TEXT NOT NULL,
                        asset_grade TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS active_status_tracker (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        trading_history_master_id INTEGER,
                        system_type TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        entry_price TEXT NOT NULL,
                        entry_quantity TEXT NOT NULL,
                        entry_amount TEXT NOT NULL,
                        entry_time TEXT NOT NULL,
                        status TEXT NOT NULL,
                        status_entered_at TEXT NOT NULL,
                        gray_entered_at TEXT,
                        force_liquidation_flag TEXT NOT NULL DEFAULT 'N',
                        isolated_at TEXT,
                        isolation_reason TEXT,
                        updated_at TEXT NOT NULL
                    )
                    """);
            addColumnIfMissing(connection, "active_status_tracker", "trading_history_master_id", "INTEGER");
            addColumnIfMissing(connection, "active_status_tracker", "force_liquidation_flag", "TEXT NOT NULL DEFAULT 'N'");
            addColumnIfMissing(connection, "active_status_tracker", "isolated_at", "TEXT");
            addColumnIfMissing(connection, "active_status_tracker", "isolation_reason", "TEXT");
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_active_status_tracker_active_symbol
                    ON active_status_tracker(system_type, symbol)
                    WHERE status IN ('WHITE', 'GRAY', 'BLACK')
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_active_status_tracker_status_time
                    ON active_status_tracker(status, entry_time, gray_entered_at)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trading_history_master (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        system_type TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        buy_price TEXT NOT NULL,
                        buy_quantity TEXT NOT NULL,
                        buy_amount TEXT NOT NULL,
                        buy_time TEXT NOT NULL,
                        sell_price TEXT,
                        sell_time TEXT,
                        pnl_ratio TEXT,
                        force_liquidation_flag TEXT NOT NULL DEFAULT 'N',
                        integrity_verified_at TEXT,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            addColumnIfMissing(connection, "trading_history_master", "force_liquidation_flag", "TEXT NOT NULL DEFAULT 'N'");
            addColumnIfMissing(connection, "trading_history_master", "integrity_verified_at", "TEXT");
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_trading_history_master_open_symbol
                    ON trading_history_master(system_type, symbol, status, buy_time)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trading_status_history_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tracker_id INTEGER,
                        master_id INTEGER,
                        system_type TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        previous_status TEXT,
                        new_status TEXT NOT NULL,
                        event_type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        force_liquidation_flag TEXT NOT NULL DEFAULT 'N',
                        logged_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_trading_status_history_log_target
                    ON trading_status_history_log(system_type, symbol, logged_at DESC, id DESC)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trading_training_dataset (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        master_id INTEGER NOT NULL UNIQUE,
                        system_type TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        market_currency TEXT NOT NULL,
                        buy_price TEXT NOT NULL,
                        buy_quantity TEXT NOT NULL,
                        buy_amount TEXT NOT NULL,
                        buy_time TEXT NOT NULL,
                        sell_price TEXT NOT NULL,
                        sell_time TEXT NOT NULL,
                        pnl_ratio TEXT NOT NULL,
                        force_liquidation_flag TEXT NOT NULL,
                        outcome_status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
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
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS budget_allocation_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        market_currency TEXT NOT NULL,
                        total_wallet_balance TEXT NOT NULL,
                        pilot_budget TEXT NOT NULL,
                        autobot_budget TEXT NOT NULL,
                        pilot_used_amount TEXT NOT NULL,
                        autobot_used_amount TEXT NOT NULL,
                        pilot_available_amount TEXT NOT NULL,
                        autobot_available_amount TEXT NOT NULL,
                        calculated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_budget_allocation_snapshots_latest
                    ON budget_allocation_snapshots(market_currency, calculated_at DESC, id DESC)
                    """);
        }

        logger.info("pilot schema initialized");
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

package com.won.autoinvestor.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component("databaseFileBootstrap")
public class DatabaseFileBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseFileBootstrap.class);
    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

    private final String datasourceUrl;

    public DatabaseFileBootstrap(@Value("${spring.datasource.url}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void bootstrap() throws IOException {
        if (datasourceUrl == null || !datasourceUrl.startsWith(SQLITE_URL_PREFIX)) {
            logger.info("database bootstrap skipped because datasource is not SQLite");
            return;
        }

        String databasePathValue = datasourceUrl.substring(SQLITE_URL_PREFIX.length());
        if (databasePathValue.isBlank() || ":memory:".equalsIgnoreCase(databasePathValue)) {
            logger.info("database bootstrap skipped for SQLite memory database");
            return;
        }

        Path runtimeDatabasePath = Path.of(databasePathValue).toAbsolutePath().normalize();
        Path parent = runtimeDatabasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(runtimeDatabasePath)) {
            logger.info("runtime database already exists: {}", runtimeDatabasePath);
            return;
        }

        Files.createFile(runtimeDatabasePath);
        logger.info("runtime database file created: {}", runtimeDatabasePath);
    }
}

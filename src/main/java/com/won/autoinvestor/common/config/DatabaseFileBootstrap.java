package com.won.autoinvestor.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sql.DataSource;

@Component("databaseFileBootstrap")
public class DatabaseFileBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseFileBootstrap.class);
    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";
    private static final String LOCAL_PROFILE = "local";
    private static final String DATABASE_FILE_NAME = "auto-investor.db";
    private static final String LOCAL_DATABASE_DIRECTORY = "./src/main/resources";

    private final String configuredDatasourceUrl;
    private final Environment environment;

    public DatabaseFileBootstrap(@Value("${spring.datasource.url:}") String configuredDatasourceUrl,
                                 Environment environment) {
        this.configuredDatasourceUrl = configuredDatasourceUrl;
        this.environment = environment;
    }

    @PostConstruct
    public void bootstrap() throws IOException {
        String datasourceUrl = resolveDatasourceUrl();
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
            logger.info("runtime database already exists. mode={}, path={}", modeName(), runtimeDatabasePath);
            return;
        }

        Files.createFile(runtimeDatabasePath);
        logger.info("runtime database file created. mode={}, path={}", modeName(), runtimeDatabasePath);
    }

    @Bean
    @DependsOn("databaseFileBootstrap")
    public DataSource dataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(resolveDatasourceUrl());
        return dataSource;
    }

    private String resolveDatasourceUrl() {
        if (configuredDatasourceUrl != null && !configuredDatasourceUrl.isBlank()) {
            return configuredDatasourceUrl;
        }

        Path databasePath = isLocalProfile()
                ? Path.of(LOCAL_DATABASE_DIRECTORY, DATABASE_FILE_NAME)
                : jarDirectory().resolve(DATABASE_FILE_NAME);
        return SQLITE_URL_PREFIX + databasePath.toAbsolutePath().normalize();
    }

    private Path jarDirectory() {
        Path classPath = pathFromSystemProperty("java.class.path");
        if (classPath != null && Files.isRegularFile(classPath)) {
            return classPath.getParent();
        }

        String javaCommand = System.getProperty("sun.java.command", "");
        String commandPathValue = javaCommand.split("\\s+", 2)[0];
        Path commandPath = pathFromSystemProperty(commandPathValue);
        if (commandPath != null && Files.isRegularFile(commandPath)) {
            return commandPath.getParent();
        }

        try {
            Path codeSource = Path.of(DatabaseFileBootstrap.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isRegularFile(codeSource)) {
                return codeSource.getParent();
            }
        } catch (URISyntaxException | NullPointerException e) {
            logger.warn("cannot resolve JAR directory; using current directory", e);
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    private Path pathFromSystemProperty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String modeName() {
        return isLocalProfile() ? "LOCAL" : "JAR";
    }

    private boolean isLocalProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(LOCAL_PROFILE::equalsIgnoreCase);
    }
}

package com.won.autoinvestor.infra;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component("databaseFileBootstrap")
public class DatabaseFileBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseFileBootstrap.class);
    private static final String DATABASE_FILE_NAME = "auto-investor.db";

    @PostConstruct
    public void bootstrap() throws IOException {
        Path runtimeDatabasePath = Path.of(DATABASE_FILE_NAME).toAbsolutePath().normalize();

        if (Files.exists(runtimeDatabasePath)) {
            logger.info("runtime database already exists: {}", runtimeDatabasePath);
            return;
        }

        ClassPathResource seedDatabase = new ClassPathResource(DATABASE_FILE_NAME);
        if (!seedDatabase.exists()) {
            throw new IllegalStateException("seed database not found on classpath: " + DATABASE_FILE_NAME);
        }

        try (InputStream inputStream = seedDatabase.getInputStream()) {
            Files.copy(inputStream, runtimeDatabasePath);
            logger.info("runtime database created from classpath seed: {}", runtimeDatabasePath);
        }
    }
}

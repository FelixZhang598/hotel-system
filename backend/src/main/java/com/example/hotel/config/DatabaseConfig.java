package com.example.hotel.config;

import java.nio.file.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration
public class DatabaseConfig {
  @Bean
  DataSource dataSource(@Value("${hotel.database}") String database) throws java.io.IOException {
    Path path = Path.of(database).toAbsolutePath().normalize();
    if (path.getParent() != null) Files.createDirectories(path.getParent());
    SQLiteConfig config = new SQLiteConfig();
    config.enforceForeignKeys(true);
    config.setBusyTimeout(3000);
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
    SQLiteDataSource ds = new SQLiteDataSource(config);
    ds.setUrl("jdbc:sqlite:" + path);
    return ds;
  }

  @Bean
  DataSourceTransactionManager transactionManager(DataSource ds) {
    return new DataSourceTransactionManager(ds);
  }

  @Bean(destroyMethod = "close")
  java.sql.Connection databaseLifetimeConnection(DataSource ds) throws java.sql.SQLException {
    // Keep the database file open while the app runs so reset scripts can detect its owner.
    // This connection stays in auto-commit and never participates in business transactions.
    return ds.getConnection();
  }
}

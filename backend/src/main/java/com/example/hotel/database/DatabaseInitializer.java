package com.example.hotel.database;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
public class DatabaseInitializer {
  @Bean
  ApplicationRunner initialize(DataSource ds, DemoSeeder seeder) {
    return args -> {
      new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(ds);
      seeder.seed();
    };
  }
}

package com.example.hotel.database;

import java.util.function.Supplier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WriteTransactions {
  private final TransactionTemplate template;

  public WriteTransactions(DataSourceTransactionManager manager) {
    template = new TransactionTemplate(manager);
  }

  public <T> T execute(Supplier<T> action) {
    return template.execute(status -> action.get());
  }
}

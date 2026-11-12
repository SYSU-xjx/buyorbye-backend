package com.hku.money.common;

import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration
public class AppConfiguration {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  // One manager binds the same JDBC connection for JPA and MyBatis.
  @Bean
  public JpaTransactionManager transactionManager(EntityManagerFactory emf, DataSource dataSource) {
    var manager = new JpaTransactionManager(emf);
    manager.setDataSource(dataSource);
    return manager;
  }
}

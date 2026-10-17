package com.ecommerce.order.services;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseClock {
  private final JdbcTemplate jdbcTemplate;

  public DatabaseClock(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Instant now() {
    return jdbcTemplate.queryForObject(
        "select clock_timestamp()",
        (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant());
  }

  public Instant deadlineAfter(long timeoutSeconds) {
    return jdbcTemplate.queryForObject(
        "select clock_timestamp() + (? * interval '1 second')",
        (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
        timeoutSeconds);
  }
}

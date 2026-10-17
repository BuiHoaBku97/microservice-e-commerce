package com.ecommerce.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class OutboxMigrationIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.4");

  @ParameterizedTest
  @CsvSource({"order,7", "payment,3"})
  void migrate_shouldPreserveExistingIdsAndBackfillLegacyPayloads(String service, String oldVersion)
      throws Exception {
    String schema = "migration_" + service;
    String location = "filesystem:" + Path.of(System.getProperty("repo.root"), service + "-service",
        "src/main/resources/db/migration").toString().replace('\\', '/');
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations(location).schemas(schema).defaultSchema(schema).target(oldVersion).load().migrate();
    try (var connection = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setSchema(schema);
      String stableId = UUID.randomUUID().toString();
      String payload = "{\"eventId\":\"" + stableId + "\",\"orderId\":10,\"amount\":100}";
      try (var insert = connection.prepareStatement(
          "INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,payload,status)"
              + " VALUES ('Order',10,'OrderCreated',?,'PENDING')")) {
        insert.setString(1, payload);
        insert.executeUpdate();
        insert.setString(1, "{\"orderId\":11,\"amount\":200}");
        insert.executeUpdate();
      }
      String preCorrelationVersion = "order".equals(service) ? "16" : "9";
      Flyway legacyLatest = Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
          .locations(location).schemas(schema).defaultSchema(schema)
          .target(preCorrelationVersion).load();
      legacyLatest.migrate();
      legacyLatest.validate();
      var mapper = new ObjectMapper();
      String generated;
      try (var statement = connection.createStatement();
           var rows = statement.executeQuery("SELECT event_id,payload FROM outbox_events ORDER BY id")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("event_id")).isEqualTo(stableId);
        assertThat(rows.getString("payload")).isEqualTo(payload);
        assertThat(rows.next()).isTrue();
        generated = rows.getString("event_id");
        assertThat(generated).isNotBlank().isNotEqualTo(stableId);
        var legacy = mapper.readTree(rows.getString("payload"));
        assertThat(legacy.path("eventId").asText()).isEqualTo(generated);
        assertThat(legacy.path("orderId").asLong()).isEqualTo(11);
        assertThat(legacy.path("amount").asLong()).isEqualTo(200);
      }
      try (var statement = connection.createStatement()) {
        statement.executeUpdate("DELETE FROM outbox_events");
      }
      Flyway latest = Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
          .locations(location).schemas(schema).defaultSchema(schema).load();
      latest.migrate();
      latest.validate();
      assertThat(latest.migrate().migrationsExecuted).isZero();
      try (var duplicate = connection.prepareStatement(
          "INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload,"
              + "correlation_id,status,aggregate_version,schema_version,topic,partition_key,occurred_at)"
              + " VALUES (?, 'Order',10,'OrderCreated','{}','migration-test','PENDING',0,1,"
              + "'migration-topic','10',now())")) {
        duplicate.setString(1, stableId);
        assertThatThrownBy(duplicate::executeUpdate).isInstanceOfSatisfying(SQLException.class,
            error -> assertThat(error.getSQLState()).isEqualTo("23505"));
        duplicate.setString(1, null);
        assertThatThrownBy(duplicate::executeUpdate).isInstanceOfSatisfying(SQLException.class,
            error -> assertThat(error.getSQLState()).isEqualTo("23502"));
      }
    }
  }
}

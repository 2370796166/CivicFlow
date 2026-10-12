package com.civicflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

class NotificationFlywayMySqlTest {
    @Test
    void freshSchemaMigratesAndEventChannelIsUnique() throws Exception {
        try (MySQLContainer<?> mysql =
                new MySQLContainer<>("mysql:8.4.11")
                        .withDatabaseName("civicflow_notification")
                        .withUsername("notification_test")
                        .withPassword("notification_test_password")) {
            mysql.start();
            Flyway flyway =
                    Flyway.configure()
                            .dataSource(
                                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                            .locations("classpath:db/migration")
                            .load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var connection =
                            DriverManager.getConnection(
                                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                    var statement = connection.createStatement()) {
                try (var result =
                        statement.executeQuery("SELECT COUNT(*) FROM notification_template")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(7);
                }
                String eventId = UUID.randomUUID().toString();
                String insert =
                        "INSERT INTO notification_record(id,event_id,channel,event_type,template_code,template_version,user_id,aggregate_id,required_notice,payload_hash,event_json,title,body,status,attempts,created_at,updated_at) VALUES (%d,'"
                                + eventId
                                + "','IN_APP','appointment.notification.created','APPOINTMENT_CREATED',1,1,'42',1,'hash','{}','title','body','SENT',1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))";
                assertThat(statement.executeUpdate(insert.formatted(1))).isEqualTo(1);
                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> statement.executeUpdate(insert.formatted(2)))
                        .isInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
            }
        }
    }
}

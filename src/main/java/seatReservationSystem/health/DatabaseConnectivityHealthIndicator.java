package seatReservationSystem.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Readiness-only database check: validates a real connection and {@code SELECT 1}.
 * Included in the {@code readiness} health group, not liveness.
 */
@Component("database")
public class DatabaseConnectivityHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseConnectivityHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            if (resultSet.next() && resultSet.getInt(1) == 1) {
                return Health.up()
                        .withDetail("check", "SELECT 1")
                        .build();
            }

            return Health.down()
                    .withDetail("reason", "Unexpected validation query result")
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("reason", "Database unreachable")
                    .build();
        }
    }
}

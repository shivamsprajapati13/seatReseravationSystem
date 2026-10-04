package seatReservationSystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs JDBC target on startup (no credentials) to verify Railway host/port in deploy logs.
 */
@Component
public class DatasourceStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(DatasourceStartupLogger.class);

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @EventListener(ApplicationReadyEvent.class)
    public void logTarget() {
        log.info("Datasource user={} url={}", username, jdbcUrl);
    }
}

package seatReservationSystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Explains "why localhost" in deploy logs when Railway variables are missing or still contain ${{ }}.
 */
public class RailwayDatasourceValidator
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(RailwayDatasourceValidator.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        if (!isRailway(env)) {
            return;
        }

        String mysqlHost = env.getProperty("MYSQLHOST");
        String jdbcUrl = env.getProperty("spring.datasource.url");
        String dbUser = env.getProperty("spring.datasource.username");
        String springDsUser = env.getProperty("SPRING_DATASOURCE_USERNAME");

        log.info("Railway env: MYSQLHOST={}", display(mysqlHost));
        log.info("Railway env: MYSQLUSER={}", display(env.getProperty("MYSQLUSER")));
        log.info("Railway env: SPRING_DATASOURCE_USERNAME={}", display(springDsUser));
        log.info("Railway env: resolved spring.datasource.username={}", display(dbUser));
        log.info("Railway env: resolved spring.datasource.url={}", jdbcUrl);

        if ("spring".equals(dbUser)) {
            throw new IllegalStateException(
                    "MySQL user is 'spring' (container OS user), not your DB user. "
                            + "On the API service: DELETE variable SPRING_DATASOURCE_USERNAME if present. "
                            + "Set MYSQLUSER=root and reference MYSQLPASSWORD from the MySQL service."
            );
        }

        if (isMissingOrTemplate(mysqlHost)) {
            throw new IllegalStateException(
                    "MYSQLHOST is not set to a real hostname on this service (value="
                            + display(mysqlHost)
                            + "). Spring then defaults to localhost in application.properties. "
                            + "On the API/Docker service (not MySQL), use Variables → Add variable "
                            + "REFERENCE from the MySQL service for MYSQLHOST, MYSQLPORT, MYSQLUSER, "
                            + "MYSQLPASSWORD, MYSQLDATABASE. Do not paste literal ${{RAILWAY_PRIVATE_DOMAIN}}; "
                            + "the running container must see e.g. xxxx.railway.internal"
            );
        }

        if (jdbcUrl != null && (jdbcUrl.contains("localhost") || jdbcUrl.contains("127.0.0.1"))) {
            throw new IllegalStateException(
                    "JDBC URL still uses localhost on Railway. Link MySQL reference variables "
                            + "to this service and redeploy."
            );
        }
    }

    private static boolean isRailway(ConfigurableEnvironment env) {
        return hasText(env.getProperty("RAILWAY_ENVIRONMENT"))
                || hasText(env.getProperty("RAILWAY_PROJECT_ID"));
    }

    private static boolean isMissingOrTemplate(String value) {
        if (!hasText(value)) {
            return true;
        }
        return value.contains("${{") || value.contains("${");
    }

    private static String display(String value) {
        return value == null ? "<unset>" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package seatReservationSystem.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Railway MySQL: {@code MYSQL_URL} (mysql://...) or {@code MYSQLHOST} + friends.
 * Skips {@code MYSQL_URL} when Railway reference syntax was not resolved ({@code ${{...}}}).
 */
public class RailwayDataSourceEnvironmentPostProcessor
        implements EnvironmentPostProcessor {

    private static final String SOURCE = "railwayDatasource";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        if (hasText(environment.getProperty("SPRING_DATASOURCE_URL"))) {
            return;
        }

        Map<String, Object> props = new LinkedHashMap<>();

        if (applyFromHostVars(environment, props)) {
            addPropertySource(environment, props);
            return;
        }

        if (applyFromMysqlUrl(environment, props)) {
            addPropertySource(environment, props);
        }
    }

    private static boolean applyFromHostVars(
            ConfigurableEnvironment environment,
            Map<String, Object> props
    ) {
        String host = environment.getProperty("MYSQLHOST");
        if (!hasText(host) || isUnresolvedReference(host)) {
            return false;
        }

        String port = environment.getProperty("MYSQLPORT", "3306");
        String database = firstNonBlank(
                environment.getProperty("MYSQLDATABASE"),
                environment.getProperty("MYSQL_DATABASE"),
                "railway"
        );

        props.put(
                "spring.datasource.url",
                jdbcUrl(host, port, database, sslFlag(environment, host))
        );

        if (!hasText(environment.getProperty("SPRING_DATASOURCE_USERNAME"))) {
            String user = environment.getProperty("MYSQLUSER", "root");
            if (!isUnresolvedReference(user)) {
                props.put("spring.datasource.username", user);
            }
        }

        if (!hasText(environment.getProperty("SPRING_DATASOURCE_PASSWORD"))) {
            String password = firstNonBlank(
                    environment.getProperty("MYSQLPASSWORD"),
                    environment.getProperty("MYSQL_ROOT_PASSWORD")
            );
            if (password != null && !isUnresolvedReference(password)) {
                props.put("spring.datasource.password", password);
            }
        }

        return true;
    }

    private static boolean applyFromMysqlUrl(
            ConfigurableEnvironment environment,
            Map<String, Object> props
    ) {
        String mysqlUrl = firstNonBlank(
                environment.getProperty("MYSQL_URL"),
                environment.getProperty("MYSQL_PRIVATE_URL"),
                environment.getProperty("DATABASE_URL")
        );

        if (!hasText(mysqlUrl)
                || !mysqlUrl.startsWith("mysql://")
                || isUnresolvedReference(mysqlUrl)) {
            return false;
        }

        try {
            Parsed parsed = parseMysqlUrl(mysqlUrl);
            props.put(
                    "spring.datasource.url",
                    jdbcUrl(
                            parsed.host,
                            parsed.port,
                            parsed.database,
                            sslFlag(environment, parsed.host)
                    )
            );
            if (!hasText(environment.getProperty("SPRING_DATASOURCE_USERNAME"))) {
                props.put("spring.datasource.username", parsed.username);
            }
            if (!hasText(environment.getProperty("SPRING_DATASOURCE_PASSWORD"))) {
                props.put("spring.datasource.password", parsed.password);
            }
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void addPropertySource(
            ConfigurableEnvironment environment,
            Map<String, Object> props
    ) {
        environment.getPropertySources().addFirst(
                new MapPropertySource(SOURCE, props)
        );
    }

    private static String sslFlag(
            ConfigurableEnvironment environment,
            String host
    ) {
        String configured = environment.getProperty("MYSQL_USE_SSL");
        if (hasText(configured)) {
            return configured;
        }
        // Railway private network (.railway.internal) — SSL usually off
        if (host != null && host.contains("railway.internal")) {
            return "false";
        }
        return "true";
    }

    private static String jdbcUrl(
            String host,
            String port,
            String database,
            String useSsl
    ) {
        return "jdbc:mysql://"
                + host
                + ":"
                + port
                + "/"
                + database
                + "?useSSL="
                + useSsl
                + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static Parsed parseMysqlUrl(String mysqlUrl) {
        String remainder = mysqlUrl.substring("mysql://".length());
        int at = remainder.lastIndexOf('@');
        if (at < 0) {
            throw new IllegalArgumentException("Invalid MYSQL_URL format");
        }

        String userInfo = remainder.substring(0, at);
        String hostPart = remainder.substring(at + 1);

        String username = userInfo;
        String password = "";
        int colon = userInfo.indexOf(':');
        if (colon >= 0) {
            username = decode(userInfo.substring(0, colon));
            password = decode(userInfo.substring(colon + 1));
        } else {
            username = decode(username);
        }

        int slash = hostPart.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid MYSQL_URL format: missing database");
        }

        String hostPort = hostPart.substring(0, slash);
        String database = hostPart.substring(slash + 1);
        int query = database.indexOf('?');
        if (query >= 0) {
            database = database.substring(0, query);
        }

        String host = hostPort;
        String port = "3306";
        int portSep = hostPort.lastIndexOf(':');
        if (portSep >= 0) {
            host = hostPort.substring(0, portSep);
            port = hostPort.substring(portSep + 1);
        }

        return new Parsed(host, port, database, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isUnresolvedReference(String value) {
        return value.contains("${{") || value.contains("${");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (hasText(v) && !isUnresolvedReference(v)) {
                return v;
            }
        }
        return null;
    }

    private record Parsed(
            String host,
            String port,
            String database,
            String username,
            String password
    ) {
    }
}

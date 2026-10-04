//package seatReservationSystem.config;
//
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.env.EnvironmentPostProcessor;
//import org.springframework.core.env.ConfigurableEnvironment;
//import org.springframework.core.env.MapPropertySource;
//
//import java.net.URLDecoder;
//import java.nio.charset.StandardCharsets;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
///**
// * On Railway, builds {@code spring.datasource.*} from {@code MYSQL_URL} or discrete {@code MYSQL*} vars
// * so the app never silently falls back to localhost.
// */
//public class RailwayMysqlPropertiesPostProcessor implements EnvironmentPostProcessor {
//
//    private static final String SOURCE = "railwayDatasource";
//
//    @Override
//    public void postProcessEnvironment(
//            ConfigurableEnvironment environment,
//            SpringApplication application
//    ) {
//        if (hasText(environment.getProperty("SPRING_DATASOURCE_URL"))) {
//            logResolved(environment);
//            return;
//        }
//
//        if (!isRailway(environment)) {
//            return;
//        }
//
//        Map<String, Object> props = new LinkedHashMap<>();
//
//        String mysqlUrl = firstNonBlank(
//                environment.getProperty("MYSQL_URL"),
//                environment.getProperty("MYSQL_PUBLIC_URL")
//        );
//        if (isUsableMysqlUrl(mysqlUrl)) {
//            fillFromMysqlUrl(environment, props, mysqlUrl);
//            install(environment, props, "MYSQL_URL");
//            return;
//        }
//
//        if (fillFromDiscreteVars(environment, props)) {
//            install(environment, props, "MYSQLHOST/MYSQLPORT");
//            return;
//        }
//
//        throw new IllegalStateException(
//                "Railway: database not configured on this service. "
//                        + "On the APP service (not MySQL only), add a variable REFERENCE: "
//                        + "MYSQL_URL (best), or MYSQLHOST + MYSQLPORT + MYSQLUSER + MYSQLPASSWORD + MYSQLDATABASE. "
//                        + "Values must be resolved (no ${{...}} in MYSQL_URL)."
//        );
//    }
//
//    private static void fillFromMysqlUrl(
//            ConfigurableEnvironment environment,
//            Map<String, Object> props,
//            String mysqlUrl
//    ) {
//        Parsed parsed = parseMysqlUrl(mysqlUrl);
//        String ssl = environment.getProperty("MYSQL_USE_SSL", "false");
//        putJdbc(props, parsed.host, parsed.port, parsed.database, ssl);
//        props.put("spring.datasource.username", parsed.username);
//        props.put("spring.datasource.password", parsed.password);
//    }
//
//    private static boolean fillFromDiscreteVars(
//            ConfigurableEnvironment environment,
//            Map<String, Object> props
//    ) {
//        String host = firstNonBlank(
//                environment.getProperty("MYSQLHOST"),
//                environment.getProperty("MYSQL_HOST")
//        );
//        String port = firstNonBlank(
//                environment.getProperty("MYSQLPORT"),
//                environment.getProperty("MYSQL_PORT")
//        );
//        if (!hasText(host) || host.contains("${") || !hasText(port) || port.contains("${")) {
//            return false;
//        }
//
//        if (host.contains("proxy.rlwy.net") && "3306".equals(port)) {
//            throw new IllegalStateException(
//                    "Public proxy host with port 3306 is wrong — use MYSQLPORT from TCP proxy (e.g. 57659)."
//            );
//        }
//
//        String database = firstNonBlank(
//                environment.getProperty("MYSQLDATABASE"),
//                environment.getProperty("MYSQL_DATABASE"),
//                "railway"
//        );
//        String ssl = environment.getProperty("MYSQL_USE_SSL", "false");
//        putJdbc(props, host, port, database, ssl);
//
//        String user = environment.getProperty("MYSQLUSER", "root");
//        if (!hasText(user) || user.contains("${")) {
//            return false;
//        }
//        props.put("spring.datasource.username", user);
//
//        String password = firstNonBlank(
//                environment.getProperty("MYSQLPASSWORD"),
//                environment.getProperty("MYSQL_ROOT_PASSWORD")
//        );
//        if (password == null || password.contains("${")) {
//            return false;
//        }
//        props.put("spring.datasource.password", password);
//        return true;
//    }
//
//    private static void putJdbc(
//            Map<String, Object> props,
//            String host,
//            String port,
//            String database,
//            String ssl
//    ) {
//        props.put(
//                "spring.datasource.url",
//                "jdbc:mysql://"
//                        + host
//                        + ":"
//                        + port
//                        + "/"
//                        + database
//                        + "?useSSL="
//                        + ssl
//                        + "&allowPublicKeyRetrieval=true&serverTimezone=UTC"
//                        + "&connectTimeout=30000&socketTimeout=30000"
//        );
//    }
//
//    private static void install(
//            ConfigurableEnvironment environment,
//            Map<String, Object> props,
//            String source
//    ) {
//        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, props));
//        String url = (String) props.get("spring.datasource.url");
//        System.out.println("[railway] datasource from " + source + " → " + url);
//        validateRailway(environment);
//    }
//
//    private static void logResolved(ConfigurableEnvironment environment) {
//        if (!isRailway(environment)) {
//            return;
//        }
//        System.out.println(
//                "[railway] using SPRING_DATASOURCE_URL → "
//                        + environment.getProperty("spring.datasource.url")
//        );
//    }
//
//    private static void validateRailway(ConfigurableEnvironment environment) {
//        String jdbcUrl = environment.getProperty("spring.datasource.url");
//        String user = environment.getProperty("spring.datasource.username");
//        System.out.println("[railway] jdbc user=" + user);
//        if ("spring".equals(user)) {
//            throw new IllegalStateException(
//                    "Delete SPRING_DATASOURCE_USERNAME on the app service; use MYSQLUSER=root."
//            );
//        }
//        if (jdbcUrl != null && (jdbcUrl.contains("localhost") || jdbcUrl.contains("127.0.0.1"))) {
//            throw new IllegalStateException(
//                    "JDBC URL still uses localhost — fix MySQL variable references on the app service."
//            );
//        }
//    }
//
//    private static boolean isRailway(ConfigurableEnvironment environment) {
//        return hasText(environment.getProperty("RAILWAY_ENVIRONMENT"))
//                || hasText(environment.getProperty("RAILWAY_PROJECT_ID"));
//    }
//
//    private static boolean isUsableMysqlUrl(String mysqlUrl) {
//        return hasText(mysqlUrl)
//                && mysqlUrl.startsWith("mysql://")
//                && !mysqlUrl.contains("${{")
//                && !mysqlUrl.contains("${");
//    }
//
//    private static Parsed parseMysqlUrl(String mysqlUrl) {
//        String remainder = mysqlUrl.substring("mysql://".length());
//        int at = remainder.lastIndexOf('@');
//        if (at < 0) {
//            throw new IllegalArgumentException("Invalid MYSQL_URL");
//        }
//
//        String userInfo = remainder.substring(0, at);
//        String hostPart = remainder.substring(at + 1);
//
//        String username = userInfo;
//        String password = "";
//        int colon = userInfo.indexOf(':');
//        if (colon >= 0) {
//            username = decode(userInfo.substring(0, colon));
//            password = decode(userInfo.substring(colon + 1));
//        } else {
//            username = decode(username);
//        }
//
//        int slash = hostPart.indexOf('/');
//        if (slash < 0) {
//            throw new IllegalArgumentException("Invalid MYSQL_URL: missing database");
//        }
//
//        String hostPort = hostPart.substring(0, slash);
//        String database = hostPart.substring(slash + 1);
//        int query = database.indexOf('?');
//        if (query >= 0) {
//            database = database.substring(0, query);
//        }
//
//        String host = hostPort;
//        String port = "3306";
//        int portSep = hostPort.lastIndexOf(':');
//        if (portSep >= 0) {
//            host = hostPort.substring(0, portSep);
//            port = hostPort.substring(portSep + 1);
//        }
//
//        return new Parsed(host, port, database, username, password);
//    }
//
//    private static String decode(String value) {
//        return URLDecoder.decode(value, StandardCharsets.UTF_8);
//    }
//
//    private static boolean hasText(String value) {
//        return value != null && !value.isBlank();
//    }
//
//    private static String firstNonBlank(String... values) {
//        for (String v : values) {
//            if (hasText(v) && !v.contains("${")) {
//                return v;
//            }
//        }
//        return null;
//    }
//
//    private record Parsed(
//            String host,
//            String port,
//            String database,
//            String username,
//            String password
//    ) {
//    }
//}

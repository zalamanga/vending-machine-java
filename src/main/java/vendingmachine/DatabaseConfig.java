package vendingmachine;

import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;

public final class DatabaseConfig {
  private DatabaseConfig() {}

  public static ConnectionFactory fromEnvironment(Map<String, String> env) {
    String url = required(env, "DB_URL");
    String user = required(env, "DB_USER");
    String password = required(env, "DB_PASSWORD");
    if (!url.startsWith("jdbc:mysql:")) {
      throw new IllegalArgumentException("DB_URL harus berupa URL JDBC MySQL.");
    }
    Properties properties = new Properties();
    properties.setProperty("user", user);
    properties.setProperty("password", password);
    properties.setProperty("connectTimeout", "5000");
    properties.setProperty("socketTimeout", "15000");
    return () -> DriverManager.getConnection(url, properties);
  }

  private static String required(Map<String, String> env, String key) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Atur " + key + " pada environment proses Java.");
    }
    return value;
  }
}

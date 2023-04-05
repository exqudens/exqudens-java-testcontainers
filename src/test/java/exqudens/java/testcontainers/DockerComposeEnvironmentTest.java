package exqudens.java.testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class DockerComposeEnvironmentTest implements Containers, EnvironmentVariables {

  private static final String TEST_TABLE = "test_table";
  private static final String ID = "id";
  private static final String NAME = "name";

  final DockerComposeEnvironment environment;

  String keycloakDbHost;
  Integer keycloakDbPort;
  String keycloakDbName;
  String keycloakDbUser;
  String keycloakDbPassword;

  List<Map<String, Object>> startupRows;

  public DockerComposeEnvironmentTest() {
    environment = DockerComposeEnvironment.getInstance(
      getClass(),
      "/".concat(getClass().getPackage().getName().replace('.', '/')).concat("/").concat("docker-compose.yml"),
      null,
      log,
      Map.of(KEYCLOAK_SERVICE, TestContainers.waitHttp("/auth/", 8080, 200, Duration.ofMinutes(3))),
      Map.of(
        KEYCLOAK_DB, this::keycloakDbRun,
        KEYCLOAK_SERVICE, this::keycloakRun
      ),
      KEYCLOAK_DB,
      KEYCLOAK_SERVICE
    );
    environment.start();
  }

  @Test
  public void test1() throws Exception {
    final Optional<Object> table = Stream
      .of(startupRows)
      .filter(Objects::nonNull)
      .flatMap(List::stream)
      .map(m -> m.get("tablename"))
      .filter(Objects::nonNull)
      .filter(TEST_TABLE::equals)
      .findAny();

    assertTrue(table.isPresent());

    final int createTwoRecordsResult = createTwoRecords();

    assertEquals(2, createTwoRecordsResult);

    final List<Map<String, Object>> expectedRows = List.of(
      Map.of(ID, 1, NAME, "name_1"),
      Map.of(ID, 2, NAME, "name_2")
    );

    assertTrue(expectedRows.containsAll(getTwoRecords()));
  }

  private static HikariDataSource createHikariDataSource(
    String host,
    Integer port,
    String dbname,
    String username,
    String password
  ) {
    String url = "jdbc:postgresql://"
      .concat(host)
      .concat(":")
      .concat(port.toString())
      .concat("/")
      .concat(dbname);
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    config.setMaximumPoolSize(1);
    HikariDataSource dataSource = new HikariDataSource(config);
    return dataSource;
  }

  private static int executeUpdate(
    String host,
    Integer port,
    String dbname,
    String username,
    String password,
    String sql
  ) {
    try (
      HikariDataSource d = createHikariDataSource(host, port, dbname, username, password);
      Connection c = d.getConnection();
      Statement s = c.createStatement()
    ) {
      return s.executeUpdate(sql);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static List<Map<String, Object>> executeQuery(
    String host,
    Integer port,
    String dbname,
    String username,
    String password,
    String sql
  ) {
    try (
      HikariDataSource d = createHikariDataSource(host, port, dbname, username, password);
      Connection c = d.getConnection();
      Statement s = c.createStatement();
      ResultSet rs = s.executeQuery(sql)
    ) {
      ResultSetMetaData md = rs.getMetaData();
      List<Map<String, Object>> rows = new ArrayList<>();
      while (rs.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
          row.put(md.getColumnName(i), rs.getObject(i));
        }
        rows.add(row);
      }
      return rows;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private List<Map<String, Object>> getTwoRecords() {
    String sql = "select * from " + TEST_TABLE;
    return executeQuery(
      keycloakDbHost,
      keycloakDbPort,
      keycloakDbName,
      keycloakDbUser,
      keycloakDbPassword,
      sql
    );
  }

  private int createTwoRecords() {
    String sql = "insert into " + TEST_TABLE + "(" + ID + ", " + NAME + ") values(1, 'name_1'), (2, 'name_2')";

    return executeUpdate(
      keycloakDbHost,
      keycloakDbPort,
      keycloakDbName,
      keycloakDbUser,
      keycloakDbPassword,
      sql
    );
  }

  private void keycloakRun() {
    String sql = "select * from pg_catalog.pg_tables";

    startupRows = executeQuery(
      keycloakDbHost,
      keycloakDbPort,
      keycloakDbName,
      keycloakDbUser,
      keycloakDbPassword,
      sql
    );
  }

  private void keycloakDbRun() {
    keycloakDbHost = environment.getHost(KEYCLOAK_DB);
    keycloakDbPort = environment.getPort(KEYCLOAK_DB);
    keycloakDbName = environment.getEnvMap(KEYCLOAK_DB).get(POSTGRES_DB);
    keycloakDbUser = environment.getEnvMap(KEYCLOAK_DB).get(POSTGRES_USER);
    keycloakDbPassword = environment.getEnvMap(KEYCLOAK_DB).get(POSTGRES_PASSWORD);

    String sql = "create table " + TEST_TABLE + "(id serial PRIMARY KEY, name VARCHAR(255))";

    final int executeUpdateResult = executeUpdate(
      keycloakDbHost,
      keycloakDbPort,
      keycloakDbName,
      keycloakDbUser,
      keycloakDbPassword,
      sql
    );

    log.info("'executeUpdateResult': {}", executeUpdateResult);
  }

}

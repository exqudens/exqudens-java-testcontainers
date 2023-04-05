package exqudens.java.testcontainers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.lifecycle.Startable;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class TestContainers {

  private static final String SERVICES = "services";
  private static final String IMAGE = "image";
  private static final String EXPOSE = "expose";
  private static final String PORTS = "ports";
  private static final String ENVIRONMENT = "environment";
  private static final String DEPENDS_ON = "depends_on";

  public static WaitStrategy waitHttp() {
    return waitHttp("/actuator/health", 8080, 200, Duration.ofMinutes(3));
  }

  public static WaitStrategy waitHttp(String path, int port, int code, Duration startupTimeout) {
    return Wait.forHttp(path).forPort(port).forStatusCode(code).withStartupTimeout(startupTimeout);
  }

  public static WaitStrategy waitLogMessage(String regex) {
    return waitLogMessage(regex, 1, Duration.ofMinutes(3));
  }

  public static WaitStrategy waitLogMessage(String regex, int times, Duration startupTimeout) {
    return Wait.forLogMessage(regex, times).withStartupTimeout(startupTimeout);
  }

  public static WaitStrategy waitListeningPort() {
    return waitListeningPort(Duration.ofMinutes(3));
  }

  public static WaitStrategy waitListeningPort(Duration startupTimeout) {
    return Wait.forListeningPort().withStartupTimeout(startupTimeout);
  }

  static String info(GenericContainer<?> container) {
    String threeDashes = "-".repeat(3);
    String header = String.join(" ", threeDashes, container.getNetworkAliases().get(1), threeDashes);
    String footer = "-".repeat(header.length());
    return String.join(
        System.lineSeparator(),
        header,
        "networkAliases: " + container.getNetworkAliases().toString(),
        "portBindings: " + container.getPortBindings().toString(),
        footer
    );
  }

  static Network network() {
    return Network.newNetwork();
  }

  static Map<String, FixedHostPortGenericContainer<?>> dockerCompose(
      Map<String, Object> map,
      Network network,
      Logger logger,
      Duration startupTimeout,
      Map<String, WaitStrategy> waitStrategyMap
  ) {
    Map<String, FixedHostPortGenericContainer<?>> dockerComposeMap = new HashMap<>();

    Objects.requireNonNull(map);

    Object servicesObject = map.get(SERVICES);

    Objects.requireNonNull(servicesObject);

    Map<String, Object> servicesMap = toMap(String.class, Object.class, servicesObject);
    Map<String, Set<String>> dependsOnMap = new HashMap<>();

    for (Entry<String, Object> entry : servicesMap.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      Objects.requireNonNull(key);
      Objects.requireNonNull(value);

      Map<String, Object> containerMap = toMap(String.class, Object.class, value);

      WaitStrategy waitStrategy = waitStrategyMap != null ? waitStrategyMap.get(key) : null;

      FixedHostPortGenericContainer<?> container = toDockerComposeContainer(
          key,
          containerMap,
          network,
          logger,
          waitStrategy != null ? waitStrategy : waitListeningPort(startupTimeout)
      );

      dockerComposeMap.put(key, container);

      Object dependsOnObject = containerMap.get(DEPENDS_ON);

      if (dependsOnObject != null) {
        List<String> list = toList(String.class, dependsOnObject);
        dependsOnMap.put(key, new HashSet<>(list));
      }
    }

    if (!dependsOnMap.isEmpty()) {

      checkLoopedDependencies(dependsOnMap);

      for (Entry<String, Set<String>> entry1 : dependsOnMap.entrySet()) {
        String key = entry1.getKey();
        Set<String> value = entry1.getValue();
        List<Startable> dependsOn = dockerComposeMap
            .entrySet()
            .stream()
            .filter(entry2 -> value.contains(entry2.getKey()))
            .map(Entry::getValue)
            .collect(Collectors.toList());
        dockerComposeMap.get(key).dependsOn(dependsOn);
      }
    }

    return dockerComposeMap;
  }

  static FixedHostPortGenericContainer<?> dockerComposeContainer(
      String image,
      Network network,
      String alias,
      Logger logger,
      List<Integer[]> ports,
      Map<String, String> environment,
      WaitStrategy waitingFor,
      List<Startable> dependsOn
  ) {
    Objects.requireNonNull(image);
    FixedHostPortGenericContainer<?> container = new FixedHostPortGenericContainer<>(image);
    if (network != null) {
      container = container.withNetwork(network);
    }
    if (alias != null) {
      container = container.withNetworkAliases(alias);
    }
    if (logger != null) {
      container = container.withLogConsumer(logConsumer(logger, alias));
    }
    if (ports != null) {
      for (Integer[] binding : ports) {
        if (binding[0] != null) {
          container = container.withFixedExposedPort(binding[0], binding[1]);
          container.addExposedPort(binding[1]);
        } else {
          container.addExposedPort(binding[1]);
        }
      }
    }
    if (environment != null) {
      container = container.withEnv(environment);
    }
    if (waitingFor != null) {
      container = container.waitingFor(waitingFor);
    }
    if (dependsOn != null) {
      container = container.dependsOn(dependsOn);
    }
    return container;
  }

  static GenericContainer<?> container(
      String image,
      Network network,
      String alias,
      Logger logger,
      List<Integer> ports,
      Map<String, String> environment,
      WaitStrategy waitingFor,
      List<Startable> dependsOn
  ) {
    Objects.requireNonNull(image);
    GenericContainer<?> container = new GenericContainer<>(image);
    if (network != null) {
      container = container.withNetwork(network);
    }
    if (alias != null) {
      container = container.withNetworkAliases(alias);
    }
    if (logger != null) {
      container = container.withLogConsumer(logConsumer(logger, alias));
    }
    if (ports != null) {
      container = container.withExposedPorts(ports.toArray(new Integer[0]));
    }
    if (environment != null) {
      container = container.withEnv(environment);
    }
    if (waitingFor != null) {
      container = container.waitingFor(waitingFor);
    }
    if (dependsOn != null) {
      container = container.dependsOn(dependsOn);
    }
    return container;
  }

  static Consumer<OutputFrame> logConsumer(Logger logger, String alias) {
    return logConsumer(logger, SERVICES, alias);
  }

  static Consumer<OutputFrame> logConsumer(Logger logger, String prefix, String alias) {
    String suffix = alias.replace('-', '.');
    String name = String.join(".", logger.getName(), prefix, suffix);
    return new Slf4jLogConsumer(LoggerFactory.getLogger(name));
  }

  private static <K, V> Map<K, V> toMap(Class<K> keyType, Class<V> valueType, Object object) {
    Objects.requireNonNull(keyType);
    Objects.requireNonNull(valueType);
    Objects.requireNonNull(object);
    Map<?, ?> genericMap = (Map<?, ?>) object;
    return genericMap
        .entrySet()
        .stream()
        .map(entry -> Map.entry(keyType.cast(entry.getKey()), valueType.cast(entry.getValue())))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private static <T> List<T> toList(Class<T> type, Object object) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(object);
    List<?> genericList = (List<?>) object;
    return genericList
        .stream()
        .map(type::cast)
        .collect(Collectors.toList());
  }

  private static FixedHostPortGenericContainer<?> toDockerComposeContainer(
      String key,
      Map<String, Object> map,
      Network network,
      Logger logger,
      WaitStrategy waitStrategy
  ) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(map);
    Objects.requireNonNull(network);
    Objects.requireNonNull(logger);
    Objects.requireNonNull(waitStrategy);

    Object imageObject = map.get(IMAGE);
    Object exposeObject = map.get(EXPOSE);
    Object portsObject = map.get(PORTS);
    Object environmentObject = map.get(ENVIRONMENT);

    Objects.requireNonNull(imageObject, key.concat(" ").concat("'imageObject' is null"));

    if (exposeObject == null && portsObject == null) {
      throw new IllegalStateException(key.concat(" ").concat("'exposeObject' and 'portsObject' is null"));
    }

    String image = map.get(IMAGE).toString();
    String alias = key;
    List<Integer[]> ports = toPorts(exposeObject, portsObject);
    Map<String, String> environment = environmentObject != null ? toEnvironment(environmentObject) : null;
    WaitStrategy waitingFor = waitStrategy;
    List<Startable> dependsOn = null;

    return dockerComposeContainer(
        image,
        network,
        alias,
        logger,
        ports,
        environment,
        waitingFor,
        dependsOn
    );
  }

  private static List<Integer[]> toPorts(Object exposeObject, Object portsObject) {
    if (exposeObject == null && portsObject == null) {
      throw new IllegalStateException("'exposeObject' and 'portsObject' is null");
    }

    Set<Integer> expose = Set.of();
    if (exposeObject != null) {
      if (exposeObject instanceof List) {
        List<?> genericList = (List<?>) exposeObject;
        if (!genericList.isEmpty()) {
          Object element = genericList.get(0);
          if (element instanceof String) {
            List<String> list = toList(String.class, exposeObject);
            expose = list
                .stream()
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
          } else if (element instanceof Integer) {
            expose = new HashSet<>(toList(Integer.class, exposeObject));
          } else {
            throw new IllegalArgumentException("Unsupported type of 'exposeObject'.'element': " + element.getClass());
          }
        }
      } else {
        throw new IllegalArgumentException("Unsupported type of 'exposeObject': " + exposeObject.getClass());
      }
    }

    Map<Integer, Integer> ports = Map.of();
    if (portsObject != null) {
      if (portsObject instanceof List) {
        List<?> genericList = (List<?>) portsObject;
        if (!genericList.isEmpty()) {
          Object element = genericList.get(0);
          if (element instanceof String) {
            List<String> list = toList(String.class, portsObject);
            ports = list
                .stream()
                .map(s -> split(s, ":"))
                .map(l -> Map.entry(l.get(0), l.get(1)))
                .map(entry -> Map.entry(Integer.parseInt(entry.getKey()), Integer.parseInt(entry.getValue())))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
          } else {
            throw new IllegalArgumentException(
                "Unsupported type of 'portsObject'.'element': " + element.getClass());
          }
        }
      } else {
        throw new IllegalArgumentException("Unsupported type of 'portsObject': " + portsObject.getClass());
      }
    }

    List<Integer[]> result = ports
      .entrySet()
      .stream()
      .map(entry -> new Integer[] {entry.getKey(), entry.getValue()})
      .collect(Collectors.toList());
    Set<Integer> filter = new HashSet<>(ports.values());
    expose
      .stream()
      .distinct()
      .filter(Predicate.not(filter::contains))
      .map(i -> new Integer[] { null, i })
      .collect(Collectors.toCollection(() -> result));

    return result;
  }

  private static Map<String, String> toEnvironment(Object object) {
    Objects.requireNonNull(object);
    if (object instanceof Map) {
      Map<String, Object> map = toMap(String.class, Object.class, object);
      return map
          .entrySet()
          .stream()
          .map(entry -> Map.entry(entry.getKey(), entry.getValue().toString()))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    } else if (object instanceof List) {
      List<String> list = toList(String.class, object);
      return list
          .stream()
          .map(s -> split(s, "="))
          .map(l -> Map.entry(l.get(0), l.get(1)))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    } else {
      throw new IllegalArgumentException("Unsupported type: " + object.getClass());
    }
  }

  private static void checkLoopedDependencies(Map<String, Set<String>> dependsOnMap) {
    Set<Entry<String, String>> dependencies = dependsOnMap
      .entrySet()
      .stream()
      .flatMap(e -> e.getValue().stream().map(s -> Map.entry(e.getKey(), s)))
      .collect(Collectors.toSet());
    for (Entry<String, String> dependency1 : dependencies) {
      for (Entry<String, String> dependency2 : dependencies) {
        boolean looped = false;
        if (
          dependency1.getKey().equals(dependency2.getValue())
            && dependency1.getValue().equals(dependency2.getKey())
        ) {
          looped = true;
        }
        if (looped) {
          String message = "Looped dependencies: ("
            .concat(dependency1.getKey())
            .concat(" to ")
            .concat(dependency1.getValue())
            .concat(") and (")
            .concat(dependency2.getKey())
            .concat(" to ")
            .concat(dependency2.getValue())
            .concat(")");
          throw new IllegalStateException(message);
        }
      }
    }
  }

  private static List<String> split(String string, String delimiter) {
    Objects.requireNonNull(string);
    Objects.requireNonNull(delimiter);
    return Collections
        .list(new StringTokenizer(string, delimiter))
        .stream()
        .map(Object::toString)
        .collect(Collectors.toList());
  }

}

package exqudens.java.testcontainers;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter(AccessLevel.PROTECTED)
@Slf4j
public class DockerComposeEnvironment {

  private static DockerComposeEnvironment INSTANCE;

  @SneakyThrows
  public static DockerComposeEnvironment getInstance(
    @NonNull Path path,
    Network network,
    Logger logger,
    Map<String, WaitStrategy> waitStrategyMap,
    Map<String, Runnable> afterStartRunnableMap,
    @NonNull String... startKeys
  ) {
    return getInstance(Files.readString(path), network, logger, waitStrategyMap, afterStartRunnableMap, startKeys);
  }

  @SneakyThrows
  public static DockerComposeEnvironment getInstance(
    @NonNull Class<?> resourceClass,
    @NonNull String resource,
    Network network,
    Logger logger,
    Map<String, WaitStrategy> waitStrategyMap,
    Map<String, Runnable> afterStartRunnableMap,
    @NonNull String... startKeys
  ) {
    InputStream inputStream = null;
    try {
      inputStream = resourceClass.getResourceAsStream(resource);
      Objects.requireNonNull(inputStream, "'inputStream' is null 'resource' ".concat(resource));
      byte[] bytes = inputStream.readAllBytes();
      String content = new String(bytes, StandardCharsets.UTF_8);
      return getInstance(content, network, logger, waitStrategyMap, afterStartRunnableMap, startKeys);
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  public static DockerComposeEnvironment getInstance(
    @NonNull String content,
    Network network,
    Logger logger,
    Map<String, WaitStrategy> waitStrategyMap,
    Map<String, Runnable> afterStartRunnableMap,
    @NonNull String... startKeys
  ) {
    if (INSTANCE == null) {
      synchronized (DockerComposeEnvironment.class) {
        if (INSTANCE == null) {
          INSTANCE = new DockerComposeEnvironment(
            content,
            startKeys,
            network,
            logger,
            waitStrategyMap,
            afterStartRunnableMap
          );
        }
      }
    } else if (!INSTANCE.getContent().equals(content)) {
      throw new IllegalStateException("Already started with another docker compose content!");
    }
    return INSTANCE;
  }

  final String content;
  final String[] startKeys;
  final Network network;
  final Logger logger;
  final Map<String, Runnable> afterStartRunnableMap;
  final Map<String, FixedHostPortGenericContainer<?>> containerMap;

  boolean started;

  protected DockerComposeEnvironment(
    @NonNull String content,
    @NonNull String[] startKeys,
    Network network,
    Logger logger,
    Map<String, WaitStrategy> waitStrategyMap,
    Map<String, Runnable> afterStartRunnableMap
  ) {
    if (startKeys.length == 0) {
      throw new IllegalStateException("'startKeys' is empty!");
    }

    this.content = content;
    this.startKeys = startKeys;
    this.network = network != null ? network : TestContainers.network();
    this.logger = logger != null ? logger : log;
    this.afterStartRunnableMap = afterStartRunnableMap != null ? afterStartRunnableMap : Collections.emptyMap();
    this.containerMap = TestContainers.dockerCompose(
      new Yaml().load(this.content),
      this.network,
      this.logger,
      Duration.ofMinutes(1),
      waitStrategyMap
    );
  }

  public String getHost(String key) {
    return containerMap.get(key).getContainerIpAddress();
  }

  public Integer getPort(String key) {
    return getPort(key, null);
  }

  public Integer getPort(String key, Integer originalPort) {
    if (originalPort != null) {
      return containerMap.get(key).getMappedPort(originalPort);
    } else {
      return containerMap.get(key).getFirstMappedPort();
    }
  }

  public Map<String, String> getEnvMap(String key) {
    return new HashMap<>(containerMap.get(key).getEnvMap());
  }

  public void start() {
    if (started) {
      return;
    }

    started = true;

    logger.debug("run containers");
    containerMap
      .values()
      .stream()
      .map(TestContainers::info)
      .map(s -> System.lineSeparator().concat(s))
      .forEach(logger::debug);

    for (String startKey : startKeys) {
      containerMap.get(startKey).start();
      if (afterStartRunnableMap != null && afterStartRunnableMap.containsKey(startKey)) {
        afterStartRunnableMap.get(startKey).run();
      }
    }
  }

}

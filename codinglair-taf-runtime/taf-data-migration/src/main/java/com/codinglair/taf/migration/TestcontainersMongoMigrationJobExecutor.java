package com.codinglair.taf.migration;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;

final class TestcontainersMongoMigrationJobExecutor implements MongoMigrationJobExecutor {
  private final MigrationContainerInspector inspector;

  TestcontainersMongoMigrationJobExecutor() {
    this(_ -> {});
  }

  TestcontainersMongoMigrationJobExecutor(MigrationContainerInspector inspector) {
    this.inspector = inspector;
  }

  @Override
  public MongoMigrationJobResult execute(MongoMigrationJob job) {
    long started = System.nanoTime();
    GenericContainer<?> container = null;
    try {
      container = configured(job);
      container.start();
      inspector.inspect(container);
      String output = bounded(container.getLogs(), job.maximumOutputBytes());
      Integer exitCode = container.getContainerInfo().getState().getExitCodeLong().intValue();
      return new MongoMigrationJobResult(
          exitCode, output, Duration.ofNanos(System.nanoTime() - started));
    } catch (RuntimeException failure) {
      throw new IllegalStateException(
          "The scoped Mongo migration job failed; protected details were redacted", failure);
    } finally {
      if (container != null) container.stop();
    }
  }

  static GenericContainer<?> configured(MongoMigrationJob job) {
    var container = new GenericContainer<>(DockerImageName.parse(job.image()));
    List<String> locations = new ArrayList<>();
    for (int index = 0; index < job.locations().size(); index++) {
      String target = "/taf-migrations/" + index;
      container.withFileSystemBind(
          job.locations().get(index).toString(), target, BindMode.READ_ONLY);
      locations.add("filesystem:" + target);
    }
    container
        .withEnv("FLYWAY_URL", job.uri())
        .withNetworkMode(job.networkId())
        .withCreateContainerCmdModifier(
            command -> {
              HostConfig host = command.getHostConfig();
              if (host == null) host = new HostConfig();
              host.withReadonlyRootfs(true)
                  .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=" + job.tmpfsBytes()))
                  .withCapDrop(Capability.ALL)
                  .withSecurityOpts(List.of("no-new-privileges"))
                  .withMemory(job.memoryBytes())
                  .withNanoCPUs(job.cpuCount() * 1_000_000_000L)
                  .withPidsLimit(job.pidLimit());
              command.withHostConfig(host).withUser("1000:1000");
            })
        .withCommand(
            "-locations=" + String.join(",", locations),
            "-table=" + job.history(),
            "-sqlMigrationSuffixes=.json",
            "-outputType=json",
            "-cleanDisabled=true",
            "-baselineOnMigrate=false",
            job.operation())
        .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(job.timeout()));
    return container;
  }

  private static String bounded(String value, int maximumBytes) {
    byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length > maximumBytes)
      throw new IllegalStateException("Flyway structured output exceeded the configured bound");
    return value;
  }
}

@FunctionalInterface
interface MigrationContainerInspector {
  void inspect(GenericContainer<?> container);
}

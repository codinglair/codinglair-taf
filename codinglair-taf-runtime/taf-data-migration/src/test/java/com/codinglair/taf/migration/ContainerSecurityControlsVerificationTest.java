package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.model.Capability;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Runtime Docker-inspect proof for every mandatory migration-job control. */
@EnabledIfSystemProperty(named = "taf.migration.containers", matches = "true")
class ContainerSecurityControlsVerificationTest {
  private static final String MONGO_IMAGE =
      "mongo@sha256:05b417e0f4e6c30f4d5bf8ef47cdb846ba377b366277b925493d4c42339eb533";

  @BeforeAll
  static void requireDocker() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the migration container security contract");
  }

  @Test
  void dockerInspectProvesAllSecurityAndEgressControls() throws Exception {
    try (Network network =
            Network.builder()
                .createNetworkCmdModifier(command -> command.withInternal(true))
                .build();
        GenericContainer<?> mongo =
            new GenericContainer<>(DockerImageName.parse(MONGO_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("taf-mongo")
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))) {
      mongo.start();
      Path migrations =
          Path.of("src/test/resources/db/migration/mongodb/framework").toAbsolutePath();
      var inspected = new AtomicBoolean();
      var executor =
          new TestcontainersMongoMigrationJobExecutor(
              container -> {
                var info = container.getContainerInfo();
                var host = info.getHostConfig();
                assertThat(host.getReadonlyRootfs()).isTrue();
                assertThat(host.getCapDrop()).contains(Capability.ALL);
                assertThat(host.getSecurityOpts()).contains("no-new-privileges");
                assertThat(host.getMemory()).isPositive();
                assertThat(host.getNanoCPUs()).isPositive();
                assertThat(host.getPidsLimit()).isPositive();
                assertThat(info.getConfig().getUser()).isEqualTo("1000:1000");
                assertThat(info.getConfig().getExposedPorts()).isNullOrEmpty();
                assertThat(host.getPortBindings().getBindings()).isEmpty();
                assertThat(info.getMounts())
                    .isNotEmpty()
                    .allMatch(mount -> !Boolean.TRUE.equals(mount.getRW()));
                var networkInfo =
                    container
                        .getDockerClient()
                        .inspectNetworkCmd()
                        .withNetworkId(network.getId())
                        .exec();
                assertThat(networkInfo.getInternal()).isTrue();
                assertThat(info.getNetworkSettings().getNetworks()).hasSize(1);
                inspected.set(true);
              });
      var job =
          new MongoMigrationJob(
              MongoContainerMigrationProperties.APPROVED_IMAGE,
              network.getId(),
              "mongodb://taf-mongo:27017/taf_security",
              "taf_security_history",
              "migrate",
              List.of(migrations),
              java.time.Duration.ofMinutes(2),
              1_048_576,
              536_870_912,
              1,
              256,
              67_108_864);

      assertThat(executor.execute(job).exitCode()).isZero();
      assertThat(inspected).isTrue();
    }
  }
}

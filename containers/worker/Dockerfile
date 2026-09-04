# syntax=docker/dockerfile:1.10
ARG JAVA_BUILDER_IMAGE=eclipse-temurin:25-jdk-noble@sha256:534968c051301957beae735e7ba1db54d99ddecf08746d3b9d4f318cc132dbc3

FROM ${JAVA_BUILDER_IMAGE} AS builder
ARG VERSION
WORKDIR /source
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod 0555 mvnw
RUN test -n "${VERSION}" && \
    ./mvnw -B -ntp -Drevision="${VERSION}" -pl taf-mcp-server/taf-execution-worker -am \
    -DskipTests -DincludeScope=runtime -DoutputDirectory=target/runtime-libs \
    package dependency:copy-dependencies

FROM ${JAVA_BUILDER_IMAGE} AS runtime
ARG VERSION
ARG REVISION=unknown
ARG CREATED=1970-01-01T00:00:00Z
LABEL org.opencontainers.image.title="Codinglair TAF isolated execution worker" \
      org.opencontainers.image.description="Constrained Java 25 environment for governed TAF worker commands" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}" \
      org.opencontainers.image.source="https://github.com/codinglair/codinglair-taf" \
      org.opencontainers.image.licenses="Apache-2.0" \
      com.codinglair.taf.trust-profile="isolated-worker"
RUN groupadd --gid 10002 taf-worker && \
    useradd --uid 10002 --gid taf-worker --create-home --home-dir /home/taf-worker --shell /bin/bash taf-worker && \
    mkdir -p /opt/taf /workspace /artifacts && \
    chown -R 10002:10002 /opt/taf /workspace /artifacts /home/taf-worker
WORKDIR /workspace
COPY --from=builder --chown=10002:10002 /source/taf-mcp-server/taf-execution-worker/target/taf-execution-worker-*.jar /opt/taf/worker.jar
COPY --from=builder --chown=10002:10002 /source/taf-mcp-server/taf-execution-worker/target/runtime-libs/ /opt/taf/lib/
COPY --chown=10002:10002 containers/worker/entrypoint.sh /opt/taf/entrypoint.sh
RUN sed -i 's/\r$//' /opt/taf/entrypoint.sh && chmod 0555 /opt/taf/entrypoint.sh
USER 10002:10002
ENV HOME=/home/taf-worker \
    TAF_WORKSPACE=/workspace \
    TAF_ARTIFACTS=/artifacts \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["/opt/taf/entrypoint.sh"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD ["bash", "-c", "test \"$(id -u)\" = 10002 && test -w /workspace && test -w /artifacts && kill -0 1"]

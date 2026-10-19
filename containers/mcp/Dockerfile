# syntax=docker/dockerfile:1.10
ARG JAVA_BUILDER_IMAGE=eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:25-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5

FROM ${JAVA_BUILDER_IMAGE} AS builder
ARG VERSION
WORKDIR /source
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod 0555 mvnw
RUN test -n "${VERSION}" && \
    ./mvnw -B -ntp -Drevision="${VERSION}" \
      -pl taf-mcp-server/taf-mcp-transport-http,taf-mcp-server/taf-mcp-transport-stdio -am \
      -DskipTests -DincludeScope=runtime -DoutputDirectory=target/runtime-libs \
      package dependency:copy-dependencies

FROM ${JAVA_RUNTIME_IMAGE} AS runtime
ARG VERSION
ARG REVISION=unknown
ARG CREATED=1970-01-01T00:00:00Z
LABEL org.opencontainers.image.title="Codinglair TAF MCP Server" \
      org.opencontainers.image.description="Codinglair TAF MCP Server with STDIO and Streamable HTTP profiles" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}" \
      org.opencontainers.image.source="https://github.com/codinglair/codinglair-taf" \
      org.opencontainers.image.licenses="Apache-2.0"
RUN groupadd --gid 10001 taf && \
    useradd --uid 10001 --gid taf --no-create-home --shell /usr/sbin/nologin taf && \
    mkdir -p /opt/taf/bin /opt/taf/http/lib /opt/taf/stdio/lib /workspace /var/lib/taf-mcp /etc/taf-mcp && \
    chown -R 10001:10001 /opt/taf /workspace /var/lib/taf-mcp
COPY --from=builder --chown=10001:10001 /source/taf-mcp-server/taf-mcp-transport-http/target/taf-mcp-transport-http-${VERSION}.jar /opt/taf/http/app.jar
COPY --from=builder --chown=10001:10001 /source/taf-mcp-server/taf-mcp-transport-http/target/runtime-libs/ /opt/taf/http/lib/
COPY --from=builder --chown=10001:10001 /source/taf-mcp-server/taf-mcp-transport-stdio/target/taf-mcp-transport-stdio-${VERSION}.jar /opt/taf/stdio/app.jar
COPY --from=builder --chown=10001:10001 /source/taf-mcp-server/taf-mcp-transport-stdio/target/runtime-libs/ /opt/taf/stdio/lib/
COPY --chown=10001:10001 containers/mcp/taf-mcp /opt/taf/bin/taf-mcp
COPY --chown=10001:10001 LICENSE /opt/taf/LICENSE
COPY --chown=10001:10001 taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/catalog/v1/image-compatibility.json /opt/taf/image-compatibility.json
COPY --chown=10001:10001 taf-mcp-server/taf-mcp-contracts/src/main/resources/META-INF/taf/mcp/catalog/v1/capabilities.json /opt/taf/capabilities.json
RUN sed -i 's/\r$//' /opt/taf/bin/taf-mcp && chmod 0555 /opt/taf/bin/taf-mcp
USER 10001:10001
WORKDIR /workspace
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["/opt/taf/bin/taf-mcp"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD ["bash", "-c", "kill -0 1"]

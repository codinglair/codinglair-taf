# syntax=docker/dockerfile:1.10
ARG JAVA_BUILDER_IMAGE=eclipse-temurin:25-jdk-noble@sha256:534968c051301957beae735e7ba1db54d99ddecf08746d3b9d4f318cc132dbc3
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:25-jre-noble@sha256:b4c93a50fc67612798db73d68ca3b0ee4ebdd51736e59cca370e689b9797037e

FROM ${JAVA_BUILDER_IMAGE} AS builder
ARG VERSION
WORKDIR /source
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod 0555 mvnw
RUN test -n "${VERSION}" && \
    ./mvnw -B -ntp -Drevision="${VERSION}" -pl taf-mcp-server/taf-mcp-transport-http -am \
    -DskipTests -DincludeScope=runtime -DoutputDirectory=target/runtime-libs \
    package dependency:copy-dependencies

FROM ${JAVA_RUNTIME_IMAGE} AS runtime
ARG VERSION
ARG REVISION=unknown
ARG CREATED=1970-01-01T00:00:00Z
LABEL org.opencontainers.image.title="Codinglair TAF MCP control plane" \
      org.opencontainers.image.description="OIDC-secured TAF MCP Streamable HTTP control plane" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}" \
      org.opencontainers.image.source="https://github.com/codinglair/codinglair-taf" \
      org.opencontainers.image.licenses="Apache-2.0"
RUN groupadd --gid 10001 taf && useradd --uid 10001 --gid taf --no-create-home --shell /usr/sbin/nologin taf
WORKDIR /opt/taf
COPY --from=builder --chown=10001:10001 /source/taf-mcp-server/taf-mcp-transport-http/target/taf-mcp-transport-http-${VERSION}.jar app.jar
COPY --from=builder --chown=10001:10001 /source/taf-mcp-server/taf-mcp-transport-http/target/runtime-libs/ lib/
COPY --chown=10001:10001 containers/control-plane/entrypoint.sh ./entrypoint.sh
RUN sed -i 's/\r$//' ./entrypoint.sh && chmod 0555 ./entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["/opt/taf/entrypoint.sh"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\r\n\r\n' >&3 && grep -q '200' <&3"]

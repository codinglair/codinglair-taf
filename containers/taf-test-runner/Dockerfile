# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-noble@sha256:735baf2edc6cd6485240144a84fa4db142b9a6f47b4eb4080f31058d200f9813

ARG ALLURE_VERSION=2.36.0

LABEL org.opencontainers.image.title="Codinglair TAF test runner" \
      org.opencontainers.image.description="Non-root Java 25 test runner with pinned Allure 2 CLI" \
      org.opencontainers.image.licenses="Apache-2.0" \
      com.codinglair.taf.allure.version="${ALLURE_VERSION}"

ADD --checksum=sha256:df01c87648a3103ee4e7a6e2b6b740369aae9c8fdc7c6cb674ce5bc6dd9ad7d5 \
    https://github.com/allure-framework/allure2/releases/download/2.36.0/allure-2.36.0.zip \
    /tmp/allure.zip

RUN cd /opt \
    && jar --extract --file /tmp/allure.zip \
    && chmod 0555 "/opt/allure-${ALLURE_VERSION}/bin/allure" \
    && ln -s "/opt/allure-${ALLURE_VERSION}/bin/allure" /usr/local/bin/allure \
    && rm -f /tmp/allure.zip \
    && groupadd --gid 10001 taf \
    && useradd --uid 10001 --gid taf --no-create-home --home-dir /workspace --shell /usr/sbin/nologin taf \
    && mkdir -p /workspace \
    && chown 10001:10001 /workspace

ENV TAF_TEST_ALLURE_EXECUTABLE=/usr/local/bin/allure
WORKDIR /workspace
VOLUME ["/workspace"]
USER 10001:10001

CMD ["java", "-version"]

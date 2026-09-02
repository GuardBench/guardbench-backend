# Directly runnable Performance Runner image for the private Spot host.
FROM public.ecr.aws/amazonlinux/amazonlinux:2023

ARG K6_VERSION=0.55.0
ARG APP_REVISION=unknown

ENV APP_REVISION="${APP_REVISION}" \
    GRADLE_USER_HOME=/opt/gradle-user-home \
    PERFORMANCE_GRADLE_COMMAND=/workspace/bin/gradle \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/opt/performance-python:/workspace

LABEL org.opencontainers.image.revision="${APP_REVISION}"

RUN dnf install -y \
      python3.11 python3.11-pip postgresql15 java-21-amazon-corretto-devel \
      curl-minimal gzip tar unzip findutils \
    && dnf clean all \
    && curl --fail --location --silent --show-error \
      "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/k6-v${K6_VERSION}-linux-amd64.tar.gz" \
      | tar --extract --gzip --strip-components=1 --directory=/usr/local/bin \
        "k6-v${K6_VERSION}-linux-amd64/k6" \
    && chmod 0755 /usr/local/bin/k6

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
COPY performance performance
COPY http http
COPY bin bin

# Install Python dependencies and resolve every Gradle runtime dependency while
# building the image. Runtime migration can therefore use the image offline.
RUN python3.11 -m pip install --no-cache-dir --target /opt/performance-python \
      -r performance/requirements.txt \
    && chmod +x bin/* gradlew \
    && mkdir -p "${GRADLE_USER_HOME}" \
    && ./gradlew --no-daemon resolvePerformanceRuntime classes

ENTRYPOINT ["python3.11", "-m", "performance.runner.cli"]

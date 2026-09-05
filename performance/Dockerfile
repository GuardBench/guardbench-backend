# Directly runnable Performance Runner image for the private Spot host.
FROM public.ecr.aws/amazonlinux/amazonlinux:2023

ARG K6_VERSION=0.55.0
ARG APP_REVISION=unknown

ENV APP_REVISION="${APP_REVISION}" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/opt/performance-python:/workspace

LABEL org.opencontainers.image.revision="${APP_REVISION}"

RUN dnf install -y \
      python3.11 python3.11-pip \
      curl-minimal gzip tar \
    && dnf clean all \
    && curl --fail --location --silent --show-error \
      "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/k6-v${K6_VERSION}-linux-amd64.tar.gz" \
      | tar --extract --gzip --strip-components=1 --directory=/usr/local/bin \
        "k6-v${K6_VERSION}-linux-amd64/k6" \
    && chmod 0755 /usr/local/bin/k6

WORKDIR /workspace

COPY performance performance
COPY http http
COPY bin bin

RUN python3.11 -m pip install --no-cache-dir --target /opt/performance-python \
      -r performance/requirements.txt \
    && chmod +x bin/*

ENTRYPOINT ["python3.11", "-m", "performance.runner.cli"]

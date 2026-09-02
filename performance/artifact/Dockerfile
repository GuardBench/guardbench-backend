# The IaC runner is AL2023. Building on the same ABI keeps the packaged native
# runtimes usable in its private subnet without package downloads.
FROM public.ecr.aws/amazonlinux/amazonlinux:2023 AS build

ARG K6_VERSION=0.55.0
ARG APP_REVISION=unknown

RUN dnf install -y \
      python3.11 python3.11-pip postgresql15 java-21-amazon-corretto-devel \
      gzip tar unzip findutils \
    && dnf clean all \
    && curl --fail --location --silent --show-error \
      "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/k6-v${K6_VERSION}-linux-amd64.tar.gz" \
      | tar --extract --gzip --strip-components=1 --directory=/usr/local/bin \
        "k6-v${K6_VERSION}-linux-amd64/k6"

WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
COPY performance performance
COPY http http
COPY bin bin

RUN mkdir -p runtime/python runtime/gradle-user-home \
    && python3.11 -m pip install --no-cache-dir --target runtime/python -r performance/requirements.txt \
    && GRADLE_USER_HOME=/workspace/runtime/gradle-user-home ./gradlew --no-daemon classes resolvePerformanceRuntime

# Native command dependencies are copied next to the artifact. AL2023 keeps the
# dynamic-loader ABI stable for this runner contract; launchers below point all
# commands at this tree instead of relying on software installed on the EC2 host.
RUN set -eux; \
    mkdir -p /artifact/runtime/root/usr/lib/jvm /artifact/runtime/root/usr/lib64 /artifact/runtime/bin; \
    copy_binary() { \
      binary="$1"; \
      cp --parents "$binary" /artifact/runtime/root; \
      ldd "$binary" | awk '/=> \/|^\// { for (i = 1; i <= NF; i++) if ($i ~ /^\//) print $i }' \
        | sort -u | xargs -r -I{} cp --parents {} /artifact/runtime/root; \
    }; \
    copy_binary /usr/bin/python3.11; \
    copy_binary /usr/bin/psql; \
    copy_binary /usr/local/bin/k6; \
    cp -a /usr/lib64/python3.11 /artifact/runtime/root/usr/lib64/; \
    cp -a /usr/lib/jvm/java-21-amazon-corretto /artifact/runtime/root/usr/lib/jvm/; \
    cp -a /workspace/. /artifact/; \
    printf '{\n  "backend_revision": "%s",\n  "k6_version": "%s",\n  "runner_os": "AL2023"\n}\n' "$APP_REVISION" "$K6_VERSION" > /artifact/ARTIFACT-METADATA.json; \
    for command in python3 psql k6 java; do \
      case "$command" in \
        python3) target='/usr/bin/python3.11'; args='PYTHONHOME="$root/runtime/root/usr"';; \
        psql) target='/usr/bin/psql'; args='';; \
        k6) target='/usr/local/bin/k6'; args='';; \
        java) target='/usr/lib/jvm/java-21-amazon-corretto/bin/java'; args='';; \
      esac; \
      printf '#!/usr/bin/env bash\nset -euo pipefail\nroot="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"\nloader="$root/runtime/root/lib64/ld-linux-x86-64.so.2"\nlibpath="$root/runtime/root/lib64:$root/runtime/root/usr/lib64"\nexport LD_LIBRARY_PATH="$libpath${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"\n%s\nexec "$loader" --library-path "$libpath" "$root/runtime/root%s" "$@"\n' "$args" "$target" > "/artifact/runtime/bin/$command"; \
      chmod +x "/artifact/runtime/bin/$command"; \
    done; \
    chmod +x /artifact/bin/*

FROM scratch AS artifact
COPY --from=build /artifact /

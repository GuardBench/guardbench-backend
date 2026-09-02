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
      cp -L --parents "$binary" /artifact/runtime/root; \
      ldd "$binary" | awk '/=> \/|^\// { for (i = 1; i <= NF; i++) if ($i ~ /^\//) print $i }' \
        | sort -u | xargs -r -I{} cp -L --parents {} /artifact/runtime/root; \
    }; \
    copy_binary /usr/bin/python3.11; \
    copy_binary /usr/bin/psql; \
    copy_binary /usr/local/bin/k6; \
    cp -L /lib64/ld-linux-x86-64.so.2 /artifact/runtime/root/lib64/ld-linux-x86-64.so.2; \
    cp -a /usr/lib64/python3.11 /artifact/runtime/root/usr/lib64/; \
    jdk_home="$(readlink -f /usr/lib/jvm/java-21-amazon-corretto)"; \
    mkdir -p /artifact/runtime/root/usr/lib/jvm/java-21-amazon-corretto; \
    cp -a "$jdk_home/." /artifact/runtime/root/usr/lib/jvm/java-21-amazon-corretto/; \
    find "$jdk_home" -type f \( -name '*.so*' -o -perm -u+x \) -print0 \
      | xargs -0 -r -n1 ldd 2>/dev/null \
      | awk '/=> \// { print $3 }' | sort -u | grep -v "^$jdk_home" \
      | xargs -r -I{} cp -L --parents {} /artifact/runtime/root; \
    cp -a /workspace/. /artifact/; \
    find /artifact/runtime/root -type d ! -perm -u+w -exec chmod u+w {} +; \
    printf '{\n  "backend_revision": "%s",\n  "k6_version": "%s",\n  "runner_os": "AL2023"\n}\n' "$APP_REVISION" "$K6_VERSION" > /artifact/ARTIFACT-METADATA.json; \
    for command in python3 psql k6 java javac; do \
      case "$command" in \
        python3) target='/usr/bin/python3.11'; args='PYTHONHOME="$root/runtime/root/usr"';; \
        psql) target='/usr/bin/psql'; args='';; \
        k6) target='/usr/local/bin/k6'; args='';; \
        java) target='/usr/lib/jvm/java-21-amazon-corretto/bin/java'; args='';; \
        javac) target='/usr/lib/jvm/java-21-amazon-corretto/bin/javac'; args='';; \
      esac; \
      printf '#!/usr/bin/env bash\nset -euo pipefail\nroot="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"\nloader="$root/runtime/root/lib64/ld-linux-x86-64.so.2"\nlibpath="$root/runtime/root/lib64:$root/runtime/root/usr/lib64"\nexport LD_LIBRARY_PATH="$libpath${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"\n%s\nexec "$loader" --library-path "$libpath" "$root/runtime/root%s" "$@"\n' "$args" "$target" > "/artifact/runtime/bin/$command"; \
      chmod +x "/artifact/runtime/bin/$command"; \
    done; \
    mkdir -p /artifact/runtime/java-home/bin; \
    ln -s ../root/usr/lib/jvm/java-21-amazon-corretto/lib /artifact/runtime/java-home/lib; \
    ln -s ../root/usr/lib/jvm/java-21-amazon-corretto/include /artifact/runtime/java-home/include; \
    for command in java javac; do \
      printf '#!/usr/bin/env bash\nset -euo pipefail\nroot="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"\nexec "$root/runtime/bin/%s" "$@"\n' "$command" > "/artifact/runtime/java-home/bin/$command"; \
      chmod +x "/artifact/runtime/java-home/bin/$command"; \
    done; \
    chmod +x /artifact/bin/*

FROM scratch AS artifact
COPY --from=build /artifact /

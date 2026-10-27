#!/usr/bin/env bash
# Source from repository scripts; installs a complete JDK without modifying system packages.
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]] && [[ "$("$JAVA_HOME/bin/javac" -version 2>&1)" == javac\ 17.* ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
  return 0
fi
if [[ ! -x /workspace/.tools/jdk-17/bin/javac ]]; then
  command -v docker >/dev/null || { echo 'JDK 17 installation requires Docker.' >&2; return 1; }
  docker pull eclipse-temurin:17-jdk
  srm_jdk_container=$(docker create eclipse-temurin:17-jdk)
  mkdir -p /workspace/.tools/jdk-17
  docker cp "$srm_jdk_container:/opt/java/openjdk/." /workspace/.tools/jdk-17/
  docker rm "$srm_jdk_container" >/dev/null
fi
export JAVA_HOME=/workspace/.tools/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"

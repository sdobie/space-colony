#!/bin/bash
# Installs JDK 25 in Claude Code on the web sessions, since build.gradle.kts pins a
# Java 25 toolchain and the cloud image ships only JDK 21. Gradle's toolchain detection
# finds it under /usr/lib/jvm; the system default java is left alone.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JDK_DIR=/usr/lib/jvm/java-25-openjdk-amd64
if [ -x "$JDK_DIR/bin/javac" ]; then
  exit 0
fi

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo -n"
fi

export DEBIAN_FRONTEND=noninteractive
# The image's package lists can be stale (old point releases 404), so refresh first.
# Some third-party PPAs are blocked by the proxy; ignore those failures.
$SUDO apt-get update -q >/dev/null 2>&1 || true
$SUDO apt-get install -y -q --no-install-recommends openjdk-25-jdk >/dev/null

"$JDK_DIR/bin/java" -version

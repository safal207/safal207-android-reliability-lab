#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.9"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-${HOME}/.gradle}/headless-wrapper/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME_DIR}/bin/gradle"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if [[ ! -x "${GRADLE_BIN}" ]]; then
  mkdir -p "$(dirname "${GRADLE_HOME_DIR}")"
  tmp_zip="$(mktemp)"
  trap 'rm -f "${tmp_zip}"' EXIT
  echo "==> Bootstrapping Gradle ${GRADLE_VERSION}"
  curl -fsSL "${DIST_URL}" -o "${tmp_zip}"
  unzip -q -o "${tmp_zip}" -d "$(dirname "${GRADLE_HOME_DIR}")"
fi

exec "${GRADLE_BIN}" "$@"

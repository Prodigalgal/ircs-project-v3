#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${REGISTRY:-registry.mnnu.eu.org/ircs}"
REGISTRY_HOST="${REGISTRY%%/*}"
TARGET_ARCH="${TARGET_ARCH:-arm64}"
TARGET_OS="${TARGET_OS:-linux}"
BASE_IMAGE_SOURCE="${BASE_IMAGE_SOURCE:-mirror.gcr.io/library/eclipse-temurin:25-jre-alpine}"
BASE_IMAGE_FALLBACK="${BASE_IMAGE_FALLBACK:-public.ecr.aws/docker/library/eclipse-temurin:25-jre-alpine}"
BASE_IMAGE_TARGET="${BASE_IMAGE_TARGET:-${REGISTRY}/base/eclipse-temurin:25-jre-alpine}"

if [[ -z "${REGISTRY_USERNAME:-}" || -z "${REGISTRY_PASSWORD:-}" ]]; then
  echo "REGISTRY_USERNAME and REGISTRY_PASSWORD are required to sync the Jib base image." >&2
  exit 2
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker CLI is required to sync the Jib base image." >&2
  exit 2
fi

echo "${REGISTRY_PASSWORD}" | docker login "${REGISTRY_HOST}" -u "${REGISTRY_USERNAME}" --password-stdin >/dev/null

selected_source=""
for source in "${BASE_IMAGE_SOURCE}" "${BASE_IMAGE_FALLBACK}"; do
  [[ -z "${source}" ]] && continue
  echo "Pulling Jib base image ${source} for ${TARGET_OS}/${TARGET_ARCH}"
  if docker pull --platform "${TARGET_OS}/${TARGET_ARCH}" "${source}"; then
    selected_source="${source}"
    break
  fi
done

if [[ -z "${selected_source}" ]]; then
  echo "Unable to pull any configured Jib base image source." >&2
  exit 3
fi

echo "Tagging Jib base image ${selected_source} as ${BASE_IMAGE_TARGET}"
docker tag "${selected_source}" "${BASE_IMAGE_TARGET}"
docker push "${BASE_IMAGE_TARGET}"
echo "Synced Jib base image to ${BASE_IMAGE_TARGET}"

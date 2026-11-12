#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${REGISTRY:-registry.mnnu.eu.org/ircs}"
TARGET_ARCH="${TARGET_ARCH:-arm64}"
TARGET_OS="${TARGET_OS:-linux}"
TARGET_PLATFORMS="${TARGET_PLATFORMS:-${TARGET_OS}/${TARGET_ARCH}}"
JIB_BASE_IMAGE="${JIB_BASE_IMAGE:-registry.mnnu.eu.org/ircs/base/eclipse-temurin:25-jre-alpine}"
TAG="${IMAGE_TAG:-sha-$(git rev-parse --short=12 HEAD)}"
APP_VERSION="${APP_VERSION:-}"
RELEASE_TAG="${RELEASE_TAG:-}"
REVISION="${GITHUB_SHA:-$(git rev-parse HEAD)}"
BUILD_SCOPE="${BUILD_SCOPE:-affected}"
IMAGE_MODE="${IMAGE_MODE:-jvm}"
DRY_RUN="${DRY_RUN:-false}"

if [[ -z "${APP_VERSION}" || -z "${RELEASE_TAG}" ]]; then
  echo "APP_VERSION and RELEASE_TAG from the version-checked prepare job are required." >&2
  exit 2
fi
if [[ "${TAG}" != "${RELEASE_TAG}-fallback-${IMAGE_MODE}-${TARGET_ARCH}" ]]; then
  echo "IMAGE_TAG must be the immutable fallback tag for this mode and architecture." >&2
  exit 2
fi

case "${IMAGE_MODE}" in
  jvm|native) ;;
  *) echo "IMAGE_MODE must be jvm or native" >&2; exit 2 ;;
esac

if [[ "${IMAGE_MODE}" == "native" && "${TARGET_PLATFORMS}" != "linux/${TARGET_ARCH}" ]]; then
  echo "Native builds require one matching linux/TARGET_ARCH platform per runner; merge architecture tags afterwards." >&2
  exit 2
fi

if [[ "${DRY_RUN}" != "true" ]]; then
  if [[ -z "${REGISTRY_USERNAME:-}" || -z "${REGISTRY_PASSWORD:-}" ]]; then
    echo "REGISTRY_USERNAME and REGISTRY_PASSWORD are required." >&2
    exit 2
  fi
fi

modules=(
  "platform:ircs-migrator=ircs-migrator"
  "python:services/ircs-adult-classifier-service=ircs-adult-classifier-service"
  "services:ircs-api-gateway=ircs-api-gateway"
  "services:ircs-aggregation-worker=ircs-aggregation-worker"
  "services:ircs-catalog-service=ircs-catalog-service"
  "services:ircs-config-service=ircs-config-service"
  "services:ircs-content-safety-service=ircs-content-safety-service"
  "services:ircs-content-service=ircs-content-service"
  "services:ircs-credential-service=ircs-credential-service"
  "services:ircs-identity-service=ircs-identity-service"
  "services:ircs-ingestion-worker=ircs-ingestion-worker"
  "services:ircs-interaction-service=ircs-interaction-service"
  "services:ircs-magnet-service=ircs-magnet-service"
  "services:ircs-metadata-worker=ircs-metadata-worker"
  "services:ircs-normalization-worker=ircs-normalization-worker"
  "services:ircs-notification-worker=ircs-notification-worker"
  "services:ircs-ops-alert-service=ircs-ops-alert-service"
  "services:ircs-ops-service=ircs-ops-service"
  "services:ircs-portal-service=ircs-portal-service"
  "services:ircs-scraper-service=ircs-scraper-service"
  "services:ircs-search-service=ircs-search-service"
  "services:ircs-storage-service=ircs-storage-service"
  "services:ircs-task-service=ircs-task-service"
)

service_modules=(
  "services:ircs-api-gateway=ircs-api-gateway"
  "services:ircs-aggregation-worker=ircs-aggregation-worker"
  "services:ircs-catalog-service=ircs-catalog-service"
  "services:ircs-config-service=ircs-config-service"
  "services:ircs-content-safety-service=ircs-content-safety-service"
  "services:ircs-content-service=ircs-content-service"
  "services:ircs-credential-service=ircs-credential-service"
  "services:ircs-identity-service=ircs-identity-service"
  "services:ircs-ingestion-worker=ircs-ingestion-worker"
  "services:ircs-interaction-service=ircs-interaction-service"
  "services:ircs-magnet-service=ircs-magnet-service"
  "services:ircs-metadata-worker=ircs-metadata-worker"
  "services:ircs-normalization-worker=ircs-normalization-worker"
  "services:ircs-notification-worker=ircs-notification-worker"
  "services:ircs-ops-alert-service=ircs-ops-alert-service"
  "services:ircs-ops-service=ircs-ops-service"
  "services:ircs-portal-service=ircs-portal-service"
  "services:ircs-scraper-service=ircs-scraper-service"
  "services:ircs-search-service=ircs-search-service"
  "services:ircs-storage-service=ircs-storage-service"
  "services:ircs-task-service=ircs-task-service"
)

contains_entry() {
  local needle="$1"
  shift
  local entry
  for entry in "$@"; do
    [[ "${entry}" == "${needle}" ]] && return 0
  done
  return 1
}

add_entry() {
  local entry="$1"
  if ! contains_entry "${entry}" "${selected[@]:-}"; then
    selected+=("${entry}")
  fi
}

select_all() {
  selected=("${modules[@]}")
}

select_service() {
  local requested="$1"
  local normalized="${requested#:}"
  normalized="${normalized#services:}"
  normalized="${normalized#platform:}"
  normalized="${normalized#ircs/}"
  local entry
  for entry in "${modules[@]}"; do
    local module="${entry%%=*}"
    local image="${entry##*=}"
    local gradle_module="${module#python:}"
    if [[ "${requested}" == "${module}" || "${requested}" == "${gradle_module}" || "${requested}" == ":${module}" || "${normalized}" == "${image}" ]]; then
      add_entry "${entry}"
      return 0
    fi
  done
  echo "Unknown BUILD_SCOPE service: ${requested}" >&2
  exit 3
}

resolve_base_sha() {
  if [[ -n "${AFFECTED_BASE_SHA:-}" ]] && git cat-file -e "${AFFECTED_BASE_SHA}^{commit}" 2>/dev/null; then
    echo "${AFFECTED_BASE_SHA}"
    return 0
  fi
  for candidate in "${GITHUB_EVENT_BEFORE:-}" "${GITEA_EVENT_BEFORE:-}" "${CI_COMMIT_BEFORE_SHA:-}"; do
    if [[ -n "${candidate}" && "${candidate}" != "0000000000000000000000000000000000000000" ]] \
      && git cat-file -e "${candidate}^{commit}" 2>/dev/null; then
      echo "${candidate}"
      return 0
    fi
  done
  if git rev-parse HEAD^ >/dev/null 2>&1; then
    git rev-parse HEAD^
    return 0
  fi
  echo ""
}

select_affected() {
  selected=()
  local base
  base="$(resolve_base_sha)"

  local changed
  if [[ -n "${base}" ]]; then
    mapfile -t changed < <(git diff --name-only "${base}" HEAD)
  else
    mapfile -t changed < <(git ls-files)
  fi

  if [[ "${#changed[@]}" -eq 0 ]]; then
    return 0
  fi

  local file
  for file in "${changed[@]}"; do
    case "${file}" in
      build.gradle|settings.gradle|gradle.properties|gradlew|gradlew.bat|gradle/*|gradle/**/*|scripts/ci/*|scripts/ci/**/*|scripts/docker/Dockerfile.native|.github/workflows/*|.gitea/workflows/*)
        select_all
        return 0
        ;;
      shared/*|shared/**/*)
        for entry in "${service_modules[@]}"; do
          add_entry "${entry}"
        done
        ;;
      platform/ircs-migrator/*|platform/ircs-migrator/**/*)
        add_entry "platform:ircs-migrator=ircs-migrator"
        ;;
      services/*/*|services/*/**/*)
        local service
        service="$(cut -d '/' -f 2 <<<"${file}")"
        select_service "${service}"
        ;;
      *)
        ;;
    esac
  done
}

selected=()

case "${BUILD_SCOPE}" in
  all)
    select_all
    ;;
  affected|"")
    select_affected
    ;;
  *)
    IFS=',' read -ra requested_scopes <<<"${BUILD_SCOPE}"
    for requested in "${requested_scopes[@]}"; do
      select_service "${requested// /}"
    done
    ;;
esac

echo "IRCS image build scope: ${BUILD_SCOPE}"
echo "IRCS image tag: ${TAG}"
echo "IRCS image mode: ${IMAGE_MODE}"
echo "IRCS target platforms: ${TARGET_PLATFORMS}"
echo "IRCS Jib base image: ${JIB_BASE_IMAGE}"
echo "IRCS selected module count: ${#selected[@]}"

mkdir -p build
: > build-image-manifest.txt
{
  echo "registry=${REGISTRY}"
  echo "tag=${TAG}"
  echo "target_platforms=${TARGET_PLATFORMS}"
  echo "target_os=${TARGET_OS}"
  echo "target_arch=${TARGET_ARCH}"
  echo "jib_base_image=${JIB_BASE_IMAGE}"
  echo "scope=${BUILD_SCOPE}"
  echo "image_mode=${IMAGE_MODE}"
  echo "app_version=${APP_VERSION}"
  echo "release_tag=${RELEASE_TAG}"
  echo "revision=${REVISION}"
  echo "manifest_tag=${RELEASE_TAG}-fallback-${IMAGE_MODE}"
  echo "modules=${#selected[@]}"
} >> build-image-manifest.txt

if [[ "${#selected[@]}" -eq 0 ]]; then
  echo "No image-affecting changes detected. Nothing to build."
  exit 0
fi

if [[ "${DRY_RUN}" != "true" ]]; then
  echo "${REGISTRY_PASSWORD}" | docker login "${REGISTRY%%/*}" -u "${REGISTRY_USERNAME}" --password-stdin >/dev/null
  docker buildx inspect ircs-builder >/dev/null 2>&1 || docker buildx create --name ircs-builder --use >/dev/null
  docker buildx use ircs-builder
  docker buildx inspect --bootstrap >/dev/null
fi

for entry in "${selected[@]}"; do
  module="${entry%%=*}"
  image="${entry##*=}"
  target="${REGISTRY}/${image}:${TAG}"
  echo "${module}=${target}" >> build-image-manifest.txt
  if [[ "${DRY_RUN}" != "true" ]]; then
    if bash ../.github/scripts/image-exists.sh "${target}"; then
      echo "${module}: existing immutable image reused"
      continue
    else
      image_status=$?
      if [[ "${image_status}" -ne 1 ]]; then
        echo "Image existence check failed for ${target} (status ${image_status})." >&2
        exit "${image_status}"
      fi
    fi
  fi
  echo "::group::${module} -> ${target}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY_RUN=true, skipping image push for ${target}"
  elif [[ "${module}" == python:* ]]; then
    service_dir="${module#python:}"
    docker buildx build \
      --platform "${TARGET_PLATFORMS}" \
      --file "${service_dir}/Dockerfile" \
      --tag "${target}" \
      --label "org.opencontainers.image.version=${APP_VERSION}" \
      --label "org.opencontainers.image.revision=${REVISION}" \
      --build-arg "APP_VERSION=${APP_VERSION}" \
      --provenance=false \
      --push \
      .
  elif [[ "${IMAGE_MODE}" == "native" ]]; then
    native_target_machine="${NATIVE_TARGET_MACHINE:-compatibility}"
    if [[ "${TARGET_ARCH}" == "amd64" && -z "${NATIVE_TARGET_MACHINE:-}" ]]; then
      native_target_machine="x86-64"
    fi
    native_memory="${NATIVE_BUILD_MEMORY:-8g}"
    native_parallelism="${NATIVE_BUILD_PARALLELISM:-4}"
    if [[ "${image}" == "ircs-search-service" ]]; then
      native_memory="${NATIVE_BUILD_MEMORY:-10g}"
      native_parallelism="${NATIVE_BUILD_PARALLELISM:-2}"
    fi
    docker buildx build \
      --platform "${TARGET_PLATFORMS}" \
      --file scripts/docker/Dockerfile.native \
      --build-arg "GRADLE_PROJECT=${module}" \
      --build-arg "MODULE_DIR=${module//:/\/}" \
      --build-arg "IMAGE_NAME=${image}" \
      --build-arg "NATIVE_TARGET_MACHINE=${native_target_machine}" \
      --build-arg "NATIVE_BUILD_MEMORY=${native_memory}" \
      --build-arg "NATIVE_BUILD_PARALLELISM=${native_parallelism}" \
      --build-arg "APP_VERSION=${APP_VERSION}" \
      --tag "${target}" \
      --label "org.opencontainers.image.version=${APP_VERSION}" \
      --label "org.opencontainers.image.revision=${REVISION}" \
      --provenance=false \
      --push \
      .
  else
    ./gradlew --no-daemon ":${module}:jib" \
      "-PjibToImage=${target}" \
      "-PjibBaseImage=${JIB_BASE_IMAGE}" \
      "-PjibTargetPlatforms=${TARGET_PLATFORMS}" \
      "-PappVersion=${APP_VERSION}" \
      "-Djib.container.format=OCI" \
      "-Djib.container.labels=org.opencontainers.image.version=${APP_VERSION},org.opencontainers.image.revision=${REVISION}" \
      "-Djib.to.auth.username=${REGISTRY_USERNAME}" \
      "-Djib.to.auth.password=${REGISTRY_PASSWORD}"
  fi
  echo "::endgroup::"
done

echo "Image manifest written to build-image-manifest.txt"

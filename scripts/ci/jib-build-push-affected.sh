#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${REGISTRY:-harbor.mnnu.eu.org/ircs}"
TARGET_ARCH="${TARGET_ARCH:-arm64}"
TARGET_OS="${TARGET_OS:-linux}"
TAG="${IMAGE_TAG:-sha-$(git rev-parse --short=12 HEAD)}"
BUILD_SCOPE="${BUILD_SCOPE:-affected}"
DRY_RUN="${DRY_RUN:-false}"

if [[ "${DRY_RUN}" != "true" ]]; then
  if [[ -z "${HARBOR_USERNAME:-}" || -z "${HARBOR_PASSWORD:-}" ]]; then
    echo "HARBOR_USERNAME and HARBOR_PASSWORD are required." >&2
    exit 2
  fi
fi

modules=(
  "platform:ircs-migrator=ircs-migrator"
  "services:ircs-admin-bff=ircs-admin-bff"
  "services:ircs-aggregation-worker=ircs-aggregation-worker"
  "services:ircs-catalog-service=ircs-catalog-service"
  "services:ircs-config-service=ircs-config-service"
  "services:ircs-content-service=ircs-content-service"
  "services:ircs-credential-service=ircs-credential-service"
  "services:ircs-identity-service=ircs-identity-service"
  "services:ircs-ingestion-worker=ircs-ingestion-worker"
  "services:ircs-interaction-service=ircs-interaction-service"
  "services:ircs-magnet-service=ircs-magnet-service"
  "services:ircs-metadata-worker=ircs-metadata-worker"
  "services:ircs-normalization-worker=ircs-normalization-worker"
  "services:ircs-notification-worker=ircs-notification-worker"
  "services:ircs-ops-service=ircs-ops-service"
  "services:ircs-portal-bff=ircs-portal-bff"
  "services:ircs-portal-service=ircs-portal-service"
  "services:ircs-scraper-service=ircs-scraper-service"
  "services:ircs-search-service=ircs-search-service"
  "services:ircs-storage-service=ircs-storage-service"
  "services:ircs-task-service=ircs-task-service"
)

service_modules=(
  "services:ircs-admin-bff=ircs-admin-bff"
  "services:ircs-aggregation-worker=ircs-aggregation-worker"
  "services:ircs-catalog-service=ircs-catalog-service"
  "services:ircs-config-service=ircs-config-service"
  "services:ircs-content-service=ircs-content-service"
  "services:ircs-credential-service=ircs-credential-service"
  "services:ircs-identity-service=ircs-identity-service"
  "services:ircs-ingestion-worker=ircs-ingestion-worker"
  "services:ircs-interaction-service=ircs-interaction-service"
  "services:ircs-magnet-service=ircs-magnet-service"
  "services:ircs-metadata-worker=ircs-metadata-worker"
  "services:ircs-normalization-worker=ircs-normalization-worker"
  "services:ircs-notification-worker=ircs-notification-worker"
  "services:ircs-ops-service=ircs-ops-service"
  "services:ircs-portal-bff=ircs-portal-bff"
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
    if [[ "${requested}" == "${module}" || "${requested}" == ":${module}" || "${normalized}" == "${image}" ]]; then
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
      build.gradle|settings.gradle|gradlew|gradlew.bat|gradle/*|gradle/**/*|scripts/ci/*|scripts/ci/**/*|.gitea/workflows/*)
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
echo "IRCS target platform: ${TARGET_OS}/${TARGET_ARCH}"
echo "IRCS selected module count: ${#selected[@]}"

mkdir -p build
: > build-image-manifest.txt
{
  echo "registry=${REGISTRY}"
  echo "tag=${TAG}"
  echo "target_os=${TARGET_OS}"
  echo "target_arch=${TARGET_ARCH}"
  echo "scope=${BUILD_SCOPE}"
  echo "modules=${#selected[@]}"
} >> build-image-manifest.txt

if [[ "${#selected[@]}" -eq 0 ]]; then
  echo "No image-affecting changes detected. Nothing to build."
  exit 0
fi

for entry in "${selected[@]}"; do
  module="${entry%%=*}"
  image="${entry##*=}"
  target="${REGISTRY}/${image}:${TAG}"
  echo "${module}=${target}" >> build-image-manifest.txt
  echo "::group::${module} -> ${target}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY_RUN=true, skipping Jib push for ${target}"
  else
    ./gradlew --no-daemon ":${module}:jib" \
      "-PjibToImage=${target}" \
      "-PjibTargetArch=${TARGET_ARCH}" \
      "-PjibTargetOs=${TARGET_OS}" \
      "-Djib.to.auth.username=${HARBOR_USERNAME}" \
      "-Djib.to.auth.password=${HARBOR_PASSWORD}"
  fi
  echo "::endgroup::"
done

echo "Image manifest written to build-image-manifest.txt"

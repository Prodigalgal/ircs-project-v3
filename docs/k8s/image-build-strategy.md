# Image build strategy

The current Kubernetes node runs `linux/arm64`. The local development machine is
x86, so ordinary local Docker builds can produce the wrong architecture.

```text
Local workstation                     Registry                       K8S node
Windows / x86_64                      Harbor                         linux/arm64
+--------------------+                +------------------+           +----------------+
| Gradle + Jib       | -- arm64 ----> | ircs/*:dev       | -- pull ->| ircs-dev pods  |
+--------------------+                +------------------+           +----------------+
        |
        | optional only when Docker Desktop is running
        v
+--------------------+
| docker buildx      |
| --platform arm64   |
+--------------------+
```

## Confirmed environment

```text
Kubernetes node:
  architecture: arm64
  runtime: containerd://2.2.1

SSH:
  root@168.138.40.214:22 is reachable, but the provided key is rejected.
  root@168.138.40.52 accepts the provided key, but that host is x86_64.
```

## Preferred path

Use Jib for service images. It does not require a local Docker daemon, uses the
Kubernetes `harbor-secret` as temporary Docker config, and defaults to ARM64.

```powershell
.\scripts\k8s\sync-image-pull-secret.ps1
.\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
```

## Docker fallback

The Docker script now uses `docker buildx --platform linux/arm64` by default.
It is useful only when Docker Desktop is running locally or a proper remote ARM
builder is configured.

```powershell
.\scripts\docker\build-service-image.ps1 -Service ircs-notification-worker -Tag dev -Push
```

Do not use plain `docker build` for tags consumed by the ARM64 Kubernetes node.

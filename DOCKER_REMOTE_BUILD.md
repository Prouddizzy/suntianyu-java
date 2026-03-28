# Remote Docker Image Build

This repository can build its Docker image without any local Java, Maven, or Docker installation.

## What is included

- `Dockerfile`: multi-stage build for the Spring Boot backend
- `.github/workflows/docker-image.yml`: GitHub Actions workflow that builds the image on every push, and also supports manual runs
- Docker artifact export: uploads a `.tar.gz` image archive you can download
- Automatic GHCR push on the default branch, or manual GHCR push when requested

## Image behavior

- HTTP port: `51002`
- Device TCP port: `51003`
- Runtime env override:
  - `SERVER_PORT`
  - `APP_DEVICE_TCP_PORT`

## How to build remotely

1. Push this repository to GitHub.
2. GitHub Actions will automatically run `Build backend image`.
3. Each push uploads a Docker image artifact.
4. Pushes to the default branch also publish the image to GHCR automatically.

## Manual build

If needed, you can still open `Actions` and run `Build backend image` manually, then optionally set:

- `image_tag`
- `push_to_ghcr=true`

## How to get the image

- If you keep `push_to_ghcr=false`, download the workflow artifact:
  - `backend-image-<tag>`
- The artifact contains:
  - `stm32-smart-disinfector-java-<tag>.tar.gz`

## GHCR tags

- Every successful push publishes:
  - `ghcr.io/<owner>/stm32-smart-disinfector-java:sha-<short-commit>` on the default branch
  - `ghcr.io/<owner>/stm32-smart-disinfector-java:<branch-name>` when GHCR push is enabled
- The default branch also updates:
  - `ghcr.io/<owner>/stm32-smart-disinfector-java:latest`

## How to load the image on a server

```bash
gunzip -c stm32-smart-disinfector-java-<tag>.tar.gz | docker load
docker run -d --name stm32-backend -p 51002:51002 -p 51003:51003 ghcr.io/<owner>/stm32-smart-disinfector-java:<tag>
```

If you do not push to GHCR, use the image name shown by `docker load`.

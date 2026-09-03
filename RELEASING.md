# Releasing

This project does not publish to Maven Central. Releases are Docker images published to Docker Hub, built and pushed automatically by `.github/workflows/docker-build-push.yml` whenever a tag is pushed.

Two images are published:

-   `csd773/pm-admin-pdp-epp` (from `src/admin-pdp-epp`)
-   `csd773/pm-resource-pdp` (from `src/resource-pdp`)

## 1. Verify `main` is green

Make sure the latest commit on `main` passes CI (`.github/workflows/ci.yml`).

## 2. Tag the release

Use a semver tag (no `v` prefix required, but be consistent — the workflow's `docker/metadata-action` step derives `{version}`, `{major}.{minor}`, and `{major}` image tags from it):

```
git checkout main
git pull
git tag <new-version>
git push origin <new-version>
```

## 3. CI builds and pushes the images

Pushing the tag triggers `docker-build-push.yml`, which:

1. Runs the full test suite (`cd src && mvn -B clean test`).
2. Builds both services with Maven (`cd src && mvn -B -DskipTests clean package`).
3. Builds and pushes multi-arch (`linux/amd64`, `linux/arm64`) Docker images for `admin-pdp-epp` and `resource-pdp` to Docker Hub, tagged with the release version, `{major}.{minor}`, `{major}`, and `latest`.

This requires the `DOCKER_USERNAME` and `DOCKER_PASSWORD` repository secrets to already be configured — no manual credential setup is needed per release.

## 4. Verify

Confirm the new tags appear on Docker Hub for both `csd773/pm-admin-pdp-epp` and `csd773/pm-resource-pdp`, and that `docker-compose up -d` in `docker/` pulls the new `latest` images cleanly.

# Developer Setup

Instructions for setting up a local development environment for `policy-machine-pdp`.

## Prerequisites

-   **JDK 21** (the project compiles and targets Java 21)
-   **Maven 3.9+**
-   **Docker** (for EventStoreDB, and for Testcontainers-based tests)

## Clone the repository

```
git clone https://github.com/usnistgov/policy-machine-pdp.git
cd policy-machine-pdp
```

## Build

All Maven modules live under `src/`; build from there, not the repo root. This is a multi-module Maven reactor with four modules:

-   `shared` - common utilities, gRPC/protobuf definitions, EventStoreDB integration, interceptors
-   `shared-test` - Testcontainers-based test fixtures
-   `admin-pdp-epp` - policy administration service; adjudicates admin operations, runs EPP, hosts policy queries (port 50052)
-   `resource-pdp` - lightweight resource access-control service; maintains an in-memory PAP synced via events (port 50051)

Build all modules:

```
cd src && mvn clean package
```

Skip tests for a faster build while iterating:

```
cd src && mvn clean package -DskipTests
```

## Run locally

The fastest way to start the full stack (EventStoreDB + both services) is Docker Compose:

```
cd docker && docker-compose up -d
```

To run a service directly against Maven instead (useful when iterating on code):

```
# EventStoreDB must be running first, e.g. via `docker-compose up -d eventstore`
cd src/admin-pdp-epp && mvn spring-boot:run

# admin-pdp-epp must be running first
cd src/resource-pdp && mvn spring-boot:run
```

## Run tests

```
cd src && mvn clean test
```

Tests run across all four modules. CI (`.github/workflows/ci.yml`) runs `mvn -B clean test` on every push/PR to `main` (Testcontainers requires Docker, which is available on the CI runner), so make sure it passes locally before opening a PR.

## IDE setup

Any IDE with Maven multi-module support works. Import `src/pom.xml` as the reactor root.

## Working with protobuf

Proto definitions live in `src/shared/src/main/proto/`. The `protobuf-maven-plugin` generates Java sources during the `generate-sources` phase. After changing a `.proto` file, regenerate sources with:

```
cd src && mvn generate-sources
```

Generated sources land under `src/shared/target/generated-sources/protobuf`.

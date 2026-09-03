# Testing

How to run tests for `policy-machine-pdp`.

## Test frameworks

All modules use JUnit 5 (Jupiter). `shared-test` provides shared Testcontainers-based fixtures (e.g. `EventStoreTestContainer`) used by `admin-pdp-epp` and `resource-pdp` tests to spin up a real EventStoreDB instance per test run. Docker must be available locally to run these tests.

## Running the full suite

From `src/`:

```
mvn clean test
```

This runs unit and integration tests across all four modules (`shared`, `shared-test`, `admin-pdp-epp`, `resource-pdp`).

To run the same check CI runs (`.github/workflows/ci.yml` on every push/PR to `main`):

```
mvn -B clean test
```

## Running tests for a single module

Use Maven's `-pl` (project list) flag from `src/`:

```
mvn -pl admin-pdp-epp test
mvn -pl resource-pdp test
```

## Running a single test class or method

```
mvn test -Dtest=AdminAdjudicationServiceTest
mvn test -Dtest=AdminAdjudicationServiceTest#testCreatePolicyClass
```

## Test locations

```
src/shared/src/test/java
src/shared-test/src/main/java   (shared fixtures, not tests themselves)
src/admin-pdp-epp/src/test/java
src/resource-pdp/src/test/java
```

Bootstrap `.pml`/`.json` files and other test resources live under the corresponding module's `src/test/resources`.

## Before opening a PR

Run `mvn -B clean test` from `src/` and make sure it passes — this mirrors what CI checks on every pull request against `main`.

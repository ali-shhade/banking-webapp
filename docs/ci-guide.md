# Learning this project's CI

Continuous Integration (CI) automatically builds and tests each change sent to
GitHub. This backend already has tests for customers, accounts, transactions,
authentication, and error handling. CI runs those tests consistently so a change
that breaks compilation or existing behavior is visible immediately.

GitHub Actions is used because this repository is hosted on GitHub. GitHub reads
`.github/workflows/ci.yml` at the repository root and runs the declared steps on
a temporary computer called a runner.

## What was inspected

- The Maven project is `banking-backend/pom.xml`; its Java version is 21.
- Spring Boot's parent supplies the normal Maven testing configuration. No
  custom test exclusions or skip settings are present. The integration classes
  end in `Test`, so Maven Surefire runs them during the normal `test` phase.
- All 11 concrete test classes were inspected:
  - Unit tests without external services: `AccountServiceImplTest`, `CustomerServiceImplTest`,
    `TransactionServiceImplTest`, and `GlobalExceptionHandlerTest`.
  - MVC tests with mocked services: `AccountControllerTest` and
    `CustomerControllerTest`.
  - Tests using Spring and MySQL: `BankingBackendApplicationTests`,
    `AccountIntegrationTest`, `CustomerIntegrationTest`,
    `TransactionIntegrationTest`, and `JwtAuthenticationTest`.
- The five full-context classes inherit `BaseIntegrationTest`. That base class
  uses `@SpringBootTest` and `@ActiveProfiles("test")`.
- `src/test/resources/application-test.properties` supplies test settings,
  including a synthetic JWT signing key and `create-drop` database schema setup.
- `BaseIntegrationTest` starts one shared `MySQLContainer` using `mysql:8.4` in
  its static initializer. `@DynamicPropertySource` gives Spring its generated
  JDBC URL, database username, password, and driver.
- There are no `@Testcontainers`, `@Container`, or `@ServiceConnection`
  annotations. Starting the shared container directly is the existing lifecycle
  design and works without converting it to the JUnit container extension.
- The tests do not require a locally installed MySQL server or the normal
  application's database credentials. The full suite does require Docker.

No application or test configuration changes are needed for this workflow.

## Reading ci.yml

| Line(s) | Meaning |
| --- | --- |
| 1: `name: Backend CI` | Name shown in GitHub's Actions tab. |
| 3: `on:` | Lists events that start a workflow run. |
| 4-5: `push` / `branches: [main]` | Run whenever commits are pushed to `main`. |
| 6-7: `pull_request` / `branches: [main]` | Run for pull requests whose destination branch is `main`, including when new commits update them. |
| 9-10: `permissions` / `contents: read` | Give the job's built-in GitHub token permission to read repository contents. You do not create a secret for this. |
| 12: `jobs:` | Lists the work GitHub should perform. |
| 13: `build-and-test:` | Identifier for this one job. |
| 14: `runs-on: ubuntu-latest` | Use a fresh GitHub-hosted Ubuntu runner. |
| 16: `steps:` | Ordered tasks within the job. |
| 17: step `name` | A readable label displayed in the run's logs. |
| 18: `actions/checkout@v7` | Copy the commit being tested into the runner's workspace. |
| 20: step `name` | Label for Java setup. |
| 21: `actions/setup-java@v6` | Configure the requested JDK and Maven cache. |
| 22: `with:` | Inputs passed to that reusable action. |
| 23: `distribution: temurin` | Use Eclipse Temurin, an OpenJDK distribution. |
| 24: `java-version: '21'` | Match the Java version declared in the backend's POM. |
| 25: `cache: maven` | Reuse Maven's downloaded dependencies between runs when a matching cache is available. A first run or cache miss downloads them. Tests still run every time. |
| 26: `cache-dependency-path: banking-backend/pom.xml` | Use the actual backend POM when calculating the cache key. This path is relative to the checked-out repository root. |
| 28: step `name` | Label for the build and tests. |
| 29: `working-directory: banking-backend` | Execute the following shell command in the directory containing `pom.xml`. This setting applies to this step, not to the setup-java action above. |
| 30: `run: mvn ... clean verify` | Start Maven. `clean` removes old build output. `verify` runs lifecycle phases including compilation, tests, JAR packaging, and configured verification checks. `--batch-mode` makes output suitable for automation; `--no-transfer-progress` suppresses download progress bars. |

A compilation error or failing test makes Maven return a nonzero exit status.
GitHub then marks the step, job, and workflow as failed. No failure is ignored
and no tests are skipped.

## Testcontainers on the runner

The GitHub-hosted Ubuntu runner includes Docker. When Maven reaches the first
test inheriting `BaseIntegrationTest`, its static initializer starts MySQL 8.4
through Docker, pulling the image if necessary. Testcontainers waits for the
database to be ready and chooses a mapped host port. Spring receives that
connection through `@DynamicPropertySource`, overriding the normal datasource
configuration.

All full-context tests share the container for that test JVM. The existing test
profile configures Hibernate's schema creation and removal, and the tests manage
their test records. Testcontainers' resource reaper cleans up its containers
when the JVM exits; the temporary runner is also discarded after the job.

There is no separate GitHub Actions MySQL service, no production database
connection, and no user-managed GitHub secret needed. The database credentials
and JWT key already in the test code/configuration are only for disposable tests.

## What git push origin main does

First save and commit your changes: Git pushes commits, not uncommitted files.

1. `git push origin main` sends your local `main` commits to GitHub.
2. GitHub finds the root workflow and matches its `push` event for `main`.
3. GitHub allocates a temporary Ubuntu runner for `build-and-test`.
4. Checkout retrieves the commit being tested.
5. Setup-java selects Java 21 using Temurin and restores available Maven caches.
6. The build step enters `banking-backend` and runs `clean verify`.
7. Maven reads the POM and downloads any missing dependencies/plugins.
8. Maven compiles application and test code and runs all matching tests.
9. The database-backed tests start Testcontainers' MySQL container and use the
   Spring test profile. The mocked/unit tests need no database.
10. If tests pass, Maven packages the backend JAR and finishes verification.
11. GitHub reports a green success or a red failure and keeps the step logs.
12. Post-job cleanup saves caches where applicable, removes temporary resources,
    and releases the runner.

CI reports the result after the push; it does not undo a pushed commit or deploy
the application. Enforcing a passing check before merging would be a separate
repository branch-protection setting.

## Beginner exercise: green, red, green

### 1. Run the same build locally

Start Docker Desktop with its Linux container engine. Verify Java and Docker:

```powershell
mvn -version
docker info
mvn --batch-mode --no-transfer-progress -f banking-backend/pom.xml clean verify
```

Run these from the repository root. `mvn -version` must show Java 21; this is
the JVM Maven actually uses. Your standalone `java -version` may differ if PATH
and JAVA_HOME point to different installations.

If Maven is not installed, the backend includes its Maven wrapper:

```powershell
cd banking-backend
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
cd ..
```

On macOS/Linux, the equivalent wrapper command is `./mvnw ...`.

### 2. Commit and push the CI files

From the repository root on `main`:

```powershell
git status --short
git add .github/workflows/ci.yml docs/ci-guide.md README.md
git diff --cached
git commit -m "Add backend CI with GitHub Actions"
git push origin main
```

Stage those specific files so unrelated local work, including the currently
untracked `banking-ai` directory, is not included in the CI commit.

### 3. Find the green run

Open https://github.com/ali-shhade/banking-webapp/actions, select **Backend CI**,
then the run for your commit. Open **build-and-test** and expand **Build and test
backend**. Look for Maven's test summary and `BUILD SUCCESS`.

### 4. Intentionally fail exactly one existing test

Open:

`banking-backend/src/test/java/ali/com/banking/banking_backend/service/impl/AccountServiceImplTest.java`

In `closeAccount_whenActive_changesStatusToClosedAndSaves`, change only this
assertion:

```java
assertEquals(AccountStatus.CLOSED, account.getStatus());
```

to:

```java
assertEquals(AccountStatus.ACTIVE, account.getStatus());
```

The real behavior still closes the account. We temporarily ask the test to
expect ACTIVE, so its expectation is wrong. No production code or data changes.

Check just that test locally:

```powershell
mvn --batch-mode --no-transfer-progress -f banking-backend/pom.xml "-Dtest=AccountServiceImplTest#closeAccount_whenActive_changesStatusToClosedAndSaves" test
```

Expect one assertion failure showing expected `ACTIVE` and actual `CLOSED`.

### 5. Push and observe red CI

```powershell
git add banking-backend/src/test/java/ali/com/banking/banking_backend/service/impl/AccountServiceImplTest.java
git diff --cached
git commit -m "Exercise: intentionally fail one account test"
git push origin main
```

Open the new **Backend CI** run in Actions. Its build step should fail and show
the same assertion mismatch. A red result still provides logs you can inspect.

### 6. Restore the correct test

Change the assertion back to `AccountStatus.CLOSED`. Run the full build again:

```powershell
mvn --batch-mode --no-transfer-progress -f banking-backend/pom.xml clean verify
```

### 7. Push and observe green CI

```powershell
git add banking-backend/src/test/java/ali/com/banking/banking_backend/service/impl/AccountServiceImplTest.java
git diff --cached
git commit -m "Exercise: restore correct account test expectation"
git push origin main
```

Open the newest run and confirm it is green. The old red run remains in history,
which demonstrates that CI checks each commit rather than only the current code.

## Interview explanation

I added GitHub Actions CI to automatically build and test my Spring Boot backend
on pushes and pull requests to main. The workflow uses an Ubuntu runner, Temurin
Java 21, and Maven dependency caching, then runs clean verify in banking-backend.
Integration tests use Testcontainers to start a disposable MySQL database without
production credentials. Compilation errors or failing tests make the CI run fail,
so regressions are visible before a change is merged.

## References

- [GitHub checkout action](https://github.com/actions/checkout)
- [GitHub setup-java action and Maven caching](https://github.com/actions/setup-java)
- [GitHub's Ubuntu runner software, including Docker](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
- [Testcontainers Docker requirements](https://java.testcontainers.org/supported_docker_environment/)
- [Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)

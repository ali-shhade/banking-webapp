#title 
smart banking

## Continuous Integration

The backend CI workflow runs on pushes and pull requests to `main`. It uses Java
21 and Maven to build and test `banking-backend`, including MySQL integration
tests through Testcontainers. Docker must be running for the full local suite.

See [the CI learning guide](docs/ci-guide.md) for the workflow explanation, local
commands, and a guided failing-test/fix exercise.

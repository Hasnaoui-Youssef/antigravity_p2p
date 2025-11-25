# CI/CD Integration Testing

This project uses Docker Compose for automated integration testing in CI/CD pipelines.

## Quick Start

### Run All Tests Locally
```bash
./ci-test.sh
```

This will:
1. Build the project
2. Create Docker images
3. Start all services
4. Run 10 automated tests
5. Report results with proper exit codes

### CI/CD Integration

#### GitHub Actions
A complete workflow is provided in `.github/workflows/ci.yml`:

```yaml
- Unit tests (all modules)
- Build verification
- Docker integration tests
- Test result uploads
```

**Setup**: Push to `main` or `develop` branch, or create a pull request.

#### GitLab CI
Add to `.gitlab-ci.yml`:

```yaml
test:
  image: gradle:8.5-jdk21
  services:
    - docker:dind
  script:
    - ./ci-test.sh
  artifacts:
    reports:
      junit: '**/build/test-results/test/TEST-*.xml'
```

#### Jenkins
Add to `Jenkinsfile`:

```groovy
pipeline {
    agent any
    stages {
        stage('Test') {
            steps {
                sh './ci-test.sh'
            }
        }
    }
}
```

## Test Coverage

The `ci-test.sh` script validates:

| # | Test | Description |
|---|------|-------------|
| 1 | Build | Gradle compilation |
| 2 | Docker Build | Image creation |
| 3 | Service Start | Container orchestration |
| 4 | Bootstrap Health | Server health check |
| 5 | Alice Peer | Peer container running |
| 6 | Bob Peer | Peer container running |
| 7 | Error Detection | No exceptions in logs |
| 8 | Initialization | Peers fully initialized |
| 9 | RMI Services | RMI servers operational |
| 10 | Network | Inter-peer connectivity |

## Exit Codes

- `0` - All tests passed
- `1` - One or more tests failed

## Viewing Results

### In CI/CD
Test results are uploaded as artifacts and can be viewed in your CI/CD platform's UI.

### Locally
```bash
# Run tests
./ci-test.sh

# View logs on failure
docker compose logs bootstrap
docker compose logs alice
docker compose logs bob

# Cleanup
docker compose down -v
```

## Customization

### Add More Tests

Edit `ci-test.sh` and add additional test blocks:

```bash
echo "[Test X/Y] Your test description..."
if your_test_command; then
    pass "Test passed"
else
    fail "Test failed"
fi
```

### Adjust Timeouts

Modify the initialization wait time:
```bash
# Line ~70
sleep 10  # Increase if services need more time
```

### Test Different Scenarios

Create separate docker-compose files:
```bash
docker compose -f docker-compose.test.yml up -d
```

## Comparison with Other Approaches

| Approach | Pros | Cons |
|----------|------|------|
| **ci-test.sh (Current)** | ✅ Simple, CI/CD ready, fast | ⚠️ Shell-based assertions |
| TestContainers | ✅ Java-native | ❌ Complex setup, slower |
| Manual Testing | ✅ Full control | ❌ Not automated |

## Troubleshooting

### Tests timing out
Increase the sleep duration after service startup.

### Docker build fails
Check disk space: `docker system df`
Clean up: `docker system prune -f`

### Services not healthy
View detailed logs: `docker compose logs -f bootstrap alice bob`

### CI/CD platform issues
Ensure Docker-in-Docker is enabled and the runner has sufficient resources (4GB RAM minimum).

## Future Enhancements

- [ ] Add performance benchmarks
- [ ] Test message delivery success rate
- [ ] Test friend request flow end-to-end
- [ ] Add chaos testing (network failures, restarts)
- [ ] Generate test coverage reports

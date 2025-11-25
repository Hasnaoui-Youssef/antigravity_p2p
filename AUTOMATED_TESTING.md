# Automated Integration Testing

For fully automated testing without manual interaction, here are your options:

## Option 1: Infrastructure Tests (Quickest)

Run the automated script that tests container health, network connectivity, and service initialization:

```bash
./test-docker-automated.sh
```

**Tests:**
- ✓ Container startup
- ✓ RMI registry accessibility  
- ✓ Network connectivity between peers
- ✓ Application initialization
- ✓ Error detection in logs

## Option 2: TestContainers (Recommended for CI/CD)

Add TestContainers to your integration tests for proper JUnit integration:

```kotlin
// peer-node/build.gradle.kts
dependencies {
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}
```

Then update integration tests to use TestContainers instead of manual RMI setup.

## Option 3: Scriptable Peer Node (For Complex Flows)

Use `PeerNodeScript` to execute commands from a file:

```bash
# Create test script
cat > test-scripts/alice-test.txt <<EOF
SLEEP 2000
SEND_MESSAGE Bob Hello!
LIST_FRIENDS
EOF

# Run with script
docker compose run --rm alice-script \
  java -cp app.jar:common.jar p2p.peer.PeerNodeScript \
  --username Alice --port 5001 --bootstrap bootstrap:1099 \
  --script /scripts/alice-test.txt
```

## Option 4: Expect/Pexpect Scripts

Use `expect` to automate terminal interactions:

```bash
#!/usr/bin/expect
spawn docker attach p2p-alice
expect "> "
send "/login\r"
expect "> "
send "/search Bob\r"
expect "> "
send "/add Bob\r"
expect "> "
send "/quit\r"
```

## Option 5: REST/HTTP API (Future Enhancement)

Add an HTTP API to PeerNode for test automation:

```java
// POST /api/login
// POST /api/friends/add
// POST /api/messages/send
// GET /api/friends/list
```

Then test with:
```bash
curl -X POST http://localhost:8080/api/friends/add -d '{"username":"Bob"}'
```

## Recommendation

For now, use **Option 1** (`test-docker-automated.sh`) for CI/CD pipelines. It verifies that all components are working without requiring interactive testing.

For comprehensive end-to-end testing of actual P2P flows, consider adding **Option 2 (TestContainers)** to your integration test suite.

## Run Current Tests

```bash
# Automated infrastructure tests
./test-docker-automated.sh

# Manual verification
docker compose up -d
docker attach p2p-alice  # Ctrl+P, Ctrl+Q to detach
docker attach p2p-bob
docker compose down
```

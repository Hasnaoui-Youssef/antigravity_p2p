# Docker-Based Integration Testing

## Overview

Since RMI requires separate JVM instances for proper isolation, we use Docker containers for integration testing. This provides true distributed system testing.

## Quick Start

### Run All Integration Tests
```bash
./test-docker.sh
```

This script will:
1. Build the project
2. Create Docker images for bootstrap and peers
3. Start services in containers
4. Run automated checks
5. Keep services running for manual testing

### Start Individual Services

```bash
# Start bootstrap + 2 peers
docker compose up -d bootstrap alice bob

# Start with 3 peers
docker compose --profile extended up -d

# View logs
docker compose logs -f alice
docker compose logs -f bob
docker compose logs -f bootstrap

# Stop services
docker compose down
```

## Manual Testing in Docker

### Connect to Peer Terminal

```bash
# Alice's terminal
docker compose exec -it alice /bin/bash
# Then run: java -cp app.jar:common.jar p2p.peer.PeerNode --username Alice --port 5001 --bootstrap bootstrap:1099

# Bob's terminal  
docker compose exec -it bob /bin/bash
```

### Test Scenarios

**Scenario 1: Friend Request**
```bash
# Alice's container
> /login
> /search Bob
> /add Bob

# Bob's container
> /login
> /accept Alice
> /list
```

**Scenario 2: Messaging**
```bash
# Alice
> /msg Bob Hello from Alice!

# Bob
> /msg Alice Hi Alice!
```

## Docker Architecture

```
┌─────────────────┐
│   Bootstrap     │
│   (Container)   │
│  - RMI: 1099    │
│  - UDP: 9876    │
└────────┬────────┘
         │
    ┌────┴────┬────────┬─────────┐
    │         │        │         │
┌───▼───┐ ┌──▼───┐ ┌──▼────┐ ┌──▼──────┐
│ Alice │ │ Bob  │ │Charlie│ │  More?  │
│ :5001 │ │ :5002│ │ :5003 │ │  ...    │
└───────┘ └──────┘ └───────┘ └─────────┘
```

## Why Docker for Integration Tests?

1. **JVM Isolation**: Each container has its own JVM, avoiding RMI port conflicts
2. **Network Isolation**: Containers communicate over Docker network
3. **Clean State**: Each test run starts with fresh containers
4. **Realistic**: Mimics actual distributed deployment
5. **Scalable**: Easy to add more peer instances

## Automated Integration Tests

The `test-docker.sh` script runs these checks:
- ✓ Bootstrap RMI registry is listening
- ✓ Peers can start and connect
- ✓ Users register with bootstrap
- ✓ Heartbeats are sent

For full end-to-end testing, use the manual scenarios above.

## Troubleshooting

### Container won't start
```bash
# Check logs
docker compose logs bootstrap
docker compose logs alice

# Rebuild images
docker compose build --no-cache
```

### Network issues
```bash
# Recreate network
docker compose down
docker network prune
docker compose up -d
```

### Clean everything
```bash
docker compose down --volumes --remove-orphans
docker system prune -f
```

## Performance Notes

- First startup is slow (image building)
- Subsequent runs are fast (cached layers)
- Containers use ~200MB RAM each
- Network latency: <1ms on localhost

## Future Enhancements

- Add TestContainers for automated JUnit integration tests
- Add test assertions in script
- Add performance benchmarks
- Add chaos testing (network partitions, failures)

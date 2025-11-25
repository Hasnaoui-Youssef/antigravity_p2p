# Antigravity P2P - Distributed Messaging Application

## Project Structure

This is a Phase 1 implementation of a peer-to-peer messaging system with:

- **Bootstrap Server**: Central discovery service with UDP heartbeat monitoring
- **Peer Node**: P2P application with friend requests and messaging
- **Common**: Shared models, RMI interfaces, and vector clock implementation

## Building

```bash
./gradlew build
```

## Running

### Local Development

### Start Bootstrap Server
```bash
./gradlew :bootstrap-server:run
```

### Start Peer Node
```bash
./gradlew :peer-node:run --args="--username Alice --port 5001 --bootstrap localhost:1099"
```

### Docker-Based Testing (Recommended for Integration Tests)

```bash
# Run automated integration tests
./test-docker.sh

# Or start services manually
docker compose up -d bootstrap alice bob

# Connect to peer terminal
docker compose exec alice sh
```

See [DOCKER_TESTING.md](DOCKER_TESTING.md) for detailed Docker testing guide.

## Testing

### Unit Tests
```bash
./gradlew :common:test :bootstrap-server:test 
./gradlew :peer-node:test --tests '*Test' --tests '!*IntegrationTest'
```

### Integration Tests
Use Docker Compose for proper RMI isolation. See [DOCKER_TESTING.md](DOCKER_TESTING.md).

## Terminal Commands

- `/login` - Connect to bootstrap server
- `/add <username>` - Send friend request
- `/accept <username>` - Accept friend request
- `/msg <username> <message>` - Send message to friend
- `/list` - Show friends and pending requests
- `/search <username>` - Search for users
- `/quit` - Exit application

## Requirements

- Java 21+
- Gradle 8.5+

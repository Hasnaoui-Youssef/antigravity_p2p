#!/bin/bash

# Integration test script using Docker Compose
# Tests peer-to-peer communication in isolated containers

set -e

echo "=== Antigravity P2P - Docker Integration Tests ==="
echo

# Build the project
echo "[1/5] Building project..."
./gradlew build -x test
echo "✓ Build complete"
echo

# Build Docker images
echo "[2/5] Building Docker images..."
docker compose build --no-cache
echo "✓ Images built"
echo

# Start services
echo "[3/5] Starting services..."
docker compose up -d bootstrap alice bob
echo "✓ Services started"
echo

# Wait for services to be ready
echo "[4/5] Waiting for services to initialize..."
sleep 5
echo "✓ Services ready"
echo

# Run integration tests
echo "[5/5] Running integration tests..."
echo

echo "Test 1: Verify bootstrap server is running"
docker compose exec -T bootstrap sh -c 'netstat -ln | grep 1099' && echo "✓ Bootstrap RMI registry listening" || echo "✗ Bootstrap not ready"
echo

echo "Test 2: Verify Alice can connect"
docker compose logs alice | grep "Peer Node" && echo "✓ Alice started" || echo "✗ Alice failed"
echo

echo "Test 3: Verify Bob can connect"
docker compose logs bob | grep "Peer Node" && echo "✓ Bob started" || echo "✗ Bob failed"
echo

echo "Test 4: Check bootstrap registered users"
docker compose logs bootstrap | grep "User registered" && echo "✓ Users registered with bootstrap" || echo "⚠ No registrations seen yet"
echo

echo "=== Manual Testing ==="
echo "Services are running. You can now interact with peers:"
echo
echo "  # Connect to Alice's terminal:"
echo "  docker compose exec alice sh"
echo
echo "  # Connect to Bob's terminal:"
echo "  docker compose exec bob sh"
echo
echo "  # View logs:"
echo "  docker compose logs -f alice"
echo "  docker compose logs -f bob"
echo "  docker compose logs -f bootstrap"
echo
echo "  # Stop services:"
echo "  docker compose down"
echo

echo "Press Ctrl+C to stop all services..."
docker compose logs -f

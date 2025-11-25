# Multi-stage build for Antigravity P2P components

# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# Stage 2: Bootstrap Server
FROM eclipse-temurin:21-jre AS bootstrap
WORKDIR /app
COPY --from=builder /app/bootstrap-server/build/libs/*.jar app.jar
COPY --from=builder /app/common/build/libs/*.jar common.jar
EXPOSE 1099 9876
ENTRYPOINT ["java", "-cp", "app.jar:common.jar", "p2p.bootstrap.BootstrapServer"]

# Stage 3: Peer Node
FROM eclipse-temurin:21-jre AS peer
WORKDIR /app
COPY --from=builder /app/peer-node/build/libs/*.jar app.jar
COPY --from=builder /app/common/build/libs/*.jar common.jar
EXPOSE 5000-5010
ENTRYPOINT ["java", "-cp", "app.jar:common.jar", "p2p.peer.PeerNode"]

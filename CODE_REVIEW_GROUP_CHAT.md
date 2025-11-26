# Code Review: Group Chat & Consensus Implementation

## Overview
The implementation introduces robust features for group chat, including leader election (ZooKeeper-style), gossip protocol (Cassandra-style), and quorum-based consensus. The architecture is modular and uses immutable data structures.

## Strengths
- **Modular Design**: Clear separation of concerns between `GroupManager`, `LeaderElectionManager`, `ConsensusManager`, and `GossipManager`.
- **Immutable Models**: `Group` class is immutable, preventing state corruption.
- **Leader Election**: robust heartbeat and timeout mechanism.
- **Gossip Protocol**: Efficient digest-based synchronization to detect missing messages.

## Critical Issues Identified

### 1. ConsensusManager Sync Logic Flaw
**File**: `p2p/peer/consensus/ConsensusManager.java`

The `initiateSync` method attempts to query a quorum and wait for results using `CompletableFuture`:

```java
// In queryQuorum
List<CompletableFuture<List<Message>>> futures = quorum.stream()
    .map(member -> CompletableFuture.supplyAsync(() -> 
        querySingleMember(groupId, lastKnownState, member), executor))
    .collect(Collectors.toList());
```

However, `querySingleMember` returns an empty list immediately because RMI calls are asynchronous (void return or one-way message sending):

```java
// In querySingleMember
peerService.receiveMessage(request);
return Collections.emptyList(); // Returns immediately!
```

**Result**: `initiateSync` always "completes" with 0 messages because it doesn't actually wait for the *responses* from peers. The responses come in later via `handleSyncResponse`, but the `initiateSync` logic (merging, quorum check) is effectively bypassed.

**Fix**: 
- Either implement a request-response pattern where `querySingleMember` blocks until a response is received (using a `CompletableFuture` map tracked by request ID).
- Or refactor `initiateSync` to be fully event-driven, not expecting an immediate return.

### 2. Inefficient Sync Request
**File**: `p2p/peer/consensus/ConsensusManager.java`

In `handleSyncRequest`, the logic `shouldSendMessage` currently returns `true` for all messages:

```java
private boolean shouldSendMessage(Message msg, VectorClock senderClock) {
    return true; // For now, send all messages
}
```

**Result**: Every sync request re-transmits the *entire* message history of the group. As chat history grows, this will become a massive performance bottleneck.

**Fix**: Implement proper Vector Clock comparison to only send messages the requester hasn't seen.

## Minor Issues

### 3. Leader Election Voting
**File**: `p2p/peer/groups/LeaderElectionManager.java`

The voting logic is "first-come-first-served" for a given epoch.
```java
if (state == null) {
    state = new ElectionState(...); // Vote for first proposal
}
```
In a scenario where two peers propose simultaneously, the network might split votes, leading to no winner and a retry. This is acceptable for now but could be optimized with node ID priority (e.g., highest ID wins ties).

## Recommendations

1.  **Refactor `ConsensusManager`** to properly handle async responses.
2.  **Optimize `shouldSendMessage`** to use Vector Clocks for delta syncing.
3.  **Integrate** these managers into `PeerNode` and `PeerServiceImpl`.

# Change Logs

## 2025-11-26 - Group Invitation System & Message Handling Refactoring

### Overview
Implemented complete group invitation flow with accept/reject capabilities, refactored message handling for UI/business decoupling, and achieved 9/10 integration tests passing.

### Major Features

#### 1. Group Invitation Flow
- **PendingGroup System**: Groups now go through invitation phase before finalization
- **3-Second Timeout**: Automatic timeout for non-responding invitees
- **Minimum Group Size**: Enforced 3 members minimum (creator + 2 invitees)
- **Accept/Reject Responses**: Full bidirectional invitation/response protocol
- **Group Finalization**: Automatic finalization when all invitees accept
- **Group Broadcasting**: Finalized groups shared with all accepted members via RMI

#### 2. InvitationHandler Interface
- **UI/Business Decoupling**: Created callback interface for programmatic invitation control
- **Test Integration**: Tests can control accept/reject decisions via lambda handlers
- **Default Behavior**: Safe default rejection when no handler is set
- **Exposed via PeerController**: `setInvitationHandler()` method for UI/test integration

#### 3. Message Package Refactoring
- **New Package**: All message classes moved to `p2p.common.model.message`
- **New Message Types**: Added `GroupInvitationRequest` and `GroupInvitationResponse`
- **Updated Imports**: All references updated across codebase

#### 4. Group Model Improvements
- **Set-Based Members**: Changed from `List<User>` to `Set<User>` to prevent duplicates
- **Leader Exclusion**: Creator/leader not included in members set
- **Shared User Objects**: Immutable `User` objects shared across groups for memory efficiency

#### 5. API Improvements
- **getGroup(String groupId)**: Added method for direct group lookup by ID
- **Fixed .getGroups().get(0)**: All tests now use proper ID-based lookups
- **Better Error Handling**: Null checks instead of Optional for cleaner code

### Technical Changes

#### Core Components Modified
- **GroupManager**: Added FriendManager dependency, invitation handling, timeout management
- **PeerServiceImpl**: Message dispatcher updated for invitation messages
- **PeerController**: InvitationHandler integration, improved group messaging
- **Group**: Changed to Set-based members, leader exclusion logic

#### New Classes
- `InvitationHandler` - Callback interface for invitation decisions
- `GroupInvitationRequest` - Message for sending invitations
- `GroupInvitationResponse` - Message for responding to invitations  
- `PendingGroup` - Manages groups in invitation phase

#### Bug Fixes
- **Invitation Response Routing**: Fixed creator lookup (now uses FriendManager instead of potentialMembers)
- **Group Message Broadcasting**: Now sends to ALL participants (leader + members)
- **Group Sharing**: All accepted members now receive finalized group via RMI

### Integration Tests

#### Test Suite: 9/10 Passing ✅
1. ✅ Successful group creation and messaging
2. ✅ Rejection handling (Charlie rejects)
3. ✅ Default rejection (no handler set)
4. ✅ Minimum size validation
5. ❌ Leader election (known race condition)
6. ✅ Concurrent messaging (all members send/receive)
7. ✅ Gossip propagation after partition
8. ✅ Consensus sync for late joiners
9. ✅ Concurrent group creation by different users
10. ✅ Large group (5 members) rapid messaging

#### Known Limitation
- **Test 5 (Leader Election)**: Race condition where nodes don't agree on new leader
  - Cause: Simultaneous election initiation by multiple nodes
  - Impact: Split-brain scenario possible
  - Future Work: Implement Raft/Paxos or add tie-breaking logic

### Design Decisions

#### Why Set<User> for Members?
- Prevents accidental duplicate members
- Natural representation of group membership
- Efficient contains() checks

#### Why Exclude Leader from Members Set?
- Leader has special role and privileges
- Clearer separation of concerns
- `isMember()` checks both leader and members

#### Why FriendManager in GroupManager?
- Needed to resolve creator User object for invitation responses
- Creator not in potentialMembers (only invitees are)
- Friends list provides User object lookup

#### Why InvitationHandler Interface?
- Decouples business logic from UI
- Tests can programmatically control behavior
- UI can implement same interface for user prompts
- No hardcoded acceptance logic

### Files Changed
- **Common**: Message types, Group model
- **Peer Node**: GroupManager, PeerController, PeerServiceImpl, PeerNode
- **Tests**: Complete rewrite of GroupChatIntegrationTest (10 comprehensive scenarios)

### Next Steps
- Improve leader election algorithm (Raft/Paxos implementation)
- Add UI integration for invitation prompts
- Implement group member addition/removal
- Add group metadata (description, creation time, etc.)

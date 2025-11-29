package p2p.common.model;

public enum MessageTopic {
    CHAT,              // Unified chat messages (direct and group)
    GOSSIP,
    SYNC_REQUEST,
    SYNC_RESPONSE,
    ELECTION,
    GROUP_INVITATION,  // Unified group invitation messages (request, accept, reject)
    FRIEND_MESSAGE,
    
    // Deprecated - kept for backwards compatibility during migration
    @Deprecated DIRECT,
    @Deprecated GROUP,
    @Deprecated INVITATION_REQUEST,
    @Deprecated INVITATION_RESPONSE,
    @Deprecated GROUP_REJECT
}

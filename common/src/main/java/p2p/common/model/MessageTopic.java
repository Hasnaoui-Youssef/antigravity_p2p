package p2p.common.model;

public enum MessageTopic {
    CHAT,              // Unified chat messages (direct and group)
    GOSSIP,
    SYNC_REQUEST,
    SYNC_RESPONSE,
    ELECTION,
    GROUP_INVITATION,  // Unified group invitation messages (request, accept, reject)
    FRIEND_INVITATION,
    GROUP_EVENT
}

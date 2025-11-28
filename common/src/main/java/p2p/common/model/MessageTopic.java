package p2p.common.model;

public enum MessageTopic {
    DIRECT,
    GROUP,
    GOSSIP,
    SYNC_REQUEST,
    SYNC_RESPONSE,
    ELECTION,
    INVITATION_REQUEST,
    INVITATION_RESPONSE,
    GROUP_REJECT,
    FRIEND_MESSAGE
}

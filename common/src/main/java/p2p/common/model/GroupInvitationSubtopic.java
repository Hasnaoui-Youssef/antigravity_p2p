package p2p.common.model;

/**
 * Subtopics for group invitation messages.
 */
public enum GroupInvitationSubtopic {
    REQUEST,    // Invitation sent to a user
    ACCEPT,     // User accepted the invitation
    REJECT,     // User rejected the invitation
}

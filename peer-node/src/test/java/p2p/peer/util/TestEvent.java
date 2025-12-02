package p2p.peer.util;

import p2p.common.model.GroupEvent;

/**
 * Enum for testing purposes that encompasses both user-facing GroupEvents
 * and system events like LEADER_ELECTED.
 */
public enum TestEvent {
    GROUP_CREATED,
    GROUP_DISSOLVED,
    MEMBER_JOINED,
    MEMBER_LEFT,
    MESSAGE_RECEIVED,
    LEADER_ELECTED;

    /**
     * Converts a production GroupEvent to a TestEvent.
     */
    public static TestEvent fromGroupEvent(GroupEvent event) {
        switch (event) {
            case CREATED:
                return GROUP_CREATED;
            case DISSOLVED:
                return GROUP_DISSOLVED;
            case USER_JOINED:
                return MEMBER_JOINED;
            default:
                throw new IllegalArgumentException("Unknown GroupEvent: " + event);
        }
    }
}

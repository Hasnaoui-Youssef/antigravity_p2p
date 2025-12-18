package p2p.peer.peerui.model;

import p2p.common.model.message.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks unread messages using a timestamp-based approach.
 * Memory efficient: O(conversations) instead of O(messages).
 */
public class UnreadTracker {

    // Maps conversationId -> timestamp of last time user viewed this conversation
    private final Map<String, Long> lastReadTimestamps = new ConcurrentHashMap<>();

    /**
     * Mark conversation as read (user opened it).
     * Simply stores current time as the "last read" timestamp.
     *
     * @param conversationId The conversation ID (userId for direct, groupId for
     *                       group)
     */
    public void markAsRead(String conversationId) {
        lastReadTimestamps.put(conversationId, System.currentTimeMillis());
    }

    /**
     * Check if conversation has unread messages.
     * Compares last message timestamp against last-read timestamp.
     *
     * @param conversationId         The conversation ID
     * @param latestMessageTimestamp Timestamp of the most recent message
     * @return true if there are unread messages
     */
    public boolean hasUnread(String conversationId, long latestMessageTimestamp) {
        Long lastRead = lastReadTimestamps.get(conversationId);
        if (lastRead == null) {
            return latestMessageTimestamp > 0; // Never read = unread if has messages
        }
        return latestMessageTimestamp > lastRead;
    }

    /**
     * Get count of unread messages (for badge display).
     *
     * @param conversationId The conversation ID
     * @param messages       List of messages in the conversation
     * @return Number of unread messages
     */
    public int getUnreadCount(String conversationId, List<ChatMessage> messages) {
        Long lastRead = lastReadTimestamps.getOrDefault(conversationId, 0L);
        return (int) messages.stream()
                .filter(m -> m.getTimestamp() > lastRead)
                .count();
    }

    /**
     * Get the last read timestamp for a conversation.
     *
     * @param conversationId The conversation ID
     * @return The timestamp, or 0 if never read
     */
    public long getLastReadTimestamp(String conversationId) {
        return lastReadTimestamps.getOrDefault(conversationId, 0L);
    }

    /**
     * Clear all tracking data.
     */
    public void clear() {
        lastReadTimestamps.clear();
    }
}

package p2p.peer.ui;

/**
 * Represents the current active conversation (user or group).
 */
public class ConversationContext {
    
    public enum Type {
        DIRECT,
        GROUP
    }
    
    private final Type type;
    private final String id;
    private final String displayName;
    
    private ConversationContext(Type type, String id, String displayName) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
    }
    
    public static ConversationContext directMessage(String userId, String username) {
        return new ConversationContext(Type.DIRECT, userId, username);
    }
    
    public static ConversationContext groupChat(String groupId, String groupName) {
        return new ConversationContext(Type.GROUP, groupId, groupName);
    }
    
    public Type getType() {
        return type;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName + (type == Type.GROUP ? " (Group)" : "");
    }
}

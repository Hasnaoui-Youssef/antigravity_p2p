package p2p.peer.groups;

import p2p.common.model.message.GroupInvitationRequest;

/**
 * Callback interface for handling group invitations.
 * Decouples the business logic from UI layer - UI implements this to provide user decisions.
 * Tests can implement this to programmatically control accept/reject.
 */
public interface InvitationHandler {
    
    /**
     * Called when a group invitation is received.
     * Implementation should return decision synchronously or store for later processing.
     * 
     * @param request The invitation request
     * @return true to accept, false to reject
     */
    boolean onInvitationReceived(GroupInvitationRequest request);
}

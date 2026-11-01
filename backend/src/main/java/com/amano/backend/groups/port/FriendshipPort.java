package com.amano.backend.groups.port;

import java.util.List;

/**
 * Outbound port for the (not yet existing) friendship module: the caller's
 * friends and the friend-filtered candidate search for adding members (US1/US3).
 * Search is restricted to friends by decision — never the whole user base.
 */
public interface FriendshipPort {

    /** Email addresses of the user's established friends. */
    List<String> friendEmailsOf(Long userId);

    /** True when {@code otherEmail} is a friend of {@code userId}. */
    boolean isFriend(Long userId, String otherEmail);
}

package com.amano.backend.groups;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test support for the group ITs: real ACCEPTED rows in
 * {@code friendship_requests} (011–013) instead of the old stub property
 * {@code groups.friendship.stub-friend-emails}. Each IT declares its fixture
 * emails ("friends of everyone"); every time an account is registered, it is
 * linked with the fixtures (or, if it is a fixture, with every other account).
 * Whichever account registers second creates the link, so the result does not
 * depend on the order of the tests. Idempotent: never duplicates a friendship.
 */
final class FriendshipFixtures {

    private FriendshipFixtures() {
    }

    static void link(JdbcTemplate jdbcTemplate, String email, List<String> fixtureEmails) {
        Long userId = jdbcTemplate.query("SELECT id FROM users WHERE email = ?",
                rs -> rs.next() ? rs.getLong(1) : null, email);
        if (userId == null) {
            return;
        }
        List<Long> others = fixtureEmails.contains(email)
                ? jdbcTemplate.queryForList("SELECT id FROM users WHERE id <> ?", Long.class, userId)
                : jdbcTemplate.queryForList("SELECT id FROM users WHERE email = ANY (?) AND id <> ?",
                        Long.class, fixtureEmails.toArray(String[]::new), userId);
        others.forEach(otherId -> befriend(jdbcTemplate, userId, otherId));
    }

    static void befriend(JdbcTemplate jdbcTemplate, long a, long b) {
        jdbcTemplate.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                SELECT ?, ?, 'ACCEPTED', now()
                WHERE NOT EXISTS (
                    SELECT 1 FROM friendship_requests
                    WHERE status = 'ACCEPTED'
                      AND LEAST(sender_id, recipient_id) = LEAST(?, ?)
                      AND GREATEST(sender_id, recipient_id) = GREATEST(?, ?))
                """, a, b, a, b, a, b);
    }
}

package com.amano.backend.groups.dto;

/**
 * Response of {@code POST /api/groups/{id}/members} (contracts/api-contracts.md):
 * the added member plus the caller's role and the action summary. Registered
 * members carry the user id; simulated profiles carry id = null.
 */
public class AddMemberResponse {

    private MemberInfo member;
    private String groupMemberRole;
    private String action = "ADDED";
    private boolean notified = false;

    public static class MemberInfo {

        private Long id;
        private String type;
        private String name;
        private String email;
        private String role;

        public MemberInfo(Long id, String type, String name, String email, String role) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.email = email;
            this.role = role;
        }

        public Long getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }
    }

    public MemberInfo getMember() {
        return member;
    }

    public void setMember(MemberInfo member) {
        this.member = member;
    }

    public String getGroupMemberRole() {
        return groupMemberRole;
    }

    public void setGroupMemberRole(String groupMemberRole) {
        this.groupMemberRole = groupMemberRole;
    }

    public String getAction() {
        return action;
    }

    public boolean isNotified() {
        return notified;
    }
}

package com.amano.backend.groups.dto;

import java.util.List;

/**
 * Payload of {@code POST /api/groups} (contracts/api-contracts.md). At least one
 * additional member is required: registered friends (identifier = email,
 * resolved through the friendship relation) or simulated profiles (email +
 * name). {@code members} (spec 023) carries several; {@code member} remains
 * valid for one, and {@code members} wins if both arrive. Nothing else lives in
 * it — business validation is in {@code GroupService}.
 */
public class CreateGroupRequest {

    /** Required, non-empty (FR-001); trimmed by the service. */
    private String name;

    private String description;

    private MemberInput member;

    private List<MemberInput> members;

    public static class MemberInput {

        /** REGISTERED | SIMULATED. */
        private String memberType;

        /** Friend's email (REGISTERED) or the simulated profile email. */
        private String identifier;

        /** Required when memberType = SIMULATED. */
        private String name;

        public String getMemberType() {
            return memberType;
        }

        public void setMemberType(String memberType) {
            this.memberType = memberType;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MemberInput getMember() {
        return member;
    }

    public void setMember(MemberInput member) {
        this.member = member;
    }

    public List<MemberInput> getMembers() {
        return members;
    }

    public void setMembers(List<MemberInput> members) {
        this.members = members;
    }
}

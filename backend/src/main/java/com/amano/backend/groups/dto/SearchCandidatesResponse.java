package com.amano.backend.groups.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response of {@code GET /api/groups/{id}/candidates?q=} (contracts
 * /api-contracts.md): ONLY the caller's friends (via FriendshipPort) who are
 * not yet active members of the group. Never the whole user base.
 */
public class SearchCandidatesResponse {

    private List<Candidate> content = new ArrayList<>();

    public static class Candidate {

        private Long id;
        private String firstName;
        private String lastName;
        private String email;

        public Candidate(Long id, String firstName, String lastName, String email) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
        }

        public Long getId() {
            return id;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getEmail() {
            return email;
        }
    }

    public List<Candidate> getContent() {
        return content;
    }

    public void setContent(List<Candidate> content) {
        this.content = content;
    }
}

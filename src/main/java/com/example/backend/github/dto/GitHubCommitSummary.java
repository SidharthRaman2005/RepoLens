package com.example.backend.github.dto;

import java.time.OffsetDateTime;

public record GitHubCommitSummary(
        String sha,
        GitHubCommit commit) {

    public record GitHubCommit(String message, GitHubPerson author, GitHubPerson committer) { }

    public record GitHubPerson(String name, String email, OffsetDateTime date) { }
}
